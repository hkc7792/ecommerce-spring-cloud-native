package com.ecommerce.order.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDto {
    private String skuCode;
    private BigDecimal price;
    private Integer quantity;
}
