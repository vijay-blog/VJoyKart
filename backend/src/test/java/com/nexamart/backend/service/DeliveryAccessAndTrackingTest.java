package com.nexamart.backend.service;

import static com.nexamart.backend.service.DeliveryFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nexamart.backend.api.ApiModels.DeliveryStepDto;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.*;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Ownership rules, transition enforcement and the customer tracking DTO. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryAccessAndTrackingTest {
  @Mock OrderRepository orders;
  @Mock UserAccountRepository users;
  @Mock DeliveryPartnerProfileRepository profiles;
  @Mock NotificationRepository notes;
  @Mock EarningRepository earnings;
  @Mock OrderItemRepository items;
  @Mock ProductRepository products;
  @Mock CategoryRepository categories;
  @Mock AddressRepository addresses;
  @Mock OrderNotifier notifier;

  MappingService mapper;
  DeliveryService delivery;
  CustomerService customers;

  UserAccount alice = user(500, Role.CUSTOMER, "Alice", "9000000500");
  UserAccount bob = user(501, Role.CUSTOMER, "Bob", "9000000501");
  UserAccount ravi = user(2, Role.DELIVERY_PARTNER, "Ravi Kumar", "9876543202");
  UserAccount suresh = user(3, Role.DELIVERY_PARTNER, "Suresh", "9876543203");

  @BeforeEach
  void setUp() {
    mapper = new MappingService(items, props());
    delivery = new DeliveryService(orders, users, profiles, notes, earnings, mapper, notifier, props(), Clock.fixed(NOW, ZoneOffset.UTC));
    customers = new CustomerService(users, products, categories, orders, items, addresses, notes, mapper);
    when(items.findByOrderId(any())).thenReturn(List.of());
    when(users.findById(500L)).thenReturn(Optional.of(alice));
    when(users.findById(501L)).thenReturn(Optional.of(bob));
  }

  @AfterEach
  void clear() { SecurityContextHolder.clearContext(); }

  private void login(UserAccount u) {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getId().toString(), null,
        List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()))));
  }

  private Order assignedOrder(long id, UserAccount customer, UserAccount partner) {
    Order o = order(id, customer, PaymentMethod.COD, PaymentStatus.PENDING);
    o.setDeliveryPartner(partner);
    o.applyDeliveryStatus(DeliveryStatus.DELIVERY_ASSIGNED, NOW.minusSeconds(60));
    when(orders.findById(id)).thenReturn(Optional.of(o));
    when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(o));
    return o;
  }

  @Test
  void deliveryBoyCannotUpdateAnotherDeliveryBoysOrder() {
    Order o = assignedOrder(20, alice, ravi);
    assertThatThrownBy(() -> delivery.advance(20L, suresh.getId(), DeliveryStatus.PACKING))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThat(o.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_ASSIGNED);
    verify(orders, never()).save(any());
  }

  @Test
  void deliveryBoyCannotReadAnotherDeliveryBoysOrder() {
    assignedOrder(21, alice, ravi);
    login(suresh);
    assertThatThrownBy(() -> delivery.detail(21L))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void stepsCannotBeSkipped() {
    Order o = assignedOrder(22, alice, ravi);
    assertThatThrownBy(() -> delivery.advance(22L, ravi.getId(), DeliveryStatus.DELIVERED))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    assertThatThrownBy(() -> delivery.advance(22L, ravi.getId(), DeliveryStatus.ON_THE_WAY))
        .isInstanceOf(ApiException.class);
    assertThat(o.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_ASSIGNED);
  }

  @Test
  void assignedDeliveryBoyWalksTheFullFlowAndIsReleasedOnDelivery() {
    Order o = assignedOrder(23, alice, ravi);
    delivery.advance(23L, ravi.getId(), DeliveryStatus.PACKING);
    delivery.advance(23L, ravi.getId(), DeliveryStatus.ON_THE_WAY);
    delivery.advance(23L, ravi.getId(), DeliveryStatus.ARRIVED);
    verify(profiles, never()).releaseFromOrder(any(), any());
    delivery.advance(23L, ravi.getId(), DeliveryStatus.DELIVERED);

    assertThat(o.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    assertThat(o.getPackingAt()).isNotNull();
    assertThat(o.getOnTheWayAt()).isNotNull();
    assertThat(o.getArrivedAt()).isNotNull();
    assertThat(o.getDeliveredAt()).isNotNull();
    assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(profiles).releaseFromOrder(2L, 23L);
    verify(earnings).save(any(Earning.class));
    verify(notifier, times(4)).customerStatus(any(), any());
    assertThatThrownBy(() -> delivery.advance(23L, ravi.getId(), DeliveryStatus.DELIVERED)).isInstanceOf(ApiException.class);
  }

  @Test
  void partnerSeesOnlyTheValidNextAction() {
    Order o = assignedOrder(24, alice, ravi);
    login(ravi);
    assertThat(mapper.order(o).allowedActions()).containsExactly("PACKING");
    o.applyDeliveryStatus(DeliveryStatus.PACKING, NOW);
    assertThat(mapper.order(o).allowedActions()).containsExactly("ON_THE_WAY");
    o.applyDeliveryStatus(DeliveryStatus.ON_THE_WAY, NOW);
    assertThat(mapper.order(o).allowedActions()).containsExactly("ARRIVED");
    o.applyDeliveryStatus(DeliveryStatus.ARRIVED, NOW);
    assertThat(mapper.order(o).allowedActions()).containsExactly("DELIVERED");
    o.applyDeliveryStatus(DeliveryStatus.DELIVERED, NOW);
    assertThat(mapper.order(o).allowedActions()).isEmpty();
  }

  @Test
  void customerCannotSeeAnotherCustomersDeliveryPartner() {
    assignedOrder(25, alice, ravi);
    login(bob);
    assertThatThrownBy(() -> customers.tracking(25L))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> customers.detail(25L))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void customerTrackingContainsDeliveryBoyNameAndPhoneStoreAndFiveSteps() {
    assignedOrder(26, alice, ravi);
    login(alice);

    var t = customers.tracking(26L);

    assertThat(t.deliveryStatus()).isEqualTo("DELIVERY_ASSIGNED");
    assertThat(t.assignmentStatus()).isEqualTo("ASSIGNED");
    assertThat(t.assignedAt()).isNotNull();
    assertThat(t.deliveryPartner().id()).isEqualTo("2");
    assertThat(t.deliveryPartner().name()).isEqualTo("Ravi Kumar");
    assertThat(t.deliveryPartner().phone()).isEqualTo("9876543202");
    assertThat(t.store().name()).isEqualTo("VJoyKart Store");
    assertThat(t.store().latitude()).isEqualTo(17.3899091);
    assertThat(t.store().longitude()).isEqualTo(78.383089);
    assertThat(t.store().mapsUrl()).isEqualTo("https://maps.app.goo.gl/2j5t44Xc4DJJSvjR9");
    assertThat(t.deliveryLocation().latitude()).isEqualTo(17.40);
    assertThat(t.paymentStatus()).isEqualTo("PENDING");
    assertThat(t.progress()).extracting(DeliveryStepDto::status)
        .containsExactly("DELIVERY_ASSIGNED", "PACKING", "ON_THE_WAY", "ARRIVED", "DELIVERED");
    assertThat(t.progress()).extracting(DeliveryStepDto::state)
        .containsExactly("CURRENT", "PENDING", "PENDING", "PENDING", "PENDING");
  }

  @Test
  void deliveryPartnerContactExposesOnlyIdNameAndPhone() {
    assertThat(com.nexamart.backend.api.ApiModels.DeliveryPartnerContact.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName).containsExactly("id", "name", "phone");
  }

  @Test
  void progressMarksCompletedCurrentAndPendingSteps() {
    Order o = assignedOrder(27, alice, ravi);
    o.applyDeliveryStatus(DeliveryStatus.PACKING, NOW);
    o.applyDeliveryStatus(DeliveryStatus.ON_THE_WAY, NOW);
    assertThat(MappingService.deliveryProgress(o)).extracting(DeliveryStepDto::state)
        .containsExactly("COMPLETED", "COMPLETED", "CURRENT", "PENDING", "PENDING");
    o.applyDeliveryStatus(DeliveryStatus.ARRIVED, NOW);
    o.applyDeliveryStatus(DeliveryStatus.DELIVERED, NOW);
    assertThat(MappingService.deliveryProgress(o)).extracting(DeliveryStepDto::state)
        .containsOnly("COMPLETED");
  }

  @Test
  void unassignedOrderShowsNoDeliveryBoyAndNoCompletedStep() {
    Order o = order(28, alice, PaymentMethod.COD, PaymentStatus.PENDING);
    when(orders.findById(28L)).thenReturn(Optional.of(o));
    login(alice);
    var t = customers.tracking(28L);
    assertThat(t.deliveryPartner()).isNull();
    assertThat(t.assignmentStatus()).isEqualTo("AWAITING_ASSIGNMENT");
    assertThat(t.progress()).extracting(DeliveryStepDto::state).containsOnly("PENDING");
  }
}
