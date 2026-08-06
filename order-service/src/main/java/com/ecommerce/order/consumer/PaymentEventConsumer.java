package com.ecommerce.order.consumer;

import com.ecommerce.commons.client.InventoryClient;
import com.ecommerce.commons.enums.OrderStatus;
import com.ecommerce.commons.events.OrderStatusChangedEvent;
import com.ecommerce.commons.events.PaymentCompletedEvent;
import com.ecommerce.commons.events.PaymentFailedEvent;
import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.order.entities.Order;
import com.ecommerce.order.entities.OrderLineItems;
import com.ecommerce.order.realtime.OrderStatusSseService;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes payment results from "paymentTopic" and drives the order
 * state machine: RESERVED -> CONFIRMED (payment ok) or
 * RESERVED -> CANCELLED + stock release (payment failed).
 *
 * A single listener method is used (branching on the concrete type) so
 * this consumer owns the whole group's partitions regardless of the
 * topic's partition count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String ORDER_STATUS_UPDATES_TOPIC = "order-status-updates";

    private final OrderRepository orderRepository;
    private final OrderStatusSseService sseService;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderStatusChangedEvent> orderStatusKafkaTemplate;

    @KafkaListener(topics = "paymentTopic", groupId = "order-service-group")
    public void onPaymentResult(Object payload) {
        if (payload instanceof PaymentCompletedEvent completed) {
            handlePaymentCompleted(completed);
        } else if (payload instanceof PaymentFailedEvent failed) {
            handlePaymentFailed(failed);
        } else {
            log.warn("Ignoring unknown payload on paymentTopic: {}",
                    payload == null ? "null" : payload.getClass().getName());
        }
    }

    private void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = findOrder(event.orderNumber());
        OrderStatus previous = order.getStatus();

        if (previous != OrderStatus.RESERVED) {
            log.warn("Payment completed for order {} but current status is {}; skipping transition",
                    event.orderNumber(), previous);
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        publishStatus(order, previous);
        log.info("Order {} CONFIRMED after payment {}", event.orderNumber(), event.paymentId());
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        Order order = findOrder(event.orderNumber());
        OrderStatus previous = order.getStatus();

        if (previous != OrderStatus.RESERVED) {
            log.warn("Payment failed for order {} but current status is {}; skipping transition",
                    event.orderNumber(), previous);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        publishStatus(order, previous);
        log.warn("Order {} CANCELLED after payment failure: {}", event.orderNumber(), event.failureReason());

        // Compensating transaction: restore the stock reserved at placement.
        releaseStock(order);
    }

    private void releaseStock(Order order) {
        for (OrderLineItems item : order.getOrderLineItemsList()) {
            try {
                inventoryClient.addStock(new InventoryRequest(item.getSkuCode(), item.getQuantity()));
            } catch (Exception e) {
                log.error("Failed to release stock for SKU {} on cancelled order {}: {}",
                        item.getSkuCode(), order.getOrderNumber(), e.getMessage());
            }
        }
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
    }

    private void publishStatus(Order order, OrderStatus previous) {
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                order.getOrderNumber(), order.getCustomerId(), previous, order.getStatus(), Instant.now());
        // SSE is the primary realtime path; Kafka publish is best-effort.
        sseService.publishStatus(event);
        try {
            orderStatusKafkaTemplate.send(ORDER_STATUS_UPDATES_TOPIC, event.orderNumber(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish OrderStatusChangedEvent for order {} to Kafka: {}",
                                    event.orderNumber(), ex.getMessage());
                        } else {
                            log.info("OrderStatusChangedEvent for {} published to Kafka topic {}",
                                    event.orderNumber(), ORDER_STATUS_UPDATES_TOPIC);
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to publish OrderStatusChangedEvent for order {} to Kafka: {}",
                    event.orderNumber(), e.getMessage());
        }
    }
}
