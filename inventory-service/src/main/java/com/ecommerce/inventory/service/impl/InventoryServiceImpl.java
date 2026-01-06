package com.ecommerce.inventory.service.impl;

import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.entites.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCode) {
        return inventoryRepository.findBySkuCodeIn(skuCode).stream()
                .map(inventory ->
                        InventoryResponse.builder()
                                .skuCode(inventory.getSkuCode())
                                .isInStock(inventory.getQuantity() > 0)
                                .build()
                ).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // Ensures rollback for ALL exceptions
    public void addInventory(InventoryRequest inventoryRequest) {
        inventoryRepository.findBySkuCode(inventoryRequest.getSkuCode())
                .ifPresentOrElse(
                        existingInventory -> {
                            // UPDATE existing item count
                            existingInventory.setQuantity(existingInventory.getQuantity() + inventoryRequest.getQuantity());
                            inventoryRepository.save(existingInventory);
                        },
                        () -> {
                            // ADD new item to stock
                            Inventory newInventory = Inventory.builder().skuCode(inventoryRequest.getSkuCode()).quantity(inventoryRequest.getQuantity()).status("IN_STOCK").build();
                            inventoryRepository.save(newInventory);
                        }
                );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reduceStock(List<InventoryRequest> reduceRequests) {
        for (InventoryRequest request : reduceRequests) {
            Inventory inventory = inventoryRepository.findBySkuCode(request.getSkuCode())
                    .orElseThrow(() -> new RuntimeException("Item not found: " + request.getSkuCode()));

            if (inventory.getQuantity() < request.getQuantity()) {
                throw new RuntimeException("Insufficient stock for: " + request.getSkuCode());
            }

            inventory.setQuantity(inventory.getQuantity() - request.getQuantity());
            inventory.setStatus(inventory.getQuantity() > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
            inventoryRepository.save(inventory);
        }
    }
}
