package com.ecommerce.payment.service;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.payment.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    /**
     * Process payment for an incoming order event.
     * Called by the Kafka consumer when an OrderPlaceEvent is received.
     */
    void processPayment(OrderPlaceEvent orderPlaceEvent);

    /**
     * Get payment details by order number.
     */
    PaymentResponse getPaymentByOrderNumber(String orderNumber);

    /**
     * Get paginated payment history for a customer.
     */
    Page<PaymentResponse> getPaymentHistory(Long customerId, String status, Pageable pageable);
}
