package com.ecommerce.order.service.impl;

import com.ecommerce.commons.client.InventoryClient;
import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.order.dto.OrderItemDto;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entities.Order;
import com.ecommerce.order.entities.OrderLineItems;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlaceEvent> kafkaTemplate;
    private final OutboxService outboxService;


    @Override
    @Transactional
    @Retryable(
            value = { Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public void placeOrder(OrderRequest orderRequest) {
       //1. Map and persist order
        Order order = mapToOrder(orderRequest);
        orderRepository.save(order);

        //2. Convert line items to event DTOs
        List<OrderPlaceEvent.OrderLineItem> eventItems = order.getOrderLineItemsList().stream()
                .map(item -> new OrderPlaceEvent.OrderLineItem(
                        item.getSkuCode(), item.getPrice(), item.getQuantity()))
                .toList();

        //3. Event creation
        OrderPlaceEvent event = new OrderPlaceEvent(
                order.getOrderNumber(),
                order.getCustomerId(),
                order.getTotalAmount(),
                Instant.now(),
                eventItems
        );

        // 4. Call the retryable method
        try {
            this.publishOrderEvent(event, orderRequest, order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to publish event for order: {}", order.getOrderNumber());
        }
    }

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void publishOrderEvent(OrderPlaceEvent event, OrderRequest request, String orderNumber) {
        log.info("Attempting to send event to Kafka for order: {}", orderNumber);
        kafkaTemplate.send("notificationTopic", event);
    }

    @Recover
    public void recoverOrderPlacement(Exception e, OrderPlaceEvent event, OrderRequest orderRequest, String orderNumber) {
        log.error("All retries exhausted for Kafka. Saving event {} , to Outbox table as backup.",event);

        // Save to your Out_Box table here so the event isn't lost
        outboxService.saveFailedEvent(orderRequest, orderNumber);
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

}
