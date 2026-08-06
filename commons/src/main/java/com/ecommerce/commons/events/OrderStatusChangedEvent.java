package com.ecommerce.commons.events;

import com.ecommerce.commons.enums.OrderStatus;

import java.time.Instant;

/**
 * Published whenever an order transitions between states.
 * Pushed to the customer over SSE in realtime and optionally
 * forwarded to downstream consumers (notification service, etc.).
 */
public record OrderStatusChangedEvent(
        String orderNumber,
        Long customerId,
        OrderStatus previousStatus,
        OrderStatus currentStatus,
        Instant changedAt
) {}
