package com.nexamart.backend.service;

import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.DeliveryPartnerProfileRepository;
import com.nexamart.backend.repository.OrderRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatic assignment of the nearest free delivery partner to orders picked up at the fixed
 * VJoyKart Store.
 *
 * <p>Concurrency: the order row is locked with {@code SELECT ... FOR UPDATE} so the same order
 * is never dispatched twice, and each partner is reserved with an atomic conditional UPDATE
 * ({@code active_order_id IS NULL}) so two orders can never take the same partner. A unique
 * index on {@code active_order_id} is the final database-level guard.
 */
@Service
public class DeliveryDispatchService {
  private static final Logger log = LoggerFactory.getLogger(DeliveryDispatchService.class);
  static final Set<String> BLOCKED_VERIFICATION = Set.of("REJECTED", "SUSPENDED", "BLOCKED");

  public enum Outcome { ASSIGNED, ALREADY_ASSIGNED, NOT_READY, NO_PARTNER_AVAILABLE }

  public record Candidate(DeliveryPartnerProfile profile, double distanceKm, int tier) {
    public Long partnerId() { return profile.getUserId(); }
  }

  public record DispatchResult(Outcome outcome, Long partnerId, Double distanceKm) {
    static DispatchResult of(Outcome o) { return new DispatchResult(o, null, null); }
  }

  private final OrderRepository orders;
  private final DeliveryPartnerProfileRepository profiles;
  private final UserAccountRepository users;
  private final OrderNotifier notifier;
  private final VJoyKartProperties props;
  private final Clock clock;

  public DeliveryDispatchService(OrderRepository orders, DeliveryPartnerProfileRepository profiles, UserAccountRepository users,
      OrderNotifier notifier, VJoyKartProperties props, Clock clock) {
    this.orders = orders;
    this.profiles = profiles;
    this.users = users;
    this.notifier = notifier;
    this.props = props;
    this.clock = clock;
  }

  /** Online orders are dispatched only once paid; COD orders immediately. */
  public static boolean readyForDispatch(Order o) {
    return o.getDeliveryStatus() == DeliveryStatus.ORDER_PLACED
        && o.getDeliveryPartner() == null
        && o.getStatus() != OrderStatus.CANCELLED
        && (o.getPaymentMethod() == PaymentMethod.COD || o.getPaymentStatus() == PaymentStatus.PAID);
  }

  public double distanceFromStoreKm(double latitude, double longitude) {
    return GeoDistance.haversineKm(props.getStore().getLatitude(), props.getStore().getLongitude(), latitude, longitude);
  }

  /** Free, online, fresh-location partners within the configured radius, nearest first (initial radius tier before fallback tier). */
  public List<Candidate> findCandidates(Instant now) {
    var store = props.getStore();
    double[] box = GeoDistance.boundingBox(store.getLatitude(), store.getLongitude(), props.getDispatch().getFallbackRadiusKm());
    Instant freshSince = now.minusSeconds(props.getDispatch().getLocationMaxAgeSeconds());
    List<DeliveryPartnerProfile> rows = profiles.findDispatchCandidates(Role.DELIVERY_PARTNER, AccountStatus.ACTIVE,
        BLOCKED_VERIFICATION, freshSince, box[0], box[1], box[2], box[3]);
    return rank(rows, now);
  }

  /** Re-applies every eligibility rule in Java (defence in depth) and orders by radius tier then distance. */
  List<Candidate> rank(List<DeliveryPartnerProfile> rows, Instant now) {
    var dispatch = props.getDispatch();
    Instant freshSince = now.minusSeconds(dispatch.getLocationMaxAgeSeconds());
    return rows.stream()
        .filter(p -> isEligible(p, freshSince))
        .map(p -> {
          double km = distanceFromStoreKm(p.getCurrentLatitude(), p.getCurrentLongitude());
          return new Candidate(p, km, km <= dispatch.getInitialRadiusKm() ? 0 : 1);
        })
        .filter(c -> c.distanceKm() <= dispatch.getFallbackRadiusKm())
        .sorted(Comparator.comparingInt(Candidate::tier).thenComparingDouble(Candidate::distanceKm).thenComparing(Candidate::partnerId))
        .toList();
  }

  static boolean isEligible(DeliveryPartnerProfile p, Instant freshSince) {
    UserAccount u = p.getUser();
    return u != null
        && u.getRole() == Role.DELIVERY_PARTNER
        && u.getStatus() == AccountStatus.ACTIVE
        && u.getPhone() != null && !u.getPhone().isBlank()
        && p.isAvailable()
        && p.getActiveOrderId() == null
        && (p.getVerificationStatus() == null || !BLOCKED_VERIFICATION.contains(p.getVerificationStatus().toUpperCase()))
        && p.getCurrentLatitude() != null && p.getCurrentLongitude() != null
        && p.getLocationUpdatedAt() != null && !p.getLocationUpdatedAt().isBefore(freshSince);
  }

