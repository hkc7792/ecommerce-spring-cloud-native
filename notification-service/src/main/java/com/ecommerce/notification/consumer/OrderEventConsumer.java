package com.ecommerce.notification.consumer;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.commons.events.OrderStatusChangedEvent;
import com.ecommerce.notification.service.NotificationPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order events from Kafka and fans them out over WebSocket/STOMP.
 *
 * Pushing to subscribers is best-effort: a WebSocket send failure must
 * never take down the consumer, so each delivery is guarded and only
 * logged on error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationPushService pushService;

    @KafkaListener(topics = "notificationTopic", groupId = "notification-service-group")
    public void onOrderPlaced(OrderPlaceEvent event) {
        try {
            pushService.pushOrderPlaced(event);
        } catch (Exception e) {
            log.error("Failed to push OrderPlaceEvent for order {}: {}", event.orderNumber(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "order-status-updates", groupId = "notification-service-group")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            pushService.pushOrderStatus(event);
        } catch (Exception e) {
            log.error("Failed to push OrderStatusChangedEvent for order {}: {}", event.orderNumber(), e.getMessage(), e);
        }
    }
}
