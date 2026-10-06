package com.nexamart.backend.api;

import com.nexamart.backend.domain.AccountDeletionRequest;
import com.nexamart.backend.repository.AccountDeletionRequestRepository;
import com.nexamart.backend.security.CurrentUser;
import com.nexamart.backend.service.AccountDeletionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
  private final AccountDeletionService deletionService;
  private final AccountDeletionRequestRepository requests;
  public AccountController(AccountDeletionService deletionService, AccountDeletionRequestRepository requests) {
    this.deletionService = deletionService; this.requests = requests;
  }
  @DeleteMapping("/account")
  public ResponseEntity<Void> deleteAccount() {
    deletionService.deleteCustomerAccount(CurrentUser.id());
    return ResponseEntity.noContent().build();
  }
  @PostMapping("/account-deletion-requests")
  public ResponseEntity<Void> requestDeletion(@Valid @RequestBody PublicDeletionRequest request) {
    if (!StringUtils.hasText(request.email()) && !StringUtils.hasText(request.phone())) return ResponseEntity.badRequest().build();
    AccountDeletionRequest entity = new AccountDeletionRequest();
    entity.setEmail(StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase() : null);
    entity.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
    requests.save(entity);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
  public record PublicDeletionRequest(
      @Email @Size(max = 255) String email,
      @Pattern(regexp = "^$|^\\+?[0-9]{10,13}$") @Size(max = 40) String phone) {}
}
