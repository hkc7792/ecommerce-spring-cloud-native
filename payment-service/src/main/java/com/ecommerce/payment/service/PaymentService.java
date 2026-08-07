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
     * Refund a completed payment for an order.
     * Marks the payment REFUNDED and publishes a {@code PaymentRefundedEvent}
     * so the order service can cancel the order and release inventory.
     *
     * @param orderNumber the order whose payment should be refunded
     * @return the updated payment details
     */
    PaymentResponse refund(String orderNumber);

    /**
     * Get payment details by order number.
     */
    PaymentResponse getPaymentByOrderNumber(String orderNumber);

    /**
     * Get paginated payment history for a customer.
     */
    Page<PaymentResponse> getPaymentHistory(Long customerId, String status, Pageable pageable);
}
