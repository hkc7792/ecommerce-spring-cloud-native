package com.ecommerce.order.service.impl;

import com.ecommerce.commons.client.InventoryClient;
import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.order.dto.OrderItemDto;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entities.Order;
import com.ecommerce.order.entities.OrderLineItems;
import com.ecommerce.order.events.OrderPlaceEvent;
import com.ecommerce.order.exceptions.OutOfStockException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, OrderPlaceEvent> kafkaTemplate;

   /* @Override
    public String placeOrder(OrderRequest orderRequest) {

        // 1. Validate the Request & Map to Entity
        Order order = mapToOrder(orderRequest);
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        // 2. REMOTE CHECK (Call inventory service OUTSIDE @Transactional)
        // This avoids holding a DB connection while waiting for network I/O
        List<InventoryResponse> inventoryResponses = inventoryClient.isInStock(skuCodes);

        // 3. ROBUST VALIDATION
        validateStock(skuCodes, inventoryResponses);
        // 4. ATOMIC EXECUTION (Place Order & Reduce Stock)
        // We call a separate @Transactional method to ensure data integrity
        return executeOrderTransaction(order, orderRequest);

    }*/

    @Override
    public void placeOrder(OrderRequest orderRequest) {
       //1. save Order in db
        Order order = mapToOrder(orderRequest);
        orderRepository.save(order);

        //2. publish order place event to kafka
            //2.1 event creation
        OrderPlaceEvent event = new OrderPlaceEvent(order.getOrderNumber(),order.getOrderLineItemsList());
            // 2.2 Send to Kafka topic "notificationTopic" (Asynchronous)
        kafkaTemplate.send("notificationTopic", event);

        log.info("Order Placed Successfully, Event sent to Kafka");

    }

    private String executeOrderTransaction(Order order, OrderRequest orderRequest) {
        // 1. Save locally (Place the Order)
        orderRepository.save(order);

        // 2. Deduct remotely (Reduce Stock)
        List<InventoryRequest> reduceRequests = orderRequest.getOrderLineItemsDtoList().stream()
                .map(item -> new InventoryRequest(item.getSkuCode(), item.getQuantity()))
                .toList();

        // If this fails, @Transactional will ROLLBACK the orderRepository.save(order)
        inventoryClient.reduceStock(reduceRequests);

        return "Order Placed Successfully";
    }

    private void validateStock(List<String> skuCodes, List<InventoryResponse> inventoryResponses) {
        List<String> unavailable = skuCodes.stream()
                .filter(sku -> inventoryResponses.stream()
                        .noneMatch(res -> res.getSkuCode().equals(sku) && res.isInStock()))
                .toList();

        if (!unavailable.isEmpty()) {
            throw new OutOfStockException("Items unavailable: " + unavailable);
        }
    }

    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    private Order mapToOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        order.setOrderLineItemsList(orderLineItems);
        return order;
    }
    private OrderLineItems mapToDto(OrderItemDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

}
