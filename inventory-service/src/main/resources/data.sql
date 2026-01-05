-- For MySQL: This prevents errors if the rows already exist
INSERT IGNORE INTO inventory (sku_code, quantity, status)
VALUES ('iphone_15', 100, 'IN_STOCK');

INSERT IGNORE INTO inventory (sku_code, quantity, status)
VALUES ('iphone_15_pro', 50, 'IN_STOCK');

INSERT IGNORE INTO inventory (sku_code, quantity, status)
VALUES ('pixel_8', 0, 'OUT_OF_STOCK');