package com.ecommerce.order.entities;


import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_line_items")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderLineItems {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The SKU (Stock Keeping Unit) connects this to the Inventory Service
    private String skuCode;

    private BigDecimal price;

    private Integer quantity;
}