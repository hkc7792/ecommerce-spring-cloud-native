package com.ecommerce.order.dto;

import com.ecommerce.commons.enums.OrderStatus;

/**
 * Returned by POST /api/order so the client immediately knows the
 * orderNumber and initial status, and can subscribe to realtime updates.
 */
public record OrderPlacementResponse(
        String orderNumber,
        OrderStatus status
) {}
