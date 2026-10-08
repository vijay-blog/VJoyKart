package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.service.CustomerService;
import com.nexamart.backend.service.DispatchCoordinator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {
  final CustomerService s;
  final DispatchCoordinator dispatch;

  public CustomerController(CustomerService s, DispatchCoordinator dispatch) { this.s = s; this.dispatch = dispatch; }

  /** Creates the order (own transaction), then assigns a delivery partner on the backend and returns the fresh state. */
  @PostMapping("/orders")
  OrderResponse create(@Valid @RequestBody CreateOrderRequest r) {
    OrderResponse created = s.create(r);
    Long id = Long.valueOf(created.orderId());
    dispatch.dispatchSafely(id);
    return s.detail(id);
  }

  @GetMapping("/orders")
  PageResponse<OrderResponse> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) { return s.history(page, pageSize); }

  @GetMapping("/orders/{id}")
  OrderResponse detail(@PathVariable Long id) { return s.detail(id); }

  /** Lightweight polling endpoint for the customer live-tracking screen. */
  @GetMapping("/orders/{id}/tracking")
  OrderTrackingResponse tracking(@PathVariable Long id) { return s.tracking(id); }
}
