package com.ecommerce.inventory.cotroller;

import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.model.request.InventoryRequest;
import com.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    public void addInventory(@Valid @RequestBody InventoryRequest inventoryRequest) {
        inventoryService.addInventory(inventoryRequest);
    }
}


