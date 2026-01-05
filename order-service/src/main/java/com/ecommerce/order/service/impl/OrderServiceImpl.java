package com.ecommerce.order.service.impl;

import com.ecommerce.commons.client.InventoryClient;
import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.order.dto.OrderLineItemsDto;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entities.Order;
import com.ecommerce.order.entities.OrderLineItems;
import com.ecommerce.order.exceptions.OutOfStockException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Override
    public String placeOrder(OrderRequest orderRequest) {

        // 1. Map Request to Entity
        Order order = mapToOrder(orderRequest);

        // 2. Extract SKU codes for the inventory check
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
        // 3. Call Inventory Service (Outside the Transaction)
        List<InventoryResponse> inventoryResponses = inventoryClient.isInStock(skuCodes);

        // 4. Validate Stock
        boolean allProductsInStock = inventoryResponses.stream()
                .allMatch(InventoryResponse::isInStock);
        boolean allItemsFound = inventoryResponses.size() == skuCodes.size();

        if (allProductsInStock && allItemsFound) {
            // 5. Call the transactional method for DB operations
            saveOrder(order);
            return "Order Placed Successfully";
        }

        // 4. Identify EXACT failures for better client feedback
        List<String> missingOrEmptySkus = skuCodes.stream()
                .filter(sku -> inventoryResponses.stream()
                        .noneMatch(res -> res.getSkuCode().equals(sku) && res.isInStock()))
                .toList();

        throw new OutOfStockException("The following items are unavailable: " + missingOrEmptySkus);

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
    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

}
