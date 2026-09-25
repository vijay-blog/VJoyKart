package com.nexamart.backend.repository;

import com.nexamart.backend.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
