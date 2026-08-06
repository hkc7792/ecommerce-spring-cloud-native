package com.ecommerce.commons.events;

import java.time.Instant;

/**
 * Event published when a SKU's stock is fully depleted (reaches 0).
 * Published to Kafka topic: "inventory-stock-events".
 */
public record StockDepletedEvent(
        String skuCode,
        int quantityAfter,
        Instant timestamp
) {}
