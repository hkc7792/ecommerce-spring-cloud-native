package com.ecommerce.order.service.impl;

import com.ecommerce.commons.client.InventoryClient;
import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.order.dto.OrderItemDto;
import com.ecommerce.order.dto.OrderPlacementResponse;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entities.Order;
import com.ecommerce.order.entities.OrderLineItems;
import com.ecommerce.order.entities.OrderStatus;
import com.ecommerce.order.events.OrderStatusChangedEvent;
import com.ecommerce.order.exceptions.OutOfStockException;
import com.ecommerce.order.realtime.OrderStatusSseService;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String NOTIFICATION_TOPIC = "notificationTopic";
    private static final int MAX_PUBLISH_ATTEMPTS = 3;
    private static final long PUBLISH_BACKOFF_MS = 2000; // 2s, 4s backoff

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlaceEvent> kafkaTemplate;
    private final OutboxService outboxService;
    private final OrderStatusSseService sseService;

    @Override
    @Transactional(noRollbackFor = OutOfStockException.class)
    public OrderPlacementResponse placeOrder(OrderRequest orderRequest) {
        // 1. Persist order as PENDING
        Order order = mapToOrder(orderRequest);
        order = orderRepository.save(order);

        // 2. Reserve stock synchronously — compensating action on failure
        try {
            inventoryClient.reduceStock(toInventoryRequests(order));
            transition(order, OrderStatus.RESERVED);
        } catch (Exception e) {
            log.error("Stock reservation failed for order {}: {}", order.getOrderNumber(), e.getMessage());
            transition(order, OrderStatus.CANCELLED);
            throw new OutOfStockException("Insufficient stock for order: " + e.getMessage());
        }

        // 3. Publish OrderPlaceEvent (retry, then outbox fallback)
        OrderPlaceEvent event = buildEvent(order);
        publishOrderEventWithFallback(event, order.getOrderNumber());

        return new OrderPlacementResponse(order.getOrderNumber(), order.getStatus());
    }

    /**
     * Publishes the order event to Kafka. On failure retries up to
     * MAX_PUBLISH_ATTEMPTS with exponential backoff; if still failing the
     * event is persisted to the Outbox table for the relay worker to retry.
     * A blocking .get() ensures broker acknowledgement so a down Kafka
     * actually triggers the fallback instead of failing asynchronously.
     */
    private void publishOrderEventWithFallback(OrderPlaceEvent event, String orderNumber) {
        for (int attempt = 1; attempt <= MAX_PUBLISH_ATTEMPTS; attempt++) {
            try {
                kafkaTemplate.send(NOTIFICATION_TOPIC, orderNumber, event).get(5, TimeUnit.SECONDS);
                log.info("OrderPlaceEvent for {} published to Kafka (attempt {})", orderNumber, attempt);
                return;
            } catch (Exception e) {
                log.warn("Kafka publish attempt {} failed for order {}: {}",
                        attempt, orderNumber, e.getMessage());
                if (attempt < MAX_PUBLISH_ATTEMPTS) {
                    sleep(PUBLISH_BACKOFF_MS * attempt);
                }
            }
        }
        log.error("All {} publish attempts exhausted for order {}. Saving to Outbox.", MAX_PUBLISH_ATTEMPTS, orderNumber);
        outboxService.saveFailedEvent(event);
    }

    /**
     * Applies a status change to the order, persists it, and pushes the
     * transition to the customer's open SSE connections.
     */
    private void transition(Order order, OrderStatus newStatus) {
        OrderStatus previous = order.getStatus();
        order.setStatus(newStatus);
        orderRepository.save(order);
        sseService.publishStatus(new OrderStatusChangedEvent(
                order.getOrderNumber(), order.getCustomerId(), previous, newStatus, Instant.now()));
    }

    private Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setCustomerId(orderRequest.customerId());
        List<OrderLineItems> orderLineItems = orderRequest.orderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);

        // Calculate total amount from line items
        BigDecimal totalAmount = orderLineItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        return order;
    }

    private OrderLineItems mapToDto(OrderItemDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.price());
        orderLineItems.setQuantity(orderLineItemsDto.quantity());
        orderLineItems.setSkuCode(orderLineItemsDto.skuCode());
        return orderLineItems;
    }

    private List<InventoryRequest> toInventoryRequests(Order order) {
        return order.getOrderLineItemsList().stream()
                .map(item -> new InventoryRequest(item.getSkuCode(), item.getQuantity()))
                .toList();
    }

    private OrderPlaceEvent buildEvent(Order order) {
        List<OrderPlaceEvent.OrderLineItem> eventItems = order.getOrderLineItemsList().stream()
                .map(item -> new OrderPlaceEvent.OrderLineItem(
                        item.getSkuCode(), item.getPrice(), item.getQuantity()))
                .toList();
        return new OrderPlaceEvent(
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getTotalAmount(),
                Instant.now(),
                eventItems
        );
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
