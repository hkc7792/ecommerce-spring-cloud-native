package com.ecommerce.commons.enums;

/**
 * Lifecycle states of an order, matching the saga state machine in
 * docs/architecture/saga-orchestration.md.
 */
public enum OrderStatus {
    PENDING,    // Order received, persisted. Processing not started.
    RESERVED,   // Stock reserved for all line items.
    CONFIRMED,  // Payment processed successfully. Order finalized.
    SHIPPED,    // Handed off to logistics partner.
    DELIVERED,  // Customer received the order.
    CANCELLED   // Terminal state after a failure or user cancellation.
}
