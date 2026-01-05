package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;

public interface OrderService {
    public String placeOrder(OrderRequest orderRequest);
}
