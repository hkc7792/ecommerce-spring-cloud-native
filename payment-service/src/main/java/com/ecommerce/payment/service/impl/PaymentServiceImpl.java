package com.ecommerce.payment.service.impl;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.commons.events.PaymentCompletedEvent;
import com.ecommerce.commons.events.PaymentFailedEvent;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.entities.Payment;
import com.ecommerce.payment.entities.Payment.PaymentMethod;
import com.ecommerce.payment.entities.Payment.PaymentStatus;
import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYMENT_TOPIC = "paymentTopic";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000");
    private static final int HIGH_VALUE_FAILURE_PERCENTAGE = 20;
    private final Random random = new Random();

    @Override
    @Transactional
    public void processPayment(OrderPlaceEvent orderPlaceEvent) {
        String orderNumber = orderPlaceEvent.orderNumber();

        // Idempotency check — skip if already processed
        if (paymentRepository.existsByOrderNumber(orderNumber)) {
            log.warn("Payment already exists for order: {}. Skipping duplicate.", orderNumber);
            return;
        }

        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 12);
        PaymentMethod method = selectPaymentMethod();

        // Create payment record with PROCESSING status
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderNumber(orderNumber)
                .customerId(orderPlaceEvent.customerId())
                .amount(orderPlaceEvent.totalAmount())
                .paymentMethod(method)
                .status(PaymentStatus.PROCESSING)
                .build();
        paymentRepository.save(payment);

        log.info("Processing payment {} for order {} — amount: ₹{}, method: {}",
                paymentId, orderNumber, orderPlaceEvent.totalAmount(), method);

        // Simulate payment gateway call
        boolean paymentSuccessful = simulatePaymentGateway(orderPlaceEvent.totalAmount());

        if (paymentSuccessful) {
            String gatewayTxnId = "gw_txn_" + UUID.randomUUID().toString().substring(0, 10);
            payment.setGatewayTransactionId(gatewayTxnId);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            log.info("Payment COMPLETED for order: {} — gateway txn: {}", orderNumber, gatewayTxnId);

            // Publish success event
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    paymentId, orderNumber, orderPlaceEvent.customerId(),
                    orderPlaceEvent.totalAmount(), method.name(), gatewayTxnId, Instant.now()
            );
            kafkaTemplate.send(PAYMENT_TOPIC, orderNumber, event);

        } else {
            String failureReason = determineFailureReason(orderPlaceEvent.totalAmount());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            paymentRepository.save(payment);

            log.warn("Payment FAILED for order: {} — reason: {}", orderNumber, failureReason);

            // Publish failure event
            PaymentFailedEvent event = new PaymentFailedEvent(
                    paymentId, orderNumber, orderPlaceEvent.customerId(),
                    orderPlaceEvent.totalAmount(), failureReason, Instant.now()
            );
            kafkaTemplate.send(PAYMENT_TOPIC, orderNumber, event);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderNumber(String orderNumber) {
        Payment payment = paymentRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for order: " + orderNumber));
        return mapToResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentHistory(Long customerId, String status, Pageable pageable) {
        Page<Payment> payments;
        if (status != null && !status.isBlank()) {
            PaymentStatus paymentStatus = PaymentStatus.valueOf(status.toUpperCase());
            payments = paymentRepository.findByCustomerIdAndStatus(customerId, paymentStatus, pageable);
        } else {
            payments = paymentRepository.findByCustomerId(customerId, pageable);
        }
        return payments.map(this::mapToResponse);
    }

    // --- Private helpers ---

    /**
     * Simulates a payment gateway call.
     * High-value orders (> ₹100,000) have a 20% failure rate for realistic testing.
     * Normal orders have a 5% failure rate.
     */
    private boolean simulatePaymentGateway(BigDecimal amount) {
        try {
            // Simulate network latency (200-800ms)
            Thread.sleep(200 + random.nextInt(600));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int failureThreshold = amount.compareTo(HIGH_VALUE_THRESHOLD) > 0
                ? HIGH_VALUE_FAILURE_PERCENTAGE
                : 5;
        return random.nextInt(100) >= failureThreshold;
    }

    private String determineFailureReason(BigDecimal amount) {
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return "INSUFFICIENT_FUNDS";
        }
        String[] reasons = {"CARD_DECLINED", "GATEWAY_TIMEOUT", "PROCESSING_ERROR"};
        return reasons[random.nextInt(reasons.length)];
    }

    private PaymentMethod selectPaymentMethod() {
        PaymentMethod[] methods = PaymentMethod.values();
        return methods[random.nextInt(methods.length)];
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod().name(),
                payment.getGatewayTransactionId(),
                payment.getStatus().name(),
                payment.getFailureReason(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