  @Transactional
  public DispatchResult dispatch(Long orderId) {
    Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    if (order.getDeliveryPartner() != null || order.getDeliveryStatus() != DeliveryStatus.ORDER_PLACED) {
      return new DispatchResult(Outcome.ALREADY_ASSIGNED, order.getDeliveryPartner() == null ? null : order.getDeliveryPartner().getId(), null);
    }
    if (!readyForDispatch(order)) return DispatchResult.of(Outcome.NOT_READY);

    Instant now = clock.instant();
    for (Candidate c : findCandidates(now)) {
      if (profiles.claimForOrder(c.partnerId(), orderId) == 1) {
        order.setDeliveryPartner(c.profile().getUser());
        order.applyDeliveryStatus(DeliveryStatus.DELIVERY_ASSIGNED, now);
        orders.save(order);
        notifier.deliveryAssigned(order, props.getStore().getName());
        log.info("Order {} auto-assigned to delivery partner {} ({} km from store)", orderId, c.partnerId(), String.format("%.2f", c.distanceKm()));
        return new DispatchResult(Outcome.ASSIGNED, c.partnerId(), c.distanceKm());
      }
      log.debug("Delivery partner {} was taken concurrently; trying next candidate for order {}", c.partnerId(), orderId);
    }

    boolean firstMiss = order.getDispatchLastAttemptAt() == null;
    order.setDispatchLastAttemptAt(now);
    orders.save(order);
    if (firstMiss) {
      notifier.admins(order, "No delivery partner available",
          "Order #" + orderId + " is waiting for a delivery partner near " + props.getStore().getName()
              + ". It will be assigned automatically when a partner comes online.");
    }
    log.warn("No delivery partner available within {} km of {} for order {}", props.getDispatch().getFallbackRadiusKm(), props.getStore().getName(), orderId);
    return DispatchResult.of(Outcome.NO_PARTNER_AVAILABLE);
  }

  /** Admin override: assign or reassign a specific partner using the same reservation rules. */
  @Transactional
  public void adminAssign(Long orderId, Long partnerId) {
    Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    if (order.getDeliveryStatus().isTerminal()) throw new ApiException(HttpStatus.CONFLICT, "Order is already " + order.getDeliveryStatus().label().toLowerCase() + ".");
    UserAccount partner = users.findById(partnerId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Delivery partner not found."));
    if (partner.getRole() != Role.DELIVERY_PARTNER || partner.getStatus() != AccountStatus.ACTIVE) {
      throw new ApiException(HttpStatus.CONFLICT, "Delivery partner is not eligible.");
    }
    UserAccount previous = order.getDeliveryPartner();
    if (previous != null && previous.getId().equals(partnerId)) return;
    if (previous != null) profiles.releaseFromOrder(previous.getId(), orderId);
    if (profiles.claimForOrder(partnerId, orderId) != 1) {
      throw new ApiException(HttpStatus.CONFLICT, "Delivery partner is offline or already on an active delivery.");
    }
    order.setDeliveryPartner(partner);
    if (order.getDeliveryStatus() == DeliveryStatus.ORDER_PLACED) order.applyDeliveryStatus(DeliveryStatus.DELIVERY_ASSIGNED, clock.instant());
    orders.save(order);
    notifier.deliveryAssigned(order, props.getStore().getName());
  }

  @Transactional
  public void cancel(Long orderId, String reason) {
    Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found."));
    if (order.getDeliveryStatus().isTerminal() || order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
      throw new ApiException(HttpStatus.CONFLICT, "Order cannot be cancelled.");
    }
    if (order.getDeliveryPartner() != null) profiles.releaseFromOrder(order.getDeliveryPartner().getId(), orderId);
    order.applyDeliveryStatus(DeliveryStatus.CANCELLED, clock.instant());
    order.setCancellationReason(reason);
    orders.save(order);
    notifier.customerStatus(order, DeliveryStatus.CANCELLED);
  }

  public List<Long> ordersAwaitingDispatch() {
    Instant since = clock.instant().minusSeconds(props.getDispatch().getPendingOrderMaxAgeMinutes() * 60);
    return orders.findIdsAwaitingDispatch(DeliveryStatus.ORDER_PLACED, since, PaymentMethod.COD, PaymentStatus.PAID);
  }
}
