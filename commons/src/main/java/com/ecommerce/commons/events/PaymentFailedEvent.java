package com.ecommerce.commons.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published by Payment Service when payment fails.
 * Consumed by Order Service to cancel the order and release inventory.
 * Published to Kafka topic: "paymentTopic"
 */
public record PaymentFailedEvent(
        String paymentId,
        String orderNumber,
        Long customerId,
        BigDecimal amount,
        String failureReason,
        Instant failedAt
) {}
