package com.ecommerce.commons.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published by Payment Service when payment is successfully processed.
 * Consumed by Order Service to update order status to CONFIRMED.
 * Published to Kafka topic: "paymentTopic"
 */
public record PaymentCompletedEvent(
        String paymentId,
        String orderNumber,
        Long customerId,
        BigDecimal amount,
        String paymentMethod,
        String gatewayTransactionId,
        Instant paidAt
) {}
