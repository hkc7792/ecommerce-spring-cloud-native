package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    List<Inventory> findBySkuCodeIn(List<String> skuCode);

    Optional<Inventory> findBySkuCode(String skuCode);

    /**
     * Atomically decrements stock for a SKU. The {@code quantity >= :qty}
     * guard prevents overselling under concurrent flash-sale requests; a
     * return value of 0 means the SKU does not exist or has insufficient stock.
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty, i.status = :newStatus " +
           "WHERE i.skuCode = :skuCode AND i.quantity >= :qty")
    int reduceStockAtomic(@Param("skuCode") String skuCode, @Param("qty") int qty, @Param("newStatus") String newStatus);
}