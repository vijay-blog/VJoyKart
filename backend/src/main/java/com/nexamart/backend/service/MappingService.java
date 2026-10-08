package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.OrderItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class MappingService {
  private final OrderItemRepository items;
  private final VJoyKartProperties props;

  public MappingService(OrderItemRepository items, VJoyKartProperties props) {
    this.items = items;
    this.props = props;
  }

  public UserResponse user(UserAccount u) {
    return u == null ? null : new UserResponse(u.getId(), u.getName(), u.getPhone(), u.getEmail(), u.getRole().name());
  }

  /** Name + phone only: the delivery-partner data a customer needs to receive the order. */
  public DeliveryPartnerContact partnerContact(Order o) {
    UserAccount p = o.getDeliveryPartner();
    return p == null ? null : new DeliveryPartnerContact(p.getId().toString(), p.getName(), p.getPhone());
  }

  public StoreDto store() {
    var s = props.getStore();
    return new StoreDto(s.getName(), s.getLatitude(), s.getLongitude(), s.getMapsUrl(), s.getAddress());
  }

  public GeoPointDto deliveryLocation(Order o) {
    Address a = o.getDeliveryAddress();
    return a == null || a.getLatitude() == null || a.getLongitude() == null ? null : new GeoPointDto(a.getLatitude(), a.getLongitude());
  }

  public static String assignmentStatus(Order o) {
    DeliveryStatus s = o.getDeliveryStatus();
    if (s == DeliveryStatus.CANCELLED) return "CANCELLED";
    if (s == DeliveryStatus.DELIVERED) return "COMPLETED";
    return o.getDeliveryPartner() == null ? "AWAITING_ASSIGNMENT" : "ASSIGNED";
  }

  /** The five customer-facing delivery steps with COMPLETED / CURRENT / PENDING states. */
  public static List<DeliveryStepDto> deliveryProgress(Order o) {
    DeliveryStatus status = o.getDeliveryStatus();
    int current = DeliveryStatus.PROGRESS_STEPS.indexOf(status);
    List<DeliveryStepDto> steps = new ArrayList<>();
    for (int i = 0; i < DeliveryStatus.PROGRESS_STEPS.size(); i++) {
      DeliveryStatus step = DeliveryStatus.PROGRESS_STEPS.get(i);
      String state;
      if (current < 0) state = "PENDING";
      else if (i < current || (i == current && step == DeliveryStatus.DELIVERED)) state = "COMPLETED";
      else if (i == current) state = "CURRENT";
      else state = "PENDING";
      Instant at = "PENDING".equals(state) ? null : o.stepTime(step);
      steps.add(new DeliveryStepDto(step.name(), step.label(), state, at == null ? null : at.toString()));
    }
    return steps;
  }

  /** Partner actions are the single valid next delivery step; admin actions keep the existing behaviour. */
  public List<String> orderActions(Order o, boolean partner) {
    List<String> a = new ArrayList<>();
    if (partner) {
      Long me = com.nexamart.backend.security.CurrentUser.id();
      if (o.getDeliveryPartner() == null || !o.getDeliveryPartner().getId().equals(me)) return a;
      DeliveryStatus next = o.getDeliveryStatus().nextPartnerStep();
      if (next != null) a.add(next.name());
      return a;
    }
    switch (o.getStatus()) {
      case PENDING -> a.add("CONFIRMED");
      case CONFIRMED -> a.add("PREPARING");
      case PREPARING -> a.add("READY");
      default -> {}
    }
    boolean open = !o.getDeliveryStatus().isTerminal() && o.getStatus() != OrderStatus.CANCELLED && o.getStatus() != OrderStatus.DELIVERED;
    if (open) a.add(o.getDeliveryPartner() == null ? "ASSIGN" : "REASSIGN");
    if (open) a.add("CANCEL");
    return a;
  }

  public String address(Order o) {
    if (o.getDeliveryAddress() == null) return null;
    return String.join(", ", Arrays.asList(o.getDeliveryAddress().getAddressLine(), o.getDeliveryAddress().getCity(),
            o.getDeliveryAddress().getState(), o.getDeliveryAddress().getPostalCode()).stream()
        .filter(Objects::nonNull).filter(x -> !x.isBlank()).toList());
  }

  public List<OrderItemResponse> itemResponses(Order o) {
    return items.findByOrderId(o.getId()).stream()
        .map(i -> new OrderItemResponse(i.getId(), i.getProduct().getId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
        .toList();
  }

  private static String ts(Instant i) { return i == null ? null : i.toString(); }

  private static boolean currentUserIsPartner() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DELIVERY_PARTNER"));
  }

  public OrderResponse order(Order o) {
    boolean partner = currentUserIsPartner();
    List<OrderItemResponse> ir = itemResponses(o);
    List<OrderTimelineDto> tl = new ArrayList<>();
    tl.add(new OrderTimelineDto(DeliveryStatus.ORDER_PLACED.name(), o.getCreatedAt().toString()));
    for (DeliveryStatus step : DeliveryStatus.PROGRESS_STEPS) {
      if (o.stepTime(step) != null) tl.add(new OrderTimelineDto(step.name(), o.stepTime(step).toString()));
    }
    if (o.getDeliveryStatus() == DeliveryStatus.CANCELLED) tl.add(new OrderTimelineDto("CANCELLED", o.getUpdatedAt().toString()));
    PaymentInfoDto pay = new PaymentInfoDto(o.getPaymentMethod().name(), o.getPaymentStatus().name(), null);
    BigDecimal discount = ir.stream().map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())).subtract(i.lineTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
    OrderTotalsDto totals = new OrderTotalsDto(o.getSubtotal().toString(), o.getDeliveryFee().toString(), discount.max(BigDecimal.ZERO).toString(), "0.00", o.getTotal().toString(), o.getCurrencyCode());
    UserAccount dp = o.getDeliveryPartner();
    String assignedAt = ts(o.getAssignedAt() != null ? o.getAssignedAt() : o.getAcceptedAt());
    DeliveryInfoDto di = new DeliveryInfoDto(o.getDeliveryStatus().name(), dp == null ? null : dp.getName(), assignedAt,
        dp == null ? null : dp.getId().toString(), dp == null ? null : dp.getPhone(), assignmentStatus(o));
    return new OrderResponse(o.getId().toString(), o.getCustomer().getName(), o.getCustomer().getPhone(), address(o), o.getTotal().toString(),
        o.getCurrencyCode(), o.getStatus().name(), o.getPaymentStatus().name(), assignedAt, o.getCreatedAt().toString(), ir.size(),
        o.getDeliveryStatus().name(), user(o.getCustomer()), ir, pay, totals, di, tl, orderActions(o, false),
        o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED, partnerContact(o), orderActions(o, partner),
        false, null, null, o.getDeliveryStatus().label(), assignmentStatus(o), store(), deliveryLocation(o), deliveryProgress(o));
  }

  public OrderTrackingResponse tracking(Order o) {
    return new OrderTrackingResponse(o.getId().toString(), o.getStatus().name(), o.getPaymentMethod().name(), o.getPaymentStatus().name(),
        o.getTotal().toString(), o.getCurrencyCode(), o.getDeliveryStatus().name(), o.getDeliveryStatus().label(), assignmentStatus(o),
        ts(o.getAssignedAt()), partnerContact(o), store(), address(o), deliveryLocation(o), deliveryProgress(o), ts(o.getUpdatedAt()));
  }

  public DeliveryOrderSummary deliverySummary(Order o) {
    return new DeliveryOrderSummary(o.getId().toString(), o.getCustomer().getName(), o.getCustomer().getPhone(), address(o),
        o.getTotal().toString(), o.getCurrencyCode(), o.getDeliveryStatus().name(), o.getPaymentStatus().name(), ts(o.getAssignedAt()),
        o.getCreatedAt().toString(), o.getTotal().toString(), o.getDeliveryStatus().name(), o.getDeliveryStatus().label(), props.getStore().getName());
  }

  public ProductResponse product(Product p) {
    BigDecimal discount = p.getDiscount() == null ? BigDecimal.ZERO : p.getDiscount();
    BigDecimal discounted = p.getPrice().subtract(discount).max(BigDecimal.ZERO);
    BigDecimal percent = p.getPrice().signum() == 0 ? BigDecimal.ZERO : discount.multiply(BigDecimal.valueOf(100)).divide(p.getPrice(), 2, java.math.RoundingMode.HALF_UP);
    String status = p.isAvailable() ? "ACTIVE" : "INACTIVE";
    String availability = !p.isAvailable() || p.getStock() == null || p.getStock() <= 0 ? "OUT_OF_STOCK" : p.getStock() <= 5 ? "LOW_STOCK" : "IN_STOCK";
    List<String> actions = p.isAvailable() ? List.of("EDIT", "DEACTIVATE", "DELETE") : List.of("EDIT", "ACTIVATE", "DELETE");
    return new ProductResponse(p.getId().toString(), p.getName(), p.getDescription(), p.getCategory().getId().toString(), p.getCategory().getName(),
        p.getPrice().toString(), discounted.toString(), discount.toString(), percent.toString(), "INR", p.getStock(), p.getSku(), p.getUnit(),
        status, availability, p.getImageUrl(), p.getCreatedAt(), p.getUpdatedAt(), actions);
  }

  public CategoryResponse category(Category c, int count) {
    return new CategoryResponse(c.getId().toString(), c.getName(), c.getDescription(), c.getImageUrl(), c.isActive(), count, c.getSortOrder(),
        c.getCreatedAt(), c.getUpdatedAt(), c.isActive() ? List.of("EDIT", "DEACTIVATE") : List.of("EDIT", "ACTIVATE", "DELETE"));
  }
}
