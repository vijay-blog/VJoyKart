package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.domain.DeliveryStatus;
import com.nexamart.backend.service.DeliveryService;
import com.nexamart.backend.service.DispatchCoordinator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Delivery-partner (delivery boy) API. Secured with ROLE_DELIVERY_PARTNER in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/delivery")
public class DeliveryController {
  private final DeliveryService delivery;
  private final DispatchCoordinator dispatch;

  public DeliveryController(DeliveryService delivery, DispatchCoordinator dispatch) {
    this.delivery = delivery;
    this.dispatch = dispatch;
  }

  @GetMapping("/dashboard")
  public DeliveryDashboardResponse dashboard() { return delivery.dashboard(); }

  @GetMapping("/orders")
  public PageResponse<OrderResponse> orders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String q) {
    return delivery.orders(page, pageSize, q);
  }

  @GetMapping("/history")
  public PageResponse<OrderResponse> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String q) {
    return delivery.history(page, pageSize, q);
  }

  @GetMapping("/orders/{id}")
  public OrderResponse order(@PathVariable Long id) { return delivery.detail(id); }

  /** Advances the delivery one step: PACKING, ON_THE_WAY, ARRIVED or DELIVERED. */
  @PostMapping("/orders/{id}/status")
  public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody DeliveryStatusUpdateRequest request) {
    OrderResponse response = delivery.updateStatus(id, request);
    if (DeliveryStatus.DELIVERED.name().equals(response.deliveryStatus())) {
      dispatch.dispatchPendingOrders();
    }
    return response;
  }

  @GetMapping("/availability")
  public AvailabilityResponse availability() { return delivery.availability(); }

  @PutMapping("/availability")
  public AvailabilityResponse updateAvailability(@RequestBody AvailabilityRequest request) {
    AvailabilityResponse response = delivery.updateAvailability(request.available());
    if (response.available()) dispatch.dispatchPendingOrders();
    return response;
  }

  @PutMapping("/location")
  public LocationUpdateResponse location(@Valid @RequestBody LocationUpdateRequest request) {
    LocationUpdateResponse response = delivery.updateLocation(request);
    dispatch.dispatchPendingOrders();
    return response;
  }

  @GetMapping("/notifications")
  public DeliveryNotificationPage notifications(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return delivery.notifications(page, pageSize, unreadOnly);
  }

  @PostMapping("/notifications/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable Long id) {
    delivery.markRead(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/notifications/read-all")
  public ResponseEntity<Void> markAllRead() {
    delivery.markAllRead();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/profile")
  public ProfileResponse profile() { return delivery.profile(); }

  @PutMapping("/profile")
  public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdate request) { return delivery.updateProfile(request); }

  @GetMapping("/earnings/summary")
  public EarningsSummary earningsSummary() { return delivery.earningsSummary(); }

  @GetMapping("/earnings/history")
  public PageResponse<EarningResponse> earningsHistory(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return delivery.earningHistory(page, pageSize);
  }
}
