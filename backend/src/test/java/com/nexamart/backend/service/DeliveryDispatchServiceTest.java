package com.nexamart.backend.service;

import static com.nexamart.backend.service.DeliveryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.DeliveryPartnerProfileRepository;
import com.nexamart.backend.repository.OrderRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryDispatchServiceTest {
  @Mock OrderRepository orders;
  @Mock DeliveryPartnerProfileRepository profiles;
  @Mock UserAccountRepository users;
  @Mock OrderNotifier notifier;

  VJoyKartProperties props = props();
  DeliveryDispatchService service;
  UserAccount customer = user(500, Role.CUSTOMER, "Customer One", "9000000001");

  @BeforeEach
  void setUp() {
    service = new DeliveryDispatchService(orders, profiles, users, notifier, props, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void candidates(DeliveryPartnerProfile... rows) {
    when(profiles.findDispatchCandidates(eq(Role.DELIVERY_PARTNER), eq(AccountStatus.ACTIVE), anyCollection(), any(), anyDouble(),
        anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(rows));
  }

  private Order codOrder(long id) {
    Order o = order(id, customer, PaymentMethod.COD, PaymentStatus.PENDING);
    when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(o));
    return o;
  }

  @Test
  void nearestEligiblePartnerIsRankedFirst() {
    var far = partner(1, "Far", 4.0, 10);
    var near = partner(2, "Near", 1.0, 10);
    var mid = partner(3, "Mid", 2.5, 10);
    var ranked = service.rank(List.of(far, near, mid), NOW);
    assertThat(ranked).extracting(DeliveryDispatchService.Candidate::partnerId).containsExactly(2L, 3L, 1L);
    assertThat(ranked.get(0).distanceKm()).isBetween(0.99, 1.01);
  }

  @Test
  void initialRadiusIsPreferredAndFallbackRadiusIsTheHardLimit() {
    var inside = partner(1, "Inside", 4.9, 10);
    var fallback = partner(2, "Fallback", 7.5, 10);
    var outside = partner(3, "Outside", 9.0, 10);
    var ranked = service.rank(List.of(fallback, outside, inside), NOW);
    assertThat(ranked).extracting(DeliveryDispatchService.Candidate::partnerId).containsExactly(1L, 2L);
    assertThat(ranked).extracting(DeliveryDispatchService.Candidate::tier).containsExactly(0, 1);
  }

  @Test
  void unavailableBusyBlockedSuspendedOrPhonelessPartnersAreIgnored() {
    var offline = partner(1, "Offline", 0.5, 10);
    offline.setAvailable(false);
    var busy = partner(2, "Busy", 0.5, 10);
    busy.setActiveOrderIdForView(77L);
    var rejected = partner(3, "Rejected", 0.5, 10);
    rejected.setVerificationStatus("REJECTED");
    var suspended = partner(4, "Suspended", 0.5, 10);
    suspended.getUser().setStatus(AccountStatus.SUSPENDED);
    var noPhone = partner(5, "NoPhone", 0.5, 10);
    noPhone.getUser().setPhone(" ");
    var ok = partner(6, "Ok", 3.0, 10);
    assertThat(service.rank(List.of(offline, busy, rejected, suspended, noPhone, ok), NOW))
        .extracting(DeliveryDispatchService.Candidate::partnerId).containsExactly(6L);
  }

  @Test
  void staleOrMissingLocationIsIgnored() {
    var stale = partner(1, "Stale", 0.2, props.getDispatch().getLocationMaxAgeSeconds() + 1);
    var noLocation = new DeliveryPartnerProfile();
    org.springframework.test.util.ReflectionTestUtils.setField(noLocation, "userId", 2L);
    noLocation.setUser(user(2, Role.DELIVERY_PARTNER, "NoLoc", "9876543202"));
    noLocation.setAvailable(true);
    var fresh = partner(3, "Fresh", 4.0, props.getDispatch().getLocationMaxAgeSeconds());
    assertThat(service.rank(List.of(stale, noLocation, fresh), NOW))
        .extracting(DeliveryDispatchService.Candidate::partnerId).containsExactly(3L);
  }

  @Test
  void codOrderIsAutomaticallyAssignedToTheNearestPartnerAndPartnerIsNotified() {
    Order o = codOrder(10);
    var near = partner(2, "Ravi", 1.0, 30);
    candidates(partner(1, "Far", 3.0, 30), near);
    when(profiles.claimForOrder(2L, 10L)).thenReturn(1);

    var result = service.dispatch(10L);

    assertThat(result.outcome()).isEqualTo(DeliveryDispatchService.Outcome.ASSIGNED);
    assertThat(result.partnerId()).isEqualTo(2L);
    assertThat(o.getDeliveryPartner()).isSameAs(near.getUser());
    assertThat(o.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_ASSIGNED);
    assertThat(o.getAssignedAt()).isEqualTo(NOW);
    assertThat(o.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(orders).save(o);
    verify(notifier).deliveryAssigned(o, "VJoyKart Store");
    verify(profiles, never()).claimForOrder(eq(1L), anyLong());
  }

  @Test
  void partnerTakenConcurrentlyFallsThroughToTheNextCandidate() {
    Order o = codOrder(11);
    candidates(partner(1, "First", 1.0, 30), partner(2, "Second", 2.0, 30));
    when(profiles.claimForOrder(1L, 11L)).thenReturn(0);
    when(profiles.claimForOrder(2L, 11L)).thenReturn(1);

    var result = service.dispatch(11L);

    assertThat(result.partnerId()).isEqualTo(2L);
    assertThat(o.getDeliveryPartner().getId()).isEqualTo(2L);
  }

  @Test
  void alreadyAssignedOrderIsNeverDispatchedTwice() {
    Order o = codOrder(12);
    o.setDeliveryPartner(user(9, Role.DELIVERY_PARTNER, "Existing", "9876543209"));
    o.applyDeliveryStatus(DeliveryStatus.DELIVERY_ASSIGNED, NOW);

    var result = service.dispatch(12L);

    assertThat(result.outcome()).isEqualTo(DeliveryDispatchService.Outcome.ALREADY_ASSIGNED);
    assertThat(result.partnerId()).isEqualTo(9L);
    verify(profiles, never()).claimForOrder(anyLong(), anyLong());
    verify(notifier, never()).deliveryAssigned(any(), any());
  }

  @Test
  void unpaidOnlineOrderWaitsForPayment() {
    Order o = order(13, customer, PaymentMethod.ONLINE, PaymentStatus.PENDING);
    when(orders.findByIdForUpdate(13L)).thenReturn(Optional.of(o));

    assertThat(service.dispatch(13L).outcome()).isEqualTo(DeliveryDispatchService.Outcome.NOT_READY);
    assertThat(o.getDeliveryPartner()).isNull();
    verify(profiles, never()).claimForOrder(anyLong(), anyLong());
  }

  @Test
  void noPartnerAvailableKeepsOrderWaitingAndAlertsAdminsOnlyOnce() {
    Order o = codOrder(14);
    candidates();

    var first = service.dispatch(14L);
    var second = service.dispatch(14L);

    assertThat(first.outcome()).isEqualTo(DeliveryDispatchService.Outcome.NO_PARTNER_AVAILABLE);
    assertThat(second.outcome()).isEqualTo(DeliveryDispatchService.Outcome.NO_PARTNER_AVAILABLE);
    assertThat(o.getDeliveryPartner()).isNull();
    assertThat(o.getDeliveryStatus()).isEqualTo(DeliveryStatus.ORDER_PLACED);
    assertThat(o.getAssignedAt()).isNull();
    assertThat(MappingService.assignmentStatus(o)).isEqualTo("AWAITING_ASSIGNMENT");
    assertThat(o.getDispatchLastAttemptAt()).isEqualTo(NOW);
    verify(notifier, times(1)).admins(eq(o), eq("No delivery partner available"), anyString());
    verify(notifier, never()).deliveryAssigned(any(), any());
  }

  @Test
  void adminOverrideReleasesPreviousPartnerAndClaimsNewOne() {
    Order o = codOrder(15);
    UserAccount old = user(1, Role.DELIVERY_PARTNER, "Old", "9876543201");
    UserAccount fresh = user(2, Role.DELIVERY_PARTNER, "New", "9876543202");
    o.setDeliveryPartner(old);
    o.applyDeliveryStatus(DeliveryStatus.DELIVERY_ASSIGNED, NOW);
    when(users.findById(2L)).thenReturn(Optional.of(fresh));
    when(profiles.claimForOrder(2L, 15L)).thenReturn(1);

    service.adminAssign(15L, 2L);

    verify(profiles).releaseFromOrder(1L, 15L);
    assertThat(o.getDeliveryPartner()).isSameAs(fresh);
    verify(notifier).deliveryAssigned(o, "VJoyKart Store");
  }

  @Test
  void adminOverrideRejectsBusyPartner() {
    codOrder(16);
    when(users.findById(2L)).thenReturn(Optional.of(user(2, Role.DELIVERY_PARTNER, "Busy", "9876543202")));
    when(profiles.claimForOrder(2L, 16L)).thenReturn(0);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.adminAssign(16L, 2L))
        .isInstanceOf(com.nexamart.backend.exception.ApiException.class);
  }
}
