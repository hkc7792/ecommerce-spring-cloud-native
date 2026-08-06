package com.ecommerce.payment.consumer;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    /**
     * Listens for OrderPlaceEvent messages on the "notificationTopic" and processes payment.
     *
     * Reliability: with {@link RetryableTopic} a processing failure is NEVER silently
     * dropped. The message is retried with exponential backoff (1s → 2s → 4s, 4 total
     * attempts) and, if it still fails, published to the auto-created
     * "notificationTopic-dlt" dead-letter topic for operator review ({@link #handleDlt}).
     */
    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            numPartitions = "-1",       // match the source topic partition count
            replicationFactor = "-1"    // match the source topic replication factor
    )
    @KafkaListener(
            topics = "notificationTopic",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrderPlaceEvent(OrderPlaceEvent event) {
        log.info("Received OrderPlaceEvent for order: {} — customer: {}, amount: ₹{}",
                event.orderNumber(), event.customerId(), event.totalAmount());

        // No try/catch here on purpose: @RetryableTopic needs the exception to propagate
        // so the message is retried and, ultimately, dead-lettered on "notificationTopic-dlt".
        paymentService.processPayment(event);
    }

    /**
     * Dead-letter handler for "notificationTopic-dlt". Reached only after all retries
     * are exhausted — the message requires operator review.
     */
    @DltHandler
    public void handleDlt(OrderPlaceEvent event,
                          @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String receivedTopic,
                          @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String failureReason) {
        log.error("Payment processing for order {} permanently failed after all retries — "
                        + "message moved to DLQ ({}) for operator review. Reason: {}",
                event.orderNumber(), receivedTopic, failureReason);
    }
}
