package com.nexamart.backend.domain;

import java.util.List;

/**
 * Delivery lifecycle of an order. Independent from {@link PaymentStatus}.
 *
 * <p>Allowed flow (strictly sequential, enforced server-side):
 * ORDER_PLACED -> DELIVERY_ASSIGNED -> PACKING -> ON_THE_WAY -> ARRIVED -> DELIVERED.
 * CANCELLED is terminal and only reachable through admin cancellation.
 */
public enum DeliveryStatus {
  ORDER_PLACED("Order placed"),
  DELIVERY_ASSIGNED("Delivery boy assigned"),
  PACKING("Packing"),
  ON_THE_WAY("On the way"),
  ARRIVED("Arrived"),
  DELIVERED("Delivered"),
  CANCELLED("Cancelled");

  /** The five customer-facing delivery progress steps, in order. */
  public static final List<DeliveryStatus> PROGRESS_STEPS = List.of(DELIVERY_ASSIGNED, PACKING, ON_THE_WAY, ARRIVED, DELIVERED);

  /** States in which the order occupies its delivery partner. */
  public static final List<DeliveryStatus> ACTIVE = List.of(DELIVERY_ASSIGNED, PACKING, ON_THE_WAY, ARRIVED);

  private final String label;

  DeliveryStatus(String label) { this.label = label; }

  public String label() { return label; }

  public boolean isActive() { return ACTIVE.contains(this); }

  public boolean isTerminal() { return this == DELIVERED || this == CANCELLED; }

  /** The only status a delivery partner may move to next, or null when no partner action is possible. */
  public DeliveryStatus nextPartnerStep() {
    return switch (this) {
      case DELIVERY_ASSIGNED -> PACKING;
      case PACKING -> ON_THE_WAY;
      case ON_THE_WAY -> ARRIVED;
      case ARRIVED -> DELIVERED;
      default -> null;
    };
  }

  public boolean canTransitionTo(DeliveryStatus target) {
    if (target == null) return false;
    if (this == ORDER_PLACED) return target == DELIVERY_ASSIGNED;
    return nextPartnerStep() == target;
  }

  /** Keeps the legacy order status (used by existing dashboards/filters) in sync. */
  public OrderStatus legacyOrderStatus() {
    return switch (this) {
      case ORDER_PLACED -> OrderStatus.PENDING;
      case DELIVERY_ASSIGNED -> OrderStatus.ASSIGNED;
      case PACKING -> OrderStatus.PREPARING;
      case ON_THE_WAY, ARRIVED -> OrderStatus.OUT_FOR_DELIVERY;
      case DELIVERED -> OrderStatus.DELIVERED;
      case CANCELLED -> OrderStatus.CANCELLED;
    };
  }

  public static DeliveryStatus parse(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    return switch (value) {
      case "START_PACKING" -> PACKING;
      case "OUT_FOR_DELIVERY" -> ON_THE_WAY;
      case "COMPLETE", "COMPLETED" -> DELIVERED;
      default -> {
        try { yield DeliveryStatus.valueOf(value); } catch (IllegalArgumentException e) { yield null; }
      }
    };
  }
}
