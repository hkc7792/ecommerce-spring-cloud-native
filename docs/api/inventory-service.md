# Inventory Service — API Documentation

## Service Overview

| Property         | Value                                                |
|------------------|------------------------------------------------------|
| Service Name     | `inventory-service`                                   |
| Base URL         | `http://localhost:8081/api/inventory`                  |
| Database         | `inventory_db` (MySQL 8.x, port 3307)                |
| Package          | `com.ecommerce.inventory`                             |
| Spring Boot      | 3.4.1                                                 |
| Java             | 21                                                    |

---

## Endpoints

### 1. Check Stock Availability

Returns the availability status for one or more SKU codes. Used by the Order Service during checkout to validate that all items are in stock before placing an order.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `GET`                                              |
| Path        | `/api/inventory/items`                             |
| Auth        | None (planned: service-to-service auth)            |
| Status      | `200 OK`                                           |

#### Query Parameters

| Parameter  | Type           | Required | Description                              |
|------------|----------------|----------|------------------------------------------|
| `skuCode`  | `List<String>` | Yes      | One or more SKU codes to check           |

#### Request Example
```
GET /api/inventory/items?skuCode=IPHONE-15-128GB&skuCode=AIRPODS-PRO-2&skuCode=MACBOOK-AIR-M3
```

#### Response — 200 OK
```json
[
  {
    "skuCode": "IPHONE-15-128GB",
    "isInStock": true
  },
  {
    "skuCode": "AIRPODS-PRO-2",
    "isInStock": true
  },
  {
    "skuCode": "MACBOOK-AIR-M3",
    "isInStock": false
  }
]
```

#### Response Schema

| Field       | Type      | Description                                      |
|-------------|-----------|--------------------------------------------------|
| `skuCode`   | `String`  | The SKU code that was queried                    |
| `isInStock` | `boolean` | `true` if quantity > 0, `false` otherwise        |

#### cURL Example
```bash
curl -X GET "http://localhost:8081/api/inventory/items?skuCode=IPHONE-15-128GB&skuCode=AIRPODS-PRO-2"
```

> **Note**: Only SKUs that exist in the database are returned. If a requested SKU doesn't exist, it will be absent from the response (not returned as `false`). Consumers should treat missing SKUs as "not available".

---

### 2. Add Inventory Stock

Adds stock quantity for a given SKU. If the SKU already exists, the quantity is **added** to the current stock. If the SKU doesn't exist, a new inventory record is created.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/inventory/add`                               |
| Auth        | None (planned: Admin/Warehouse role required)      |
| Status      | `201 Created`                                      |

#### Request Body
```json
{
  "skuCode": "SAMSUNG-S24-ULTRA-256GB",
  "quantity": 150
}
```

#### Request Schema

| Field      | Type      | Required | Validation                                    |
|------------|-----------|----------|-----------------------------------------------|
| `skuCode`  | `String`  | Yes      | `@NotBlank` — must not be null or empty       |
| `quantity` | `Integer` | Yes      | `@NotNull`, `@Min(1)` — must be ≥ 1          |

#### Responses

**201 Created** — Stock added successfully (no response body)

