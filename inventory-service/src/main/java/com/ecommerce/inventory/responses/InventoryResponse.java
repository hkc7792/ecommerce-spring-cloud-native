package com.ecommerce.inventory.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InventoryResponse {
    private String skuCode;   // The unique identifier for the product
    private boolean isInStock; // True if quantity > 0, otherwise false
}