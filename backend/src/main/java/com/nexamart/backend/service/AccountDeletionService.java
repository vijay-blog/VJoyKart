package com.nexamart.backend.service;

import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.AddressRepository;
import com.nexamart.backend.repository.NotificationRepository;
import com.nexamart.backend.repository.OtpChallengeRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionService {
  private final UserAccountRepository users;
  private final AddressRepository addresses;
  private final NotificationRepository notifications;
  private final OtpChallengeRepository otpChallenges;
  private final PasswordEncoder passwordEncoder;

  public AccountDeletionService(UserAccountRepository users, AddressRepository addresses,
      NotificationRepository notifications, OtpChallengeRepository otpChallenges,
      PasswordEncoder passwordEncoder) {
    this.users = users;
    this.addresses = addresses;
    this.notifications = notifications;
    this.otpChallenges = otpChallenges;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void deleteCustomerAccount(Long userId) {
    if (userId == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired."));
    if (user.getRole() != Role.CUSTOMER) throw new ApiException(HttpStatus.FORBIDDEN, "Customer access required.");
    if (user.getStatus() == AccountStatus.DELETED) return;
    if (user.getStatus() != AccountStatus.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active.");

    String phone = user.getPhone();
    notifications.deleteByUserId(userId);
    addresses.anonymizeByCustomerId(userId);
    if (phone != null && !phone.isBlank()) otpChallenges.deleteByPhone(phone);

    user.setName("Deleted VJoyKart Customer");
    user.setUsername("deleted_customer_" + userId);
    user.setEmail("deleted.customer." + userId + "@deleted.vjoykart.invalid");
    user.setPhone(null);
    user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
    user.setLastActiveAt(null);
    user.setStatus(AccountStatus.DELETED);
    user.setDeletedAt(Instant.now());
    users.save(user);
  }
}
