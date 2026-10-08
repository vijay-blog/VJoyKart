package com.nexamart.backend.service;

import com.nexamart.backend.api.ApiModels.*;
import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.*;
import com.nexamart.backend.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delivery-partner (delivery boy) operations. Every order access is restricted to the assigned partner. */
@Service
public class DeliveryService {
  static final BigDecimal DELIVERY_EARNING = new BigDecimal("40.00");
  private static final List<DeliveryStatus> HISTORY = List.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED);

  final OrderRepository orders;
  final UserAccountRepository users;
  final DeliveryPartnerProfileRepository profiles;
  final NotificationRepository notes;
  final EarningRepository earnings;
  final MappingService map;
  final OrderNotifier notifier;
  final VJoyKartProperties props;
  final Clock clock;

  public DeliveryService(OrderRepository o, UserAccountRepository u, DeliveryPartnerProfileRepository p, NotificationRepository n,
      EarningRepository e, MappingService m, OrderNotifier notifier, VJoyKartProperties props, Clock clock) {
    orders = o; users = u; profiles = p; notes = n; earnings = e; map = m; this.notifier = notifier; this.props = props; this.clock = clock;
  }

  private Long me() { return CurrentUser.id(); }

  private UserAccount partner() {
    return users.findById(me()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));
  }

  private DeliveryPartnerProfile myProfile() {
    return profiles.findById(me()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery profile not found."));
  }

  public DeliveryDashboardResponse dashboard() {
    Long id = partner().getId();
    long active = orders.countByDeliveryPartnerIdAndDeliveryStatusIn(id, DeliveryStatus.ACTIVE);
    long assigned = orders.countByDeliveryPartnerIdAndDeliveryStatus(id, DeliveryStatus.DELIVERY_ASSIGNED);
    long packing = orders.countByDeliveryPartnerIdAndDeliveryStatus(id, DeliveryStatus.PACKING);
    long onTheWay = orders.countByDeliveryPartnerIdAndDeliveryStatusIn(id, List.of(DeliveryStatus.ON_THE_WAY, DeliveryStatus.ARRIVED));
    long completed = orders.countByDeliveryPartnerIdAndDeliveryStatus(id, DeliveryStatus.DELIVERED);
    Instant now = clock.instant();
    BigDecimal today = earnings.findByPartnerIdAndEarnedAtBetween(id, now.minus(1, ChronoUnit.DAYS), now).stream()
        .map(Earning::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<DeliveryOrderSummary> recent = orders.findByDeliveryPartnerId(id, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent().stream().map(map::deliverySummary).toList();
    String av = profiles.findById(id).map(x -> x.isAvailable() ? "ONLINE" : "OFFLINE").orElse("UNKNOWN");
    return new DeliveryDashboardResponse(active, assigned, packing, onTheWay, completed, today.toString(), "INR", av, recent);
  }

  /** Active jobs assigned to the signed-in partner (newest first). */
  public PageResponse<OrderResponse> orders(int page, int size, String q) {
    return page(DeliveryStatus.ACTIVE, page, size, q);
  }

  public PageResponse<OrderResponse> history(int page, int size, String q) {
    return page(HISTORY, page, size, q);
  }

  private PageResponse<OrderResponse> page(List<DeliveryStatus> statuses, int page, int size, String q) {
    String query = q == null || q.isBlank() ? null : q.trim();
    Page<Order> pg = orders.partnerOrders(me(), statuses, query, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
        Sort.by(Sort.Direction.DESC, "createdAt")));
    return new PageResponse<>(pg.getContent().stream().map(map::order).toList(), pg.getNumber(), pg.getSize(), pg.getTotalPages(),
        pg.getTotalElements(), pg.hasNext());
  }

  public OrderResponse detail(Long id) {
    Order o = orders.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    requireAssignedTo(o, me());
    return map.order(o);
  }

  public OrderResponse updateStatus(Long id, DeliveryStatusUpdateRequest r) {
    advance(id, me(), DeliveryStatus.parse(r.status()));
    return detail(id);
  }

  static void requireAssignedTo(Order o, Long partnerId) {
    if (o.getDeliveryPartner() == null || partnerId == null || !o.getDeliveryPartner().getId().equals(partnerId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "Order is not assigned to you.");
    }
  }

  /**
   * Moves an order one step along DELIVERY_ASSIGNED -> PACKING -> ON_THE_WAY -> ARRIVED -> DELIVERED.
   * The order row is locked so concurrent taps cannot skip or repeat a step.
   */
  @Transactional
  public Order advance(Long orderId, Long partnerId, DeliveryStatus target) {
    if (target == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown delivery status.");
    Order o = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    requireAssignedTo(o, partnerId);
    DeliveryStatus current = o.getDeliveryStatus();
    if (!current.canTransitionTo(target)) {
      DeliveryStatus next = current.nextPartnerStep();
      throw new ApiException(HttpStatus.CONFLICT, "Cannot change delivery status from " + current.label() + " to " + target.label() + "."
          + (next == null ? "" : " Next allowed step: " + next.label() + "."));
    }
    Instant now = clock.instant();
    o.applyDeliveryStatus(target, now);
    orders.save(o);
    if (target == DeliveryStatus.DELIVERED) {
      profiles.releaseFromOrder(partnerId, orderId);
      Earning e = new Earning();
      e.setPartner(o.getDeliveryPartner());
      e.setOrder(o);
      e.setAmount(DELIVERY_EARNING);
      e.setDescription("Delivery earning for order #" + o.getId());
      earnings.save(e);
    }
    notifier.customerStatus(o, target);
    return o;
  }

  public AvailabilityResponse availability() {
    DeliveryPartnerProfile p = myProfile();
    boolean busy = profiles.findActiveOrderId(p.getUserId()) != null;
    return new AvailabilityResponse(p.isAvailable(), p.isAvailable() ? "ONLINE" : "OFFLINE", !busy, busy ? "Active delivery in progress." : null, p.getUpdatedAt());
  }

  @Transactional
  public AvailabilityResponse updateAvailability(boolean available) {
    DeliveryPartnerProfile p = myProfile();
    if (!available && profiles.findActiveOrderId(p.getUserId()) != null) {
      throw new ApiException(HttpStatus.CONFLICT, "You cannot go offline during an active delivery.");
    }
    p.setAvailable(available);
    profiles.save(p);
    return availability();
  }

  /** Records the partner's current GPS position; used by dispatch together with the freshness window. */
  @Transactional
  public LocationUpdateResponse updateLocation(LocationUpdateRequest r) {
    DeliveryPartnerProfile p = myProfile();
    Instant now = clock.instant();
    p.updateLocation(r.latitude(), r.longitude(), now);
    profiles.save(p);
    UserAccount u = p.getUser();
    u.setLastActiveAt(now);
    users.save(u);
    double km = GeoDistance.haversineKm(props.getStore().getLatitude(), props.getStore().getLongitude(), r.latitude(), r.longitude());
    return new LocationUpdateResponse(true, now, Math.round(km * 100) / 100.0, props.getDispatch().getLocationMaxAgeSeconds());
  }

  public ProfileResponse profile() {
    UserAccount u = partner();
    DeliveryPartnerProfile p = myProfile();
    return new ProfileResponse(u.getId(), u.getName(), u.getPhone(), u.getEmail(), null, p.getVerificationStatus(), u.getStatus().name(),
        p.getVehicleType(), p.getVehicleNumber(), p.getLicenseReference(), u.getCreatedAt(), u.getLastActiveAt(),
        List.of("name", "email", "phone", "vehicleType", "vehicleNumber", "licenseReference"));
  }

  @Transactional
  public ProfileResponse updateProfile(ProfileUpdate r) {
    UserAccount u = partner();
    if (r.name() != null && !r.name().isBlank()) u.setName(r.name().trim());
    if (r.email() != null && !r.email().isBlank()) u.setEmail(r.email().trim().toLowerCase());
    if (r.phone() != null && !r.phone().isBlank()) u.setPhone(r.phone().trim());
    DeliveryPartnerProfile p = myProfile();
    if (r.vehicleType() != null) p.setVehicleType(r.vehicleType());
    if (r.vehicleNumber() != null) p.setVehicleNumber(r.vehicleNumber());
    if (r.licenseReference() != null) p.setLicenseReference(r.licenseReference());
    users.save(u);
    profiles.save(p);
    return profile();
  }

  public DeliveryNotificationPage notifications(int page, int size, boolean unreadOnly) {
    Page<Notification> pg = notes.search(me(), unreadOnly, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
    List<NotificationResponse> list = pg.getContent().stream().map(n -> new NotificationResponse(n.getId().toString(), n.getTitle(), n.getMessage(),
        n.getCreatedAt(), n.isReadFlag(), n.getType() == null ? null : n.getType().name(), n.getOrderId(), n.getActionUrl())).toList();
    return new DeliveryNotificationPage(list, pg.getNumber(), pg.getSize(), pg.getTotalPages(), pg.getTotalElements(), pg.hasNext(),
        notes.countByUserIdAndReadFlagFalse(me()));
  }

  public void markRead(Long id) {
    Notification n = notes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
    if (!n.getUser().getId().equals(me())) throw new ApiException(HttpStatus.FORBIDDEN, "Notification is not yours.");
    n.setReadFlag(true);
    notes.save(n);
  }

  public void markAllRead() {
    notes.findByUserIdAndReadFlagFalse(me()).forEach(n -> { n.setReadFlag(true); notes.save(n); });
  }

  public EarningsSummary earningsSummary() {
    Instant now = clock.instant();
    BigDecimal today = sum(now.minus(1, ChronoUnit.DAYS), now), week = sum(now.minus(7, ChronoUnit.DAYS), now), month = sum(now.minus(30, ChronoUnit.DAYS), now);
    BigDecimal total = earnings.findByPartnerId(me(), Pageable.unpaged()).getContent().stream().map(Earning::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    long completed = orders.countByDeliveryPartnerIdAndDeliveryStatus(me(), DeliveryStatus.DELIVERED);
    return new EarningsSummary("INR", today, week, month, completed, BigDecimal.ZERO, total);
  }

  public PageResponse<EarningResponse> earningHistory(int page, int size) {
    Page<Earning> pg = earnings.findByPartnerId(me(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "earnedAt")));
    return new PageResponse<>(pg.getContent().stream().map(e -> new EarningResponse(e.getId().toString(), e.getOrder().getId(), e.getEarnedAt(),
        e.getAmount(), e.getCurrencyCode(), e.getStatus().name(), e.getDescription())).toList(), pg.getNumber(), pg.getSize(), pg.getTotalPages(),
        pg.getTotalElements(), pg.hasNext());
  }

  private BigDecimal sum(Instant a, Instant b) {
    return earnings.findByPartnerIdAndEarnedAtBetween(me(), a, b).stream().map(Earning::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
