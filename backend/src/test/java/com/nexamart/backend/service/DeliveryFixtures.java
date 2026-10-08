package com.nexamart.backend.service;

import com.nexamart.backend.config.VJoyKartProperties;
import com.nexamart.backend.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for delivery tests. Store: VJoyKart Store at 17.3899091, 78.383089. */
final class DeliveryFixtures {
  static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  static final double STORE_LAT = 17.3899091;
  static final double STORE_LNG = 78.383089;

  private DeliveryFixtures() {}

  static UserAccount user(long id, Role role, String name, String phone) {
    UserAccount u = new UserAccount();
    ReflectionTestUtils.setField(u, "id", id);
    u.setName(name);
    u.setPhone(phone);
    u.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
    u.setPasswordHash("hash");
    u.setRole(role);
    u.setStatus(AccountStatus.ACTIVE);
    return u;
  }

  /** An online, free, verified partner located {@code kmNorth} km north of the store, seen {@code ageSeconds} ago. */
  static DeliveryPartnerProfile partner(long id, String name, double kmNorth, long ageSeconds) {
    DeliveryPartnerProfile p = new DeliveryPartnerProfile();
    UserAccount u = user(id, Role.DELIVERY_PARTNER, name, "98765432" + String.format("%02d", id % 100));
    ReflectionTestUtils.setField(p, "userId", id);
    p.setUser(u);
    p.setVerificationStatus("VERIFIED");
    p.setAvailable(true);
    p.updateLocation(STORE_LAT + kmNorth / 111.195, STORE_LNG, NOW.minusSeconds(ageSeconds));
    return p;
  }

  static Order order(long id, UserAccount customer, PaymentMethod method, PaymentStatus paymentStatus) {
    Order o = new Order();
    ReflectionTestUtils.setField(o, "id", id);
    o.setCustomer(customer);
    o.setPaymentMethod(method);
    o.setPaymentStatus(paymentStatus);
    o.setSubtotal(new BigDecimal("180.00"));
    o.setDeliveryFee(new BigDecimal("30.00"));
    o.setTotal(new BigDecimal("210.00"));
    Address a = new Address();
    a.setCustomer(customer);
    a.setAddressLine("Flat 4, Customer Street");
    a.setCity("Hyderabad");
    a.setLatitude(17.40);
    a.setLongitude(78.40);
    o.setDeliveryAddress(a);
    return o;
  }

  static VJoyKartProperties props() {
    return new VJoyKartProperties();
  }
}
