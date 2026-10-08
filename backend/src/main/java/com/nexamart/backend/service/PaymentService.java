package com.nexamart.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexamart.backend.api.ApiModels.PaymentCreateOrderResponse;
import com.nexamart.backend.api.ApiModels.PaymentVerifyRequest;
import com.nexamart.backend.api.ApiModels.OrderResponse;
import com.nexamart.backend.api.ApiModels.UpiQrResponse;
import com.nexamart.backend.domain.Order;
import com.nexamart.backend.domain.Payment;
import com.nexamart.backend.domain.PaymentMethod;
import com.nexamart.backend.domain.PaymentStatus;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.OrderRepository;
import com.nexamart.backend.repository.PaymentRepository;
import com.nexamart.backend.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Razorpay Standard Checkout (cards, netbanking, wallets, UPI intent). Verified by HMAC signature. */
    public static final String GATEWAY_CHECKOUT = "RAZORPAY";
    /** Razorpay QR Codes API (type upi_qr). Verified by fetching the QR's payments from Razorpay server-side. */
    public static final String GATEWAY_UPI_QR = "RAZORPAY_UPI_QR";

    static final String QR_PENDING = "PENDING";
    static final String QR_PAID = "PAID";
    static final String QR_EXPIRED = "EXPIRED";
    static final String QR_CANCELLED = "CANCELLED";
    static final String QR_FAILED = "FAILED";
    private static final String REASON_QR_EXPIRED = "UPI QR expired";
    private static final String REASON_QR_CANCELLED = "UPI QR cancelled by customer";
    private static final String REASON_QR_SUPERSEDED = "UPI QR closed: customer chose another payment method";
    /** Razorpay requires 2 min..2 h; 15 minutes keeps the QR usable without leaving it open for long. */
    static final long QR_VALIDITY_SECONDS = 15 * 60;
    private static final String RAZORPAY_API = "https://api.razorpay.com/v1";

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final CustomerService customers;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    public PaymentService(PaymentRepository payments,
                          OrderRepository orders,
                          CustomerService customers,
                          ObjectMapper objectMapper) {
        this.payments = payments;
        this.orders = orders;
        this.customers = customers;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    // Gateway state changes (paid/closed QR) must persist even when the request ends with an ApiException.
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentCreateOrderResponse createOrder(Long orderId) {
        Order order = customerOrder(orderId);
        requireOnlineUnpaid(order);
        requireConfigured();
        // Never leave a scannable QR open while the customer pays another way (prevents double charging).
        closePendingQr(order);

        Payment existing = payments.findFirstByOrderIdAndGatewayOrderByCreatedAtDesc(order.getId(), GATEWAY_CHECKOUT).orElse(null);
        if (existing != null && existing.getStatus() == PaymentStatus.PENDING
                && existing.getGatewayOrderId() != null && !existing.getGatewayOrderId().isBlank()
                && existing.getAmount().compareTo(order.getTotal()) == 0) {
            return response(existing);
        }

        String gatewayOrderId = createGatewayOrder(order);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setGateway(GATEWAY_CHECKOUT);
        payment.setGatewayOrderId(gatewayOrderId);
        payment.setAmount(order.getTotal());
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.PENDING);
        return response(payments.save(payment));
    }

    @Transactional
    public OrderResponse verify(PaymentVerifyRequest request) {
        Payment payment = payments.findByGatewayOrderId(request.gatewayOrderId())
                .filter(p -> GATEWAY_CHECKOUT.equals(p.getGateway()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment session not found."));
        assertOwner(payment.getOrder());

        if (payment.getStatus() == PaymentStatus.PAID) {
            return customers.detail(payment.getOrder().getId());
        }
        if (!payment.getOrder().getId().equals(request.orderId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment order does not match the VJoyKart order.");
        }
        if (!validSignature(request.gatewayOrderId(), request.gatewayPaymentId(), request.gatewaySignature())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Invalid Razorpay signature");
            payments.save(payment);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payment verification failed. Please try again.");
        }

        payment.setGatewayPaymentId(request.gatewayPaymentId());
        payment.setGatewaySignature(request.gatewaySignature());
        markPaid(payment);
        return customers.detail(payment.getOrder().getId());
    }

    // ---------------------------------------------------------------- UPI QR (Razorpay QR Codes API)

    /**
     * Returns the order's active UPI QR (or creates one) for exactly the server-side order total.
     * The QR image is generated by Razorpay; payment is only accepted after server-side confirmation.
     */
    // Gateway state changes (paid/closed QR) must persist even when the request ends with an ApiException.
    @Transactional(noRollbackFor = ApiException.class)
    public UpiQrResponse createUpiQr(Long orderId) {
        Order order = customerOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select online payment for this order.");
        }
        Payment latest = latestQr(order).orElse(null);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return paidResponse(latest, order);
        }
        requireConfigured();

        if (latest != null && latest.getStatus() == PaymentStatus.PENDING) {
            if (settleQr(latest)) return paidResponse(latest, order);
            JsonNode qr = fetchQr(latest.getGatewayOrderId());
            long closeBy = qr.path("close_by").asLong(0);
            boolean usable = "active".equals(qr.path("status").asText(""))
                    && closeBy > Instant.now().getEpochSecond() + 60
                    && latest.getAmount().compareTo(order.getTotal()) == 0;
            if (usable) return qrResponse(latest, qr, QR_PENDING, null);
            if (qrReceivedPayment(qr)) {
                // Razorpay closed the QR because it was paid; the payment listing can lag briefly.
                return qrResponse(latest, qr, QR_PENDING, "Payment received. Confirming with the bank…");
            }
            closeQrQuietly(latest.getGatewayOrderId());
            fail(latest, REASON_QR_EXPIRED);
        }

        // A Razorpay Checkout attempt for this order may already have been paid (e.g. the app closed early).
        if (checkoutAlreadyPaid(order)) {
            return paidResponse(latestQr(order).orElse(null), order);
        }

        long amountPaise = toPaise(order.getTotal());
        long closeBy = Instant.now().getEpochSecond() + QR_VALIDITY_SECONDS;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "upi_qr");
        payload.put("name", "VJoyKart");
        payload.put("usage", "single_use");
        payload.put("fixed_amount", true);
        payload.put("payment_amount", amountPaise);
        payload.put("description", "VJoyKart order #" + order.getId());
        payload.put("close_by", closeBy);
        payload.put("notes", Map.of("vjoykart_order_id", String.valueOf(order.getId())));

        JsonNode qr;
        try {
            qr = razorpay("POST", "/payments/qr_codes", payload);
        } catch (RazorpayCallException e) {
            log.warn("Razorpay UPI QR creation failed for order {}: HTTP {} {}", order.getId(), e.status, e.description);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "UPI QR is not available right now. Please pay with a UPI app, card, netbanking or wallet.");
        }
        String qrId = qr.path("id").asText("");
        String imageUrl = qr.path("image_url").asText("");
        if (qrId.isBlank() || imageUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to create the UPI QR. Please try again.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setGateway(GATEWAY_UPI_QR);
        payment.setGatewayOrderId(qrId);
        payment.setAmount(order.getTotal());
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.PENDING);
        payment = payments.save(payment);
        return qrResponse(payment, qr, QR_PENDING, null);
    }

    /** Server-side status check of the order's latest UPI QR (safe to poll; idempotent). */
    // Gateway state changes (paid/closed QR) must persist even when the request ends with an ApiException.
    @Transactional(noRollbackFor = ApiException.class)
    public UpiQrResponse upiQrStatus(Long orderId) {
        Order order = customerOrder(orderId);
        Payment qrPayment = latestQr(order)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No UPI QR payment was started for this order."));
        if (order.getPaymentStatus() == PaymentStatus.PAID || qrPayment.getStatus() == PaymentStatus.PAID) {
            return paidResponse(qrPayment, order);
        }
        requireConfigured();
        // Money may arrive even after expiry/cancel races, so always ask Razorpay first.
        if (settleQr(qrPayment)) return paidResponse(qrPayment, order);
        if (qrPayment.getStatus() == PaymentStatus.FAILED) {
            return qrResponse(qrPayment, null, closedStatus(qrPayment), null);
        }

        JsonNode qr = fetchQr(qrPayment.getGatewayOrderId());
        boolean active = "active".equals(qr.path("status").asText(""))
                && qr.path("close_by").asLong(0) > Instant.now().getEpochSecond();
        if (active) return qrResponse(qrPayment, qr, QR_PENDING, null);
        if (qrReceivedPayment(qr)) {
            return qrResponse(qrPayment, qr, QR_PENDING, "Payment received. Confirming with the bank…");
        }
        closeQrQuietly(qrPayment.getGatewayOrderId());
        fail(qrPayment, REASON_QR_EXPIRED);
        return qrResponse(qrPayment, qr, QR_EXPIRED, null);
    }

    /** Closes the order's QR so it can no longer be paid. Returns PAID instead if money already arrived. */
    // Gateway state changes (paid/closed QR) must persist even when the request ends with an ApiException.
    @Transactional(noRollbackFor = ApiException.class)
    public UpiQrResponse cancelUpiQr(Long orderId) {
        Order order = customerOrder(orderId);
        Payment qrPayment = latestQr(order).orElse(null);
        if (order.getPaymentStatus() == PaymentStatus.PAID) return paidResponse(qrPayment, order);
        if (qrPayment == null) {
            return new UpiQrResponse(null, order.getId(), null, null, order.getTotal(), currency, null, QR_CANCELLED, null, null);
        }
        if (qrPayment.getStatus() == PaymentStatus.PENDING || qrPayment.getStatus() == PaymentStatus.FAILED) {
            requireConfigured();
            if (settleQr(qrPayment)) return paidResponse(qrPayment, order);
        }
        if (qrPayment.getStatus() == PaymentStatus.PENDING) {
            closeQrQuietly(qrPayment.getGatewayOrderId());
            // A payment may have landed between the check and the close.
            if (settleQr(qrPayment)) return paidResponse(qrPayment, order);
            fail(qrPayment, REASON_QR_CANCELLED);
        }
        return qrResponse(qrPayment, null, closedStatus(qrPayment), null);
    }

    private Optional<Payment> latestQr(Order order) {
        return payments.findFirstByOrderIdAndGatewayOrderByCreatedAtDesc(order.getId(), GATEWAY_UPI_QR);
    }

    /** Closes a pending QR before another payment method is started. Throws if it was already paid. */
    private void closePendingQr(Order order) {
        Payment qrPayment = latestQr(order).orElse(null);
        if (qrPayment == null || qrPayment.getStatus() != PaymentStatus.PENDING) return;
        if (settleQr(qrPayment)) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is already completed for this order.");
        }
        closeQrQuietly(qrPayment.getGatewayOrderId());
        if (settleQr(qrPayment)) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is already completed for this order.");
        }
        fail(qrPayment, REASON_QR_SUPERSEDED);
    }

    /**
     * Fetches the QR's payments from Razorpay and marks the order PAID only when a captured payment
     * of exactly the expected amount and currency exists.
     */
    private boolean settleQr(Payment qrPayment) {
        if (qrPayment.getStatus() == PaymentStatus.PAID) return true;
        JsonNode list;
        try {
            list = razorpay("GET", "/payments/qr_codes/" + qrPayment.getGatewayOrderId() + "/payments", null);
        } catch (RazorpayCallException e) {
            log.warn("Unable to fetch UPI QR payments for payment {}: HTTP {}", qrPayment.getId(), e.status);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to check the payment status right now. Please try again.");
        }
        Optional<JsonNode> match = findCapturedPayment(list.path("items"), toPaise(qrPayment.getAmount()), qrPayment.getCurrency());
        if (match.isEmpty()) return false;
        qrPayment.setGatewayPaymentId(match.get().path("id").asText(null));
        markPaid(qrPayment);
        return true;
    }

    /** Pure matching rule (unit tested): captured, exact amount in paise, same currency. */
    static Optional<JsonNode> findCapturedPayment(JsonNode items, long expectedPaise, String expectedCurrency) {
        if (items == null || !items.isArray()) return Optional.empty();
        for (JsonNode item : items) {
            if ("captured".equals(item.path("status").asText(""))
                    && item.path("amount").asLong(-1) == expectedPaise
                    && expectedCurrency.equalsIgnoreCase(item.path("currency").asText(""))
                    && !item.path("id").asText("").isBlank()) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private static boolean qrReceivedPayment(JsonNode qr) {
        return qr.path("payments_count_received").asLong(0) > 0
                || "paid".equals(qr.path("close_reason").asText(""));
    }

    /** True when a pending Razorpay Checkout order for this order was paid (fetched from Razorpay, not trusted from the client). */
    private boolean checkoutAlreadyPaid(Order order) {
        Payment checkout = payments.findFirstByOrderIdAndGatewayOrderByCreatedAtDesc(order.getId(), GATEWAY_CHECKOUT).orElse(null);
        if (checkout == null || checkout.getStatus() != PaymentStatus.PENDING) return false;
        try {
            JsonNode gatewayOrder = razorpay("GET", "/orders/" + checkout.getGatewayOrderId(), null);
            if ("paid".equals(gatewayOrder.path("status").asText(""))
                    && gatewayOrder.path("amount_paid").asLong(-1) == toPaise(checkout.getAmount())) {
                markPaid(checkout);
                return true;
            }
            return false;
        } catch (RazorpayCallException e) {
            log.warn("Unable to fetch Razorpay order for payment {}: HTTP {}", checkout.getId(), e.status);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to start UPI QR payment. Please try again.");
        }
    }

    private JsonNode fetchQr(String qrId) {
        try {
            return razorpay("GET", "/payments/qr_codes/" + qrId, null);
        } catch (RazorpayCallException e) {
            log.warn("Unable to fetch UPI QR {}: HTTP {}", qrId, e.status);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to check the payment status right now. Please try again.");
        }
    }

    private void closeQrQuietly(String qrId) {
        try {
            razorpay("POST", "/payments/qr_codes/" + qrId + "/close", null);
        } catch (RazorpayCallException e) {
            // Already closed (paid/expired) QRs reject the close call; the status is re-checked by the caller.
            log.info("Razorpay QR {} close returned HTTP {}", qrId, e.status);
        }
    }

    private void fail(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payments.save(payment);
    }

    private void markPaid(Payment payment) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setFailureReason(null);
        Order order = payment.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        orders.save(order);
        payments.save(payment);
    }

    private static String closedStatus(Payment payment) {
        String reason = payment.getFailureReason() == null ? "" : payment.getFailureReason();
        if (REASON_QR_EXPIRED.equals(reason)) return QR_EXPIRED;
        if (REASON_QR_CANCELLED.equals(reason) || REASON_QR_SUPERSEDED.equals(reason)) return QR_CANCELLED;
        return QR_FAILED;
    }

    private UpiQrResponse paidResponse(Payment payment, Order order) {
        return new UpiQrResponse(
                payment == null ? null : payment.getId(),
                order.getId(),
                payment == null ? null : payment.getGatewayOrderId(),
                null,
                order.getTotal(),
                payment == null ? currency : payment.getCurrency(),
                null,
                QR_PAID,
                null,
                customers.detail(order.getId()));
    }

    private UpiQrResponse qrResponse(Payment payment, JsonNode qr, String status, String message) {
        boolean showImage = QR_PENDING.equals(status) && qr != null && "active".equals(qr.path("status").asText(""));
        return new UpiQrResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getGatewayOrderId(),
                showImage ? qr.path("image_url").asText(null) : null,
                payment.getAmount(),
                payment.getCurrency(),
                qr == null ? null : qr.path("close_by").asLong(0),
                status,
                message,
                null);
    }

    // ---------------------------------------------------------------- shared helpers

    private void requireOnlineUnpaid(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select online payment for this order.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is already completed for this order.");
        }
    }

    private Order customerOrder(Long orderId) {
        if (orderId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Order id is required.");
        }
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
        assertOwner(order);
        return order;
    }

    private void assertOwner(Order order) {
        Long currentUserId = CurrentUser.id();
        if (currentUserId == null || order.getCustomer() == null || !currentUserId.equals(order.getCustomer().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Order is not yours.");
        }
    }

    private void requireConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is not configured on the server. Please use Cash on Delivery or contact support.");
        }
    }

    static long toPaise(BigDecimal amount) {
        long paise = amount.multiply(new BigDecimal("100")).longValueExact();
        if (paise <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Order amount must be greater than zero.");
        }
        return paise;
    }

    private String createGatewayOrder(Order order) {
        try {
            long amountPaise = toPaise(order.getTotal());
            Map<String, Object> payload = Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", "VJK-" + order.getId(),
                    "payment_capture", 1);
            JsonNode json = razorpay("POST", "/orders", payload);
            String id = json.path("id").asText("");
            if (id.isBlank()) throw new IllegalStateException("Razorpay did not return an order id");
            return id;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Unable to start Razorpay payment. Please try again.");
        }
    }

    /** Authenticated call to the Razorpay REST API. Never logs credentials or payment instrument data. */
    private JsonNode razorpay(String method, String path, Object body) {
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(RAZORPAY_API + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth);
            if ("POST".equals(method)) {
                String json = body == null ? "{}" : objectMapper.writeValueAsString(body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RazorpayCallException(response.statusCode(), json.path("error").path("description").asText(""));
            }
            return json;
        } catch (RazorpayCallException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RazorpayCallException(0, "interrupted");
        } catch (Exception e) {
            throw new RazorpayCallException(0, e.getClass().getSimpleName());
        }
    }

    private static final class RazorpayCallException extends RuntimeException {
        final int status;
        final String description;

        RazorpayCallException(int status, String description) {
            super("Razorpay HTTP " + status);
            this.status = status;
            this.description = description;
        }
    }

    // ---------------------------------------------------------------- webhook

    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay webhook is not configured.");
        }
        if (!validWebhookSignature(payload, signature)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook signature.");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("");
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String gatewayPaymentId = paymentEntity.path("id").asText("");

            if ("qr_code.credited".equals(event)) {
                String qrId = root.path("payload").path("qr_code").path("entity").path("id").asText("");
                if (qrId.isBlank()) return;
                Payment payment = payments.findByGatewayOrderId(qrId)
                        .filter(p -> GATEWAY_UPI_QR.equals(p.getGateway()))
                        .orElse(null);
                if (payment == null || payment.getStatus() == PaymentStatus.PAID) return;
                com.fasterxml.jackson.databind.node.ArrayNode single = objectMapper.createArrayNode().add(paymentEntity);
                if (findCapturedPayment(single, toPaise(payment.getAmount()), payment.getCurrency()).isPresent()) {
                    payment.setGatewayPaymentId(gatewayPaymentId);
                    markPaid(payment);
                }
                return;
            }

            String gatewayOrderId = paymentEntity.path("order_id").asText("");
            if (gatewayOrderId.isBlank()) return;

            Payment payment = payments.findByGatewayOrderId(gatewayOrderId).orElse(null);
            if (payment == null) return;

            if ("payment.captured".equals(event)) {
                payment.setGatewayPaymentId(gatewayPaymentId);
                markPaid(payment);
            } else if ("payment.failed".equals(event) && payment.getStatus() != PaymentStatus.PAID) {
                payment.setGatewayPaymentId(gatewayPaymentId);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(paymentEntity.path("error_description").asText("Razorpay payment failed"));
                payments.save(payment);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook payload.");
        }
    }

    private boolean validWebhookSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validSignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Payment verification is unavailable.");
        }
    }

    private PaymentCreateOrderResponse response(Payment payment) {
        return new PaymentCreateOrderResponse(
                payment.getId(),
                payment.getOrder().getId(),
                keyId,
                payment.getGateway(),
                payment.getGatewayOrderId(),
                payment.getAmount(),
                payment.getCurrency());
    }
}
