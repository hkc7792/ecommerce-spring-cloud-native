package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Get payment details by order number.
     * GET /api/payment/{orderNumber}
     */
    @GetMapping("/{orderNumber}")
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponse getPaymentByOrderNumber(@PathVariable String orderNumber) {
        return paymentService.getPaymentByOrderNumber(orderNumber);
    }

    /**
     * Get paginated payment history for a customer.
     * GET /api/payment/history?customerId=123&page=0&size=20&status=COMPLETED
     */
    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
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
