package com.ecommerce.inventory.controller;

import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Endpoints for inventory checking and management")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Check stock availability", description = "Checks if the given SKUs are in stock")
    @ApiResponse(responseCode = "200", description = "Stock checked successfully")
    public List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add inventory", description = "Adds new items to the inventory")
    @ApiResponse(responseCode = "201", description = "Inventory added successfully")
    public void addInventory(@Valid @RequestBody InventoryRequest inventoryRequest) {
        inventoryService.addInventory(inventoryRequest);
    }

    @PostMapping("/reduce")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reduce stock", description = "Reduces the stock quantity for given SKUs after an order")
    @ApiResponse(responseCode = "200", description = "Stock reduced successfully")
    public void reduceStock(@RequestBody List<InventoryRequest> reduceRequests) {
        inventoryService.reduceStock(reduceRequests);
    }

}
