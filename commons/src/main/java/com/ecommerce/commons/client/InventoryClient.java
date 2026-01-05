package com.ecommerce.commons.client;

import com.ecommerce.commons.responses.InventoryResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/inventory")
public interface InventoryClient {
    @GetExchange
    List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode);
}
