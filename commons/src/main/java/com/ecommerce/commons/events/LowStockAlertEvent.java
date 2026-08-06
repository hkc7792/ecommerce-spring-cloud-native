package com.ecommerce.commons.events;

import java.time.Instant;

/**
 * Event published when a SKU's quantity drops below the low-stock threshold.
 * Published to Kafka topic: "inventory-alerts".
 */
public record LowStockAlertEvent(
        String skuCode,
        int currentQuantity,
        int threshold,
        Instant timestamp
) {}
