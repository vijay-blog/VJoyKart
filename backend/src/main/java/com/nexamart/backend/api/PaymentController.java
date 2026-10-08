package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.PaymentCreateOrderResponse;
import com.nexamart.backend.api.ApiModels.PaymentVerifyRequest;
import com.nexamart.backend.api.ApiModels.OrderResponse;
import com.nexamart.backend.api.ApiModels.UpiQrResponse;
import com.nexamart.backend.service.CustomerService;
import com.nexamart.backend.service.DispatchCoordinator;
import com.nexamart.backend.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;
    private final DispatchCoordinator dispatch;
    private final CustomerService customers;

    public PaymentController(PaymentService service, DispatchCoordinator dispatch, CustomerService customers) {
        this.service = service;
        this.dispatch = dispatch;
        this.customers = customers;
    }

    @PostMapping("/create-order")
    public PaymentCreateOrderResponse createOrder(@RequestBody CreatePaymentOrderRequest request) {
        return service.createOrder(request.orderId());
    }

    @PostMapping("/verify")
    public OrderResponse verify(@Valid @RequestBody PaymentVerifyRequest request) {
        OrderResponse paid = service.verify(request);
        // Online orders become dispatchable once PAID; assign a delivery partner right away (scheduler retries otherwise).
        Long id = Long.valueOf(paid.orderId());
        dispatch.dispatchSafely(id);
        return customers.detail(id);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {
        service.handleWebhook(payload, signature == null ? "" : signature);
        return ResponseEntity.ok().build();
    }

    /** Creates (or returns the still-active) Razorpay UPI QR for the order's exact server-side total. */
    @PostMapping("/upi-qr")
    public UpiQrResponse createUpiQr(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return dispatchIfPaid(service.createUpiQr(request.orderId()));
    }

    /** Server-side verification of the QR payment with Razorpay; the app polls this while the QR is shown. */
    @PostMapping("/upi-qr/status")
    public UpiQrResponse upiQrStatus(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return dispatchIfPaid(service.upiQrStatus(request.orderId()));
    }

    /** Closes the QR at Razorpay so it cannot be paid later (returns PAID if payment already arrived). */
    @PostMapping("/upi-qr/cancel")
    public UpiQrResponse cancelUpiQr(@Valid @RequestBody CreatePaymentOrderRequest request) {
        return dispatchIfPaid(service.cancelUpiQr(request.orderId()));
    }

    private UpiQrResponse dispatchIfPaid(UpiQrResponse response) {
        if (!"PAID".equals(response.status())) return response;
        Long id = response.orderId();
        dispatch.dispatchSafely(id);
        return new UpiQrResponse(response.paymentId(), id, response.qrCodeId(), null, response.amount(),
                response.currency(), null, response.status(), response.message(), customers.detail(id));
    }

    public record CreatePaymentOrderRequest(@NotNull Long orderId) {}
}
