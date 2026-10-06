package com.nexamart.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.nexamart.backend.domain.AccountStatus;
import com.nexamart.backend.domain.Role;
import com.nexamart.backend.domain.UserAccount;
import com.nexamart.backend.exception.ApiException;
import com.nexamart.backend.repository.AddressRepository;
import com.nexamart.backend.repository.NotificationRepository;
import com.nexamart.backend.repository.OtpChallengeRepository;
import com.nexamart.backend.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {
  @Mock UserAccountRepository users;
  @Mock AddressRepository addresses;
  @Mock NotificationRepository notifications;
  @Mock OtpChallengeRepository otpChallenges;
  @Mock PasswordEncoder passwords;
  private AccountDeletionService service;

  @BeforeEach
  void setUp() {
    service = new AccountDeletionService(users, addresses, notifications, otpChallenges, passwords);
    when(passwords.encode(anyString())).thenReturn("replacement-hash");
  }

  @Test
  void deletesOnlyTheAuthenticatedCustomersIdentityAndAnonymizesPersonalData() {
    UserAccount customer = customer(AccountStatus.ACTIVE);
    when(users.findById(41L)).thenReturn(Optional.of(customer));

    service.deleteCustomerAccount(41L);

    verify(notifications).deleteByUserId(41L);
    verify(addresses).anonymizeByCustomerId(41L);
    verify(otpChallenges).deleteByPhone("9876543210");
    ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.DELETED);
    assertThat(saved.getValue().getPhone()).isNull();
    assertThat(saved.getValue().getEmail()).contains("@deleted.vjoykart.invalid");
    assertThat(saved.getValue().getDeletedAt()).isNotNull();
  }

  @Test
  void rejectsUnauthenticatedDeletion() {
    assertThatThrownBy(() -> service.deleteCustomerAccount(null))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void refusesToDeleteAUsersAccountThroughAnotherRole() {
    UserAccount admin = customer(AccountStatus.ACTIVE);
    admin.setRole(Role.ADMIN);
    when(users.findById(41L)).thenReturn(Optional.of(admin));

    assertThatThrownBy(() -> service.deleteCustomerAccount(41L))
        .isInstanceOf(ApiException.class);
    verify(users, never()).save(any());
  }

  @Test
  void repeatedDeletionIsSafeAndDoesNotChangeRetainedRecords() {
    UserAccount deleted = customer(AccountStatus.DELETED);
    when(users.findById(41L)).thenReturn(Optional.of(deleted));

    service.deleteCustomerAccount(41L);

    verifyNoInteractions(addresses, notifications, otpChallenges);
    verify(users, never()).save(any());
  }

  private UserAccount customer(AccountStatus status) {
    UserAccount customer = new UserAccount();
    customer.setName("Customer Name");
    customer.setEmail("customer@example.test");
    customer.setUsername("customer_1");
    customer.setPhone("9876543210");
    customer.setPasswordHash("old-hash");
    customer.setRole(Role.CUSTOMER);
    customer.setStatus(status);
    return customer;
  }
}
