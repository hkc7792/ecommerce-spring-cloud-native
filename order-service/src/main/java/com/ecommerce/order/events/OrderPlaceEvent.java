package com.ecommerce.order.events;

import com.ecommerce.order.dto.OrderItemDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlaceEvent(
        String orderNumber,      // Unique business ID for the order
        Long customerId,         // Who placed it
        BigDecimal totalAmount,  // Total cost
        Instant orderTime,       // When it happened (ISO-8601 format)
        List<OrderItemDto> items // What was bought
){}
