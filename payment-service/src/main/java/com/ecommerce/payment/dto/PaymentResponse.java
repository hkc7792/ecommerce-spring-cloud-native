package com.ecommerce.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentId,
        String orderNumber,
        Long customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gatewayTransactionId,
        String status,
        String failureReason,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
