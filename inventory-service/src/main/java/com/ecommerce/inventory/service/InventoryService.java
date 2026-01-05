package com.ecommerce.inventory.service;



import com.ecommerce.commons.responses.InventoryResponse;

import java.util.List;

public interface InventoryService {
    List<InventoryResponse> isInStock(List<String> skuCode);
}
