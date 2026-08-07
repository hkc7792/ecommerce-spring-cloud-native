package com.ecommerce.commons.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published by Payment Service when a completed payment is refunded
 * (compensating transaction).
 * Consumed by Order Service to cancel the confirmed order and release inventory.
 * Published to Kafka topic: "paymentTopic"
 */
public record PaymentRefundedEvent(
        String paymentId,
        String orderNumber,
        Long customerId,
        BigDecimal amount,
        String reason,
        Instant refundedAt
) {}
