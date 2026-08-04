package com.ecommerce.payment.consumer;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    /**
     * Listens for OrderPlaceEvent messages on the "notificationTopic".
     * Processes payment for each incoming order.
     */
    @KafkaListener(
            topics = "notificationTopic",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderPlaceEvent(OrderPlaceEvent event) {
        log.info("Received OrderPlaceEvent for order: {} — customer: {}, amount: ₹{}",
                event.orderNumber(), event.customerId(), event.totalAmount());

        try {
            paymentService.processPayment(event);
        } catch (Exception e) {
            log.error("Error processing payment for order: {} — {}",
                    event.orderNumber(), e.getMessage(), e);
            // In production: send to DLQ (Dead Letter Queue) for manual review
        }
    }
}
