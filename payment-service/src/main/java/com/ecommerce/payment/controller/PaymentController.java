package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Endpoints for payment processing and history")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get payment details by order number.
     * GET /api/payment/{orderNumber}
     */
    @GetMapping("/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get payment by order number", description = "Retrieves payment details associated with a specific order")
    @ApiResponse(responseCode = "200", description = "Payment details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    public PaymentResponse getPaymentByOrderNumber(@PathVariable String orderNumber) {
        return paymentService.getPaymentByOrderNumber(orderNumber);
    }

    /**
     * Refund a completed payment for an order.
     * POST /api/payment/{orderNumber}/refund
     */
    @PostMapping("/{orderNumber}/refund")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Refund a payment", description = "Refunds a COMPLETED payment for an order and publishes a compensating refund event to cancel the order and release inventory")
    @ApiResponse(responseCode = "200", description = "Payment refunded successfully")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    @ApiResponse(responseCode = "409", description = "Payment is not in a refundable (COMPLETED) state")
    public PaymentResponse refundPayment(@PathVariable String orderNumber) {
        return paymentService.refund(orderNumber);
    }

    /**
     * Get paginated payment history for a customer.
     * GET /api/payment/history?customerId=123&page=0&size=20&status=COMPLETED
     */
    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get payment history", description = "Retrieves a paginated list of payments for a customer")
    @ApiResponse(responseCode = "200", description = "Payment history retrieved successfully")
    public Page<PaymentResponse> getPaymentHistory(
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return paymentService.getPaymentHistory(customerId, status, pageRequest);
    }
}
