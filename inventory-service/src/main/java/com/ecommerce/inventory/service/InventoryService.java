package com.ecommerce.inventory.service;



import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.model.request.InventoryRequest;

import java.util.List;

public interface InventoryService {
    List<InventoryResponse> isInStock(List<String> skuCode);
    void addInventory(InventoryRequest inventoryRequest);
}
