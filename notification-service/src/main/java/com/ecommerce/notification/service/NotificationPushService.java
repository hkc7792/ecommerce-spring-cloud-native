package com.ecommerce.notification.service;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.commons.events.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Broadcasts order events to STOMP subscribers.
 *
 * Every event is pushed to /topic/orders/{orderNumber}, so any client
 * (web, mobile) subscribed to that destination receives the update in
 * realtime. The simple in-memory broker is single-instance; a Redis
 * pub/sub broker would be needed for multi-instance fan-out.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushOrderPlaced(OrderPlaceEvent event) {
        String destination = "/topic/orders/" + event.orderNumber();
        messagingTemplate.convertAndSend(destination, event);
        log.info("Pushed OrderPlaceEvent to {}", destination);
    }

    public void pushOrderStatus(OrderStatusChangedEvent event) {
        String destination = "/topic/orders/" + event.orderNumber();
        messagingTemplate.convertAndSend(destination, event);
        log.info("Pushed OrderStatusChangedEvent to {}", destination);
    }
}
