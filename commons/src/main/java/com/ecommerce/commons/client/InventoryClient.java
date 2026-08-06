package com.ecommerce.commons.client;

import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.commons.responses.InventoryResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/api/inventory")
public interface InventoryClient {
    @GetExchange("/items")
    List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode);

    // New method to reduce stock after order placement
    @PostExchange("/reduce")
    void reduceStock(@RequestBody List<InventoryRequest> reduceRequests);

    // Restore stock when an order is cancelled (saga compensation)
    @PostExchange("/add")
    void addStock(@RequestBody InventoryRequest addRequest);
}
