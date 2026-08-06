package com.ecommerce.commons.events;

import java.time.Instant;

/**
 * Event published when inventory is added for a SKU.
 * Published to Kafka topic: "inventory-stock-events".
 */
public record StockReplenishedEvent(
        String skuCode,
        int quantityAfter,
        Instant timestamp
) {}
