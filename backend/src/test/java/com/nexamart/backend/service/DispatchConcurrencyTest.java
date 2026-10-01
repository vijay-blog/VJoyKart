package com.nexamart.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexamart.backend.config.DeliveryConfig;
import com.nexamart.backend.domain.*;
import com.nexamart.backend.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the real JPQL candidate query, atomic claim and pessimistic order lock against an
 * in-memory database with concurrent threads.
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:dispatch_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DeliveryConfig.class, DeliveryDispatchService.class, OrderNotifier.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DispatchConcurrencyTest {
  @Autowired DeliveryDispatchService dispatch;
  @Autowired OrderRepository orders;
  @Autowired UserAccountRepository users;
  @Autowired DeliveryPartnerProfileRepository profiles;
  @Autowired NotificationRepository notifications;
  @Autowired AddressRepository addresses;
  @Autowired TransactionTemplate tx;

  @AfterEach
  void cleanUp() {
    notifications.deleteAll();
    orders.deleteAll();
    addresses.deleteAll();
    profiles.deleteAll();
    users.deleteAll();
  }

  private UserAccount saveUser(String name, Role role, String phone) {
    UserAccount u = new UserAccount();
    u.setName(name);
    u.setEmail(name.toLowerCase() + System.nanoTime() + "@example.com");
    u.setPhone(phone);
    u.setPasswordHash("hash");
    u.setRole(role);
    u.setStatus(AccountStatus.ACTIVE);
    return users.save(u);
  }

  private UserAccount savePartner(String name, double kmNorth, long ageSeconds, boolean available) {
    UserAccount u = saveUser(name, Role.DELIVERY_PARTNER, "98765" + (10000 + Math.abs(name.hashCode() % 89999)));
    tx.executeWithoutResult(s -> {
      DeliveryPartnerProfile p = new DeliveryPartnerProfile();
      p.setUser(users.findById(u.getId()).orElseThrow());
      p.setVerificationStatus("VERIFIED");
      p.setAvailable(available);
      p.updateLocation(DeliveryFixtures.STORE_LAT + kmNorth / 111.195, DeliveryFixtures.STORE_LNG, Instant.now().minusSeconds(ageSeconds));
      profiles.save(p);
    });
    return u;
  }

  private Long saveCodOrder(UserAccount customer) {
    Address a = new Address();
    a.setCustomer(customer);
    a.setAddressLine("Customer street");
    a = addresses.save(a);
    Order o = new Order();
    o.setCustomer(customer);
    o.setDeliveryAddress(a);
    o.setPaymentMethod(PaymentMethod.COD);
    o.setPaymentStatus(PaymentStatus.PENDING);
    o.setSubtotal(new BigDecimal("180.00"));
    o.setDeliveryFee(new BigDecimal("30.00"));
    o.setTotal(new BigDecimal("210.00"));
    return orders.save(o).getId();
  }

  private List<DeliveryDispatchService.DispatchResult> dispatchConcurrently(List<Long> orderIds) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(orderIds.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<DeliveryDispatchService.DispatchResult>> futures = new ArrayList<>();
      for (Long id : orderIds) {
        futures.add(pool.submit(() -> { start.await(); return dispatch.dispatch(id); }));
      }
      start.countDown();
      List<DeliveryDispatchService.DispatchResult> results = new ArrayList<>();
      for (var f : futures) results.add(f.get(30, TimeUnit.SECONDS));
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void candidateQueryReturnsOnlyFreshAvailablePartnersInsideTheRadius() {
    UserAccount near = savePartner("Near", 1.0, 10, true);
    savePartner("Offline", 0.5, 10, false);
    savePartner("Stale", 0.5, 3600, true);
    savePartner("TooFar", 20.0, 10, true);
    UserAccount noPhone = savePartner("NoPhone", 0.5, 10, true);
    noPhone.setPhone(null);
    users.save(noPhone);

    var candidates = dispatch.findCandidates(Instant.now());

    assertThat(candidates).extracting(DeliveryDispatchService.Candidate::partnerId).containsExactly(near.getId());
  }

  @Test
  void twoSimultaneousOrdersNeverGetTheSameDeliveryBoy() throws Exception {
    UserAccount only = savePartner("Only", 1.0, 10, true);
    UserAccount customer = saveUser("Customer", Role.CUSTOMER, "9000000001");
    Long a = saveCodOrder(customer);
    Long b = saveCodOrder(customer);

    var results = dispatchConcurrently(List.of(a, b));

    assertThat(results).extracting(DeliveryDispatchService.DispatchResult::outcome)
        .containsExactlyInAnyOrder(DeliveryDispatchService.Outcome.ASSIGNED, DeliveryDispatchService.Outcome.NO_PARTNER_AVAILABLE);
    long assignedToOnly = orders.findAll().stream()
        .filter(o -> o.getDeliveryPartner() != null && o.getDeliveryPartner().getId().equals(only.getId())).count();
    assertThat(assignedToOnly).isEqualTo(1);
    Long held = profiles.findActiveOrderId(only.getId());
    assertThat(held).isIn(a, b);
    assertThat(orders.findById(held).orElseThrow().getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERY_ASSIGNED);
  }

  @Test
  void manySimultaneousOrdersGetDistinctDeliveryBoys() throws Exception {
    List<Long> partnerIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) partnerIds.add(savePartner("P" + i, 0.5 + i, 10, true).getId());
    UserAccount customer = saveUser("Customer", Role.CUSTOMER, "9000000002");
    List<Long> orderIds = new ArrayList<>();
    for (int i = 0; i < 5; i++) orderIds.add(saveCodOrder(customer));

    var results = dispatchConcurrently(orderIds);

    var assigned = results.stream().filter(r -> r.outcome() == DeliveryDispatchService.Outcome.ASSIGNED).toList();
    assertThat(assigned).hasSize(3);
    assertThat(assigned).extracting(DeliveryDispatchService.DispatchResult::partnerId).doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(partnerIds);
    assertThat(results.stream().filter(r -> r.outcome() == DeliveryDispatchService.Outcome.NO_PARTNER_AVAILABLE)).hasSize(2);
  }

  @Test
  void sameOrderDispatchedTwiceConcurrentlyIsAssignedOnce() throws Exception {
    savePartner("A", 1.0, 10, true);
    savePartner("B", 2.0, 10, true);
    UserAccount customer = saveUser("Customer", Role.CUSTOMER, "9000000003");
    Long id = saveCodOrder(customer);

    var results = dispatchConcurrently(List.of(id, id));

    assertThat(results).extracting(DeliveryDispatchService.DispatchResult::outcome)
        .containsExactlyInAnyOrder(DeliveryDispatchService.Outcome.ASSIGNED, DeliveryDispatchService.Outcome.ALREADY_ASSIGNED);
    long busyPartners = profiles.findAll().stream().filter(p -> profiles.findActiveOrderId(p.getUserId()) != null).count();
    assertThat(busyPartners).isEqualTo(1);
  }

  private int claim(java.util.function.Supplier<Integer> op) {
    Integer n = tx.execute(s -> op.get());
    return n == null ? -1 : n;
  }

  @Test
  void releasedPartnerCanBeClaimedAgainButNotWhileBusy() {
    UserAccount p = savePartner("Solo", 1.0, 10, true);
    UserAccount customer = saveUser("Customer", Role.CUSTOMER, "9000000004");
    Long first = saveCodOrder(customer);
    Long second = saveCodOrder(customer);

    assertThat(claim(() -> profiles.claimForOrder(p.getId(), first))).isEqualTo(1);
    assertThat(claim(() -> profiles.claimForOrder(p.getId(), second))).isZero();
    assertThat(claim(() -> profiles.releaseFromOrder(p.getId(), second))).isZero();
    assertThat(claim(() -> profiles.releaseFromOrder(p.getId(), first))).isEqualTo(1);
    assertThat(claim(() -> profiles.claimForOrder(p.getId(), second))).isEqualTo(1);
  }
}
