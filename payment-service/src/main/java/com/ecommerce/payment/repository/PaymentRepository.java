package com.ecommerce.payment.repository;

import com.ecommerce.payment.entities.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderNumber(String orderNumber);

    Optional<Payment> findByPaymentId(String paymentId);

    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Payment> findByCustomerIdAndStatus(Long customerId, Payment.PaymentStatus status, Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);
}
