package com.nexamart.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(length = 255) private String email;
  @Column(length = 40) private String phone;
  @Column(nullable = false, length = 30) private String status = "PENDING_VERIFICATION";
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  public void setEmail(String value) { email = value; }
  public void setPhone(String value) { phone = value; }
}
