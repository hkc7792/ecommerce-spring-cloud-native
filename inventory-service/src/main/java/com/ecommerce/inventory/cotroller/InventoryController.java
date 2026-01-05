package com.ecommerce.inventory.cotroller;

import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode) {
       return inventoryService.isInStock(skuCode);
        //return Collections.singletonList(InventoryResponse.builder().skuCode("skuCode").isInStock(true).build());

    }
}
