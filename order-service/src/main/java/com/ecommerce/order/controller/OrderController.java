package com.ecommerce.order.controller;


import com.ecommerce.order.dto.OrderPlacementResponse;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.commons.events.OrderStatusChangedEvent;
import com.ecommerce.order.realtime.OrderStatusSseService;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Endpoints for order placement and realtime status tracking")
public class OrderController {

    private final OrderService orderService;
    private final OrderStatusSseService sseService;
    private final OrderRepository orderRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a new order", description = "Creates a new order, reserves inventory, and initiates payment processing")
    @ApiResponse(responseCode = "201", description = "Order placed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid order data")
    @ApiResponse(responseCode = "409", description = "Insufficient stock")
    public OrderPlacementResponse placeOrder(@Valid @RequestBody OrderRequest orderRequest) {
        return orderService.placeOrder(orderRequest);
    }

    /**
     * Server-Sent Events endpoint. The frontend opens an EventSource here and
     * receives an "order-status" event whenever the order changes state.
     * Optional {@code orderNumber} param replays the current status immediately,
     * so a client connecting after placing an order still gets the latest state.
     */
    @GetMapping(value = "/events/{customerId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to realtime order status updates", description = "SSE stream of OrderStatusChangedEvent for a customer")
    public SseEmitter subscribeToOrderStatus(
            @PathVariable Long customerId,
            @RequestParam(required = false) String orderNumber) {

        SseEmitter emitter = sseService.subscribe(customerId);

        if (orderNumber != null) {
            orderRepository.findByOrderNumber(orderNumber).ifPresent(order ->
                    sseService.publishStatus(new OrderStatusChangedEvent(
                            order.getOrderNumber(),
                            order.getCustomerId(),
                            order.getStatus(),
                            order.getStatus(),
                            Instant.now())));
        }
        return emitter;
    }
}
