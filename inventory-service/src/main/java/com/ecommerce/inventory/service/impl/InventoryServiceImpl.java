package com.ecommerce.inventory.service.impl;

import com.ecommerce.commons.events.LowStockAlertEvent;
import com.ecommerce.commons.events.StockDepletedEvent;
import com.ecommerce.commons.events.StockReplenishedEvent;
import com.ecommerce.commons.requests.InventoryRequest;
import com.ecommerce.commons.responses.InventoryResponse;
import com.ecommerce.inventory.entities.Inventory;
import com.ecommerce.inventory.realtime.StockSseService;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final String STOCK_EVENTS_TOPIC = "inventory-stock-events";
    private static final String ALERTS_TOPIC = "inventory-alerts";

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StockSseService stockSseService;

    @Value("${inventory.low-stock-threshold:10}")
    private int lowStockThreshold;

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
                            publishReplenished(existingInventory.getSkuCode(), existingInventory.getQuantity());
                        },
                        () -> {
                            // ADD new item to stock
                            Inventory newInventory = Inventory.builder().skuCode(inventoryRequest.getSkuCode()).quantity(inventoryRequest.getQuantity()).status("IN_STOCK").build();
                            inventoryRepository.save(newInventory);
                            publishReplenished(newInventory.getSkuCode(), newInventory.getQuantity());
                        }
                );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reduceStock(List<InventoryRequest> reduceRequests) {
        for (InventoryRequest request : reduceRequests) {
            String skuCode = request.getSkuCode();
            int qty = request.getQuantity();

            Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + skuCode));

            // Projected remaining quantity after this reduction
            int quantityAfter = inventory.getQuantity() - qty;
            String newStatus = quantityAfter > 0 ? "IN_STOCK" : "OUT_OF_STOCK";

            // Atomic UPDATE ... WHERE quantity >= :qty guarantees no overselling
            int affectedRows = inventoryRepository.reduceStockAtomic(skuCode, qty, newStatus);
            if (affectedRows == 0) {
                throw new RuntimeException("Insufficient stock for: " + skuCode);
            }

            publishReductionEvents(skuCode, quantityAfter);
        }
    }

    private void publishReductionEvents(String skuCode, int quantityAfter) {
        if (quantityAfter <= 0) {
            StockDepletedEvent depletedEvent = new StockDepletedEvent(skuCode, quantityAfter, Instant.now());
            kafkaTemplate.send(STOCK_EVENTS_TOPIC, depletedEvent);
            stockSseService.publish(depletedEvent);
            log.info("Stock depleted for SKU {} — published StockDepletedEvent", skuCode);
        }
        if (quantityAfter < lowStockThreshold) {
            LowStockAlertEvent alertEvent = new LowStockAlertEvent(skuCode, quantityAfter, lowStockThreshold, Instant.now());
            kafkaTemplate.send(ALERTS_TOPIC, alertEvent);
            stockSseService.publish(alertEvent);
            log.info("Low stock for SKU {} ({} < {}) — published LowStockAlertEvent",
                    skuCode, quantityAfter, lowStockThreshold);
        }
    }

    private void publishReplenished(String skuCode, int quantityAfter) {
        StockReplenishedEvent replenishedEvent = new StockReplenishedEvent(skuCode, quantityAfter, Instant.now());
        kafkaTemplate.send(STOCK_EVENTS_TOPIC, replenishedEvent);
        stockSseService.publish(replenishedEvent);
        log.info("Stock replenished for SKU {} (now {}) — published StockReplenishedEvent", skuCode, quantityAfter);
    }
}
