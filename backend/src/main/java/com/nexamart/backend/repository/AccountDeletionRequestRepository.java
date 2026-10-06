package com.nexamart.backend.repository;

import com.nexamart.backend.domain.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {}
