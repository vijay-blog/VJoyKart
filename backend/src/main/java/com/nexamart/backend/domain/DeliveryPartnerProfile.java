package com.nexamart.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Delivery-partner profile. {@code @DynamicUpdate} makes entity saves (availability, location,
 * profile edits) write only changed columns so they can never overwrite the dispatch claim.
 * {@code activeOrderId} is read-only for JPA: it is only changed through the atomic
 * conditional updates in {@code DeliveryPartnerProfileRepository}.
 */
@Entity
@Table(name = "delivery_partner_profiles")
@DynamicUpdate
public class DeliveryPartnerProfile {
  @Id private Long userId;
  @OneToOne @MapsId @JoinColumn(name = "user_id") private UserAccount user;
  private String verificationStatus = "PENDING";
  private String vehicleType;
  private String vehicleNumber;
  private String licenseReference;
  private boolean available = false;
  private Instant updatedAt = Instant.now();
  @Column(name = "current_latitude") private Double currentLatitude;
  @Column(name = "current_longitude") private Double currentLongitude;
  @Column(name = "location_updated_at") private Instant locationUpdatedAt;
  @Column(name = "active_order_id", insertable = false, updatable = false) private Long activeOrderId;

  public Long getUserId() { return userId; }
  public UserAccount getUser() { return user; }
  public void setUser(UserAccount v) { user = v; }
  public String getVerificationStatus() { return verificationStatus; }
  public void setVerificationStatus(String v) { verificationStatus = v; }
  public String getVehicleType() { return vehicleType; }
  public void setVehicleType(String v) { vehicleType = v; }
  public String getVehicleNumber() { return vehicleNumber; }
  public void setVehicleNumber(String v) { vehicleNumber = v; }
  public String getLicenseReference() { return licenseReference; }
  public void setLicenseReference(String v) { licenseReference = v; }
  public boolean isAvailable() { return available; }
  public void setAvailable(boolean v) { available = v; updatedAt = Instant.now(); }
  public Instant getUpdatedAt() { return updatedAt; }
  public Double getCurrentLatitude() { return currentLatitude; }
  public Double getCurrentLongitude() { return currentLongitude; }
  public Instant getLocationUpdatedAt() { return locationUpdatedAt; }
  public void updateLocation(double latitude, double longitude, Instant at) {
    currentLatitude = latitude;
    currentLongitude = longitude;
    locationUpdatedAt = at;
  }
  public Long getActiveOrderId() { return activeOrderId; }
  /** Test/in-memory helper only; persistence ignores this field on save. */
  public void setActiveOrderIdForView(Long v) { activeOrderId = v; }
}
