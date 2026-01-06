package com.ecommerce.inventory.model.request;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryRequest {

    @NotBlank(message = "SKU Code is required")
    private String skuCode;
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1") // Ensures not zero or negative
    private Integer quantity;
}
