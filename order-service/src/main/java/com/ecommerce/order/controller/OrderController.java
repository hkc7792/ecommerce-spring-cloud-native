package com.ecommerce.order.controller;


import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for order placement and management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order", description = "Creates a new order, reduces inventory, and initiates payment processing")
    @ApiResponse(responseCode = "201", description = "Order placed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid order data or out of stock")
    public String placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        orderService.placeOrder(orderRequest);
        return "Order Placed Successfully";
    }
}
