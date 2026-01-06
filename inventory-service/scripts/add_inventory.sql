-- 1. Create a temporary table to hold the merged sums
CREATE TEMPORARY TABLE temp_inventory_summary AS
SELECT
    sku_code,
    SUM(quantity) as total_quantity,
    -- Logic: If total > 0, set to IN_STOCK, else OUT_OF_STOCK
    CASE WHEN SUM(quantity) > 0 THEN 'IN_STOCK' ELSE 'OUT_OF_STOCK' END as new_status
FROM inventory
GROUP BY sku_code;

-- 2. Clear the current inventory table
-- WARNING: This deletes the current rows, ensure you have a backup if this is production!
TRUNCATE TABLE inventory;

-- 3. Re-insert the merged data
INSERT INTO inventory (sku_code, quantity, status)
SELECT sku_code, total_quantity, new_status
FROM temp_inventory_summary;

-- 4. CRITICAL: Add a unique constraint to prevent duplicates in the future
-- This ensures your Spring Boot "Upsert" logic works correctly at the DB level.
ALTER TABLE inventory ADD UNIQUE (sku_code);

-- 5. Drop the temp table
DROP TEMPORARY TABLE temp_inventory_summary;