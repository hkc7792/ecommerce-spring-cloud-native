package com.ecommerce.commons.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Event published when a new order is placed.
 * Consumed by Payment Service, Notification Service, etc.
 * Published to Kafka topic: "notificationTopic"
 */
public record OrderPlaceEvent(
        String orderNumber,
        Long customerId,
        BigDecimal totalAmount,
        Instant orderTime,
        List<OrderLineItem> items
) {
    /**
     * Represents a single line item in the order.
     * Flattened from the Order entity's OrderLineItems for event transport.
     */
    public record OrderLineItem(
            String skuCode,
            BigDecimal price,
            Integer quantity
    ) {}
}
