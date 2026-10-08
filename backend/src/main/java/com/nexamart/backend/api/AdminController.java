package com.nexamart.backend.api;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.OrderStatus;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.service.AdminService;
import com.nexamart.backend.service.DispatchCoordinator;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Admin order, customer and delivery-partner management. Secured with ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminService admin;
  private final DispatchCoordinator dispatch;

  public AdminController(AdminService admin, DispatchCoordinator dispatch) {
    this.admin = admin;
    this.dispatch = dispatch;
  }

  @GetMapping("/dashboard")
  public DashboardResponse dashboard() { return admin.dashboard(); }

  @GetMapping("/orders")
  public PageResponse<OrderResponse> orders(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String q, @RequestParam(required = false) String status,
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    return admin.orderPage(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100), blank(q), orderStatus(status), date(from, false), date(to, true));
  }

  @GetMapping("/orders/{id}")
  public OrderResponse order(@PathVariable Long id) { return admin.detail(id); }

  @PatchMapping("/orders/{id}/status")
  public OrderResponse status(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
    admin.status(id, request);
    return admin.detail(id);
  }

  @PostMapping("/orders/{id}/cancel")
  public OrderResponse cancel(@PathVariable Long id, @RequestBody(required = false) CancelRequest request) {
    admin.cancel(id, request);
    dispatch.dispatchPendingOrders();
    return admin.detail(id);
  }

  /** Manual override of the automatic assignment. */
  @PostMapping("/orders/{id}/assign-delivery")
  public OrderResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
    admin.assign(id, request);
    return admin.detail(id);
  }

  /** Re-runs automatic dispatch for an order that is still waiting for a delivery partner. */
  @PostMapping("/orders/{id}/dispatch")
  public OrderResponse redispatch(@PathVariable Long id) {
    dispatch.dispatchSafely(id);
    return admin.detail(id);
  }

  @GetMapping("/customers")
  public PageResponse<CustomerResponse> customers(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String q, @RequestParam(required = false) String status) {
    return admin.customers(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100), blank(q), accountStatus(status));
  }

  @GetMapping("/customers/{id}")
  public CustomerResponse customer(@PathVariable Long id) { return admin.customerDetail(id); }

  @PostMapping("/customers/{id}/actions")
  public ResponseEntity<Void> customerAction(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
    admin.customerAction(id, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/delivery-partners")
  public PageResponse<DeliveryPartnerResponse> partners(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String q, @RequestParam(required = false) String status) {
    return admin.partners(Math.max(page, 0), Math.min(Math.max(pageSize, 1), 100), blank(q), accountStatus(status));
  }

  @GetMapping("/delivery-partners/{id}")
  public DeliveryPartnerResponse partner(@PathVariable Long id) { return admin.partnerDetail(id); }

  @PostMapping("/delivery-partners/{id}/actions")
  public ResponseEntity<Void> partnerAction(@PathVariable Long id, @Valid @RequestBody ActionRequest request) {
    admin.partnerAction(id, request);
    return ResponseEntity.noContent().build();
  }

  private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

  private static OrderStatus orderStatus(String s) {
    if (blank(s) == null || "ALL".equalsIgnoreCase(s)) return null;
    try { return OrderStatus.valueOf(s.trim().toUpperCase()); }
    catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown order status."); }
  }

  private static AccountStatus accountStatus(String s) {
    if (blank(s) == null || "ALL".equalsIgnoreCase(s)) return null;
    try { return AccountStatus.valueOf(s.trim().toUpperCase()); }
    catch (IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown account status."); }
  }

  private static Instant date(String s, boolean endExclusive) {
    if (blank(s) == null) return null;
    try {
      if (s.length() <= 10) {
        LocalDate d = LocalDate.parse(s.trim());
        return (endExclusive ? d.plusDays(1) : d).atStartOfDay().toInstant(ZoneOffset.UTC);
      }
      return Instant.parse(s.trim());
    } catch (RuntimeException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date: " + s);
    }
  }
}
