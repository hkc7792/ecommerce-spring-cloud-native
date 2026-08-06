package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderPlacementResponse;
import com.ecommerce.order.dto.OrderRequest;

public interface OrderService {
    OrderPlacementResponse placeOrder(OrderRequest orderRequest);
}
