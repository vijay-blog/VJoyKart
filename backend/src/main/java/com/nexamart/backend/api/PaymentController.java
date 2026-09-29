package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.PaymentCreateOrderResponse;
import com.nexamart.backend.api.ApiModels.PaymentVerifyRequest;
import com.nexamart.backend.api.ApiModels.OrderResponse;
import com.nexamart.backend.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/create-order")
    public PaymentCreateOrderResponse createOrder(@RequestBody CreatePaymentOrderRequest request) {
        return service.createOrder(request.orderId());
    }

    @PostMapping("/verify")
    public OrderResponse verify(@Valid @RequestBody PaymentVerifyRequest request) {
        return service.verify(request);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {
        service.handleWebhook(payload, signature == null ? "" : signature);
        return ResponseEntity.ok().build();
    }

    public record CreatePaymentOrderRequest(@NotNull Long orderId) {}
}
