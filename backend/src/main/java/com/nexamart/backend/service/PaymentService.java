package com.nexamart.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexamart.backend.api.ApiModels.PaymentCreateOrderResponse;
import com.nexamart.backend.api.ApiModels.PaymentVerifyRequest;
import com.nexamart.backend.api.ApiModels.OrderResponse;
import com.nexamart.backend.domain.Order;
import com.nexamart.backend.domain.Payment;
import com.nexamart.backend.domain.PaymentMethod;
import com.nexamart.backend.domain.PaymentStatus;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.OrderRepository;
import com.nexamart.backend.repository.PaymentRepository;
import com.nexamart.backend.security.CurrentUser;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Service
public class PaymentService {
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

    @Transactional
    public PaymentCreateOrderResponse createOrder(Long orderId) {
        Order order = customerOrder(orderId);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select online payment for this order.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment is already completed for this order.");
        }
        requireConfigured();

        Payment existing = payments.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        if (existing != null && existing.getStatus() == PaymentStatus.PENDING
                && existing.getGatewayOrderId() != null && !existing.getGatewayOrderId().isBlank()) {
            return response(existing);
        }

        String gatewayOrderId = createGatewayOrder(order);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setGateway("RAZORPAY");
        payment.setGatewayOrderId(gatewayOrderId);
        payment.setAmount(order.getTotal());
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.PENDING);
        return response(payments.save(payment));
    }

    @Transactional
    public OrderResponse verify(PaymentVerifyRequest request) {
        Payment payment = payments.findByGatewayOrderId(request.gatewayOrderId())
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
        payment.setStatus(PaymentStatus.PAID);
        payment.setFailureReason(null);

        Order order = payment.getOrder();
        order.setPaymentStatus(PaymentStatus.PAID);
        orders.save(order);
        payments.save(payment);
        return customers.detail(order.getId());
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

    private String createGatewayOrder(Order order) {
        try {
            long amountPaise = order.getTotal().multiply(new BigDecimal("100")).longValueExact();
            if (amountPaise <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Order amount must be greater than zero.");
            }

            Map<String, Object> payload = Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", "VJK-" + order.getId(),
                    "payment_capture", 1);
            String body = objectMapper.writeValueAsString(payload);
            String auth = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Razorpay rejected order creation: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
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
            String gatewayOrderId = paymentEntity.path("order_id").asText("");
            if (gatewayOrderId.isBlank()) return;

            Payment payment = payments.findByGatewayOrderId(gatewayOrderId).orElse(null);
            if (payment == null) return;

            if ("payment.captured".equals(event)) {
                payment.setGatewayPaymentId(gatewayPaymentId);
                payment.setStatus(PaymentStatus.PAID);
                payment.setFailureReason(null);
                payment.getOrder().setPaymentStatus(PaymentStatus.PAID);
                orders.save(payment.getOrder());
                payments.save(payment);
            } else if ("payment.failed".equals(event)) {
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