**400 Bad Request** — Validation failure
```json
{
  "status": 400,
  "message": "SKU Code is required",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

```json
{
  "status": 400,
  "message": "Quantity must be at least 1",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

#### Behavior Details

| Scenario                  | Action                                                       |
|---------------------------|--------------------------------------------------------------|
| SKU exists, qty = 30      | `addInventory("SKU001", 20)` → qty becomes **50**            |
| SKU does not exist        | New `Inventory` record created with status `IN_STOCK`        |
| SKU exists, qty = 0       | `addInventory("SKU001", 10)` → qty becomes **10**, status stays as-is |

#### cURL Example
```bash
curl -X POST http://localhost:8081/api/inventory/add \
  -H "Content-Type: application/json" \
  -d '{
    "skuCode": "SAMSUNG-S24-ULTRA-256GB",
    "quantity": 150
  }'
```

---

### 3. Reduce Stock

Atomically reduces stock for one or more SKUs. This endpoint is called by the Order Service after an order is placed to deduct purchased quantities. The operation is **all-or-nothing** — if any item has insufficient stock, the entire batch rolls back.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/inventory/reduce`                            |
| Auth        | None (planned: service-to-service auth)            |
| Status      | `200 OK`                                           |

#### Request Body
```json
[
  {
    "skuCode": "IPHONE-15-128GB",
    "quantity": 1
  },
  {
    "skuCode": "AIRPODS-PRO-2",
    "quantity": 2
  }
]
```

#### Request Schema (per item)

| Field      | Type      | Required | Description                          |
|------------|-----------|----------|--------------------------------------|
| `skuCode`  | `String`  | Yes      | SKU to reduce stock for              |
| `quantity` | `Integer` | Yes      | Number of units to deduct            |

#### Responses

**200 OK** — Stock reduced successfully (no response body)

**500 Internal Server Error** — Item not found
```json
{
  "status": 500,
  "message": "Item not found: UNKNOWN-SKU",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

**500 Internal Server Error** — Insufficient stock
```json
{
  "status": 500,
  "message": "Insufficient stock for: IPHONE-15-128GB",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

#### Transaction Semantics

```
┌──────────────────────────────────────────────────────────────────┐
│  @Transactional(rollbackFor = Exception.class)                   │
│                                                                    │
│  FOR EACH item in request:                                        │
│    1. Find inventory by skuCode → throw if not found             │
│    2. Check quantity >= requested → throw if insufficient         │
│    3. Deduct quantity                                              │
│    4. Update status: qty > 0 → "IN_STOCK" | qty = 0 → "OUT_OF_STOCK" │
│    5. Save inventory                                               │
│                                                                    │
│  ANY exception → ROLLBACK entire batch (no partial deductions)   │
└──────────────────────────────────────────────────────────────────┘
```

#### cURL Example
```bash
curl -X POST http://localhost:8081/api/inventory/reduce \
  -H "Content-Type: application/json" \
  -d '[
    { "skuCode": "IPHONE-15-128GB", "quantity": 1 },
    { "skuCode": "AIRPODS-PRO-2", "quantity": 2 }
  ]'
```

---

## Domain Model

### Inventory Entity

| Column       | Type          | Constraints              | Description                              |
|--------------|---------------|--------------------------|------------------------------------------|
| `id`         | `BIGINT`      | PK, AUTO_INCREMENT       | Internal surrogate key                   |
| `sku_code`   | `VARCHAR`     | UNIQUE, NOT NULL         | Stock Keeping Unit — product variant ID  |
| `quantity`   | `INTEGER`     | Default: 0               | Current available stock count            |
| `status`     | `VARCHAR`     | Default: `OUT_OF_STOCK`  | `IN_STOCK` or `OUT_OF_STOCK`             |

### Status Transitions

```
┌──────────────┐    addInventory (qty > 0)     ┌──────────────┐
│ OUT_OF_STOCK │ ─────────────────────────────▶ │   IN_STOCK   │
│  (qty = 0)   │                                │  (qty > 0)   │
└──────────────┘ ◀───────────────────────────── └──────────────┘
                   reduceStock (remaining = 0)
```

---

## Inter-Service Client

The `InventoryClient` interface (in `commons` module) enables other services to call inventory endpoints using Spring HTTP Interface:

```java
@HttpExchange("/api/inventory")
public interface InventoryClient {
    @GetExchange("/items")
    List<InventoryResponse> isInStock(@RequestParam("skuCode") List<String> skuCode);

    @PostExchange("/reduce")
    void reduceStock(@RequestBody List<InventoryRequest> reduceRequests);
}
```

### Usage (from Order Service)
The `InventoryClient` is injected into `OrderServiceImpl` and used for synchronous stock validation and reduction during order placement.

---

## Sample Data (for Testing)

Use these requests to seed the inventory for local development:

```bash
# Add electronics
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"IPHONE-15-128GB","quantity":100}'
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"AIRPODS-PRO-2","quantity":250}'
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"MACBOOK-AIR-M3-256GB","quantity":50}'
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"SAMSUNG-S24-ULTRA-256GB","quantity":75}'

# Add fashion
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"NIKE-AIR-MAX-90-BLK-10","quantity":200}'
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" -d '{"skuCode":"LEVIS-501-JEANS-32","quantity":300}'

# Verify stock
curl "http://localhost:8081/api/inventory/items?skuCode=IPHONE-15-128GB&skuCode=AIRPODS-PRO-2&skuCode=MACBOOK-AIR-M3-256GB"
```

---

## Configuration

### Docker Ports
| Type             | Host     | Container |
|------------------|----------|-----------|
| Application      | `8081`   | `8080`    |
| Remote Debug     | `5001`   | `5000`    |

### Database
| Property                 | Value                                         |
|--------------------------|-----------------------------------------------|
| URL                      | `jdbc:mysql://inventory_db_container:3306/inventory_db` |
| Driver                   | `com.mysql.cj.jdbc.Driver`                    |
| Dialect                  | `org.hibernate.dialect.MySQLDialect`          |
| Credentials              | `scott` / `tiger` (dev only)                  |
