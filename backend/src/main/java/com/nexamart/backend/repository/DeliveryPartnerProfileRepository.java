package com.nexamart.backend.repository;

import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.DeliveryPartnerProfile;
import com.nexamart.backend.domain.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryPartnerProfileRepository extends JpaRepository<DeliveryPartnerProfile, Long> {

  /**
   * Partners that may receive a new delivery: active account with a phone number, online, not
   * blocked, no active delivery, and a fresh location inside the given bounding box. The exact
   * distance is calculated afterwards with the Haversine formula.
   */
  @Query("select p from DeliveryPartnerProfile p join fetch p.user u"
      + " where u.role = :role and u.status = :status"
      + " and u.phone is not null and u.phone <> ''"
      + " and p.available = true and p.activeOrderId is null"
      + " and upper(p.verificationStatus) not in :blockedVerification"
      + " and p.currentLatitude is not null and p.currentLongitude is not null"
      + " and p.locationUpdatedAt is not null and p.locationUpdatedAt >= :freshSince"
      + " and p.currentLatitude between :minLat and :maxLat"
      + " and p.currentLongitude between :minLng and :maxLng")
  List<DeliveryPartnerProfile> findDispatchCandidates(
      @Param("role") Role role,
      @Param("status") AccountStatus status,
      @Param("blockedVerification") Collection<String> blockedVerification,
      @Param("freshSince") Instant freshSince,
      @Param("minLat") double minLat,
      @Param("maxLat") double maxLat,
      @Param("minLng") double minLng,
      @Param("maxLng") double maxLng);

  /**
   * Atomically reserves a partner for an order. Returns 1 only if the partner was still online
   * and free at the database level; a concurrent claim for the same partner blocks on the row
   * lock and then matches 0 rows, so a partner can never hold two active deliveries.
   */
  @Modifying(flushAutomatically = true)
  @Query("update DeliveryPartnerProfile p set p.activeOrderId = :orderId"
      + " where p.userId = :partnerId and p.activeOrderId is null and p.available = true")
  int claimForOrder(@Param("partnerId") Long partnerId, @Param("orderId") Long orderId);

  /** Frees the partner only if they are still holding this exact order. */
  @Modifying(flushAutomatically = true)
  @Query("update DeliveryPartnerProfile p set p.activeOrderId = null"
      + " where p.userId = :partnerId and p.activeOrderId = :orderId")
  int releaseFromOrder(@Param("partnerId") Long partnerId, @Param("orderId") Long orderId);

  @Query("select p.activeOrderId from DeliveryPartnerProfile p where p.userId = :partnerId")
  Long findActiveOrderId(@Param("partnerId") Long partnerId);
}
