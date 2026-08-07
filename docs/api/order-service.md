# Order Service — API Documentation

## Service Overview

| Property         | Value                                                |
|------------------|------------------------------------------------------|
| Service Name     | `order-service`                                       |
| Base URL         | `http://localhost:8082/api/order`                      |
| Database         | `order_db` (MySQL 8.x, port 3308)                    |
| Package          | `com.ecommerce.order`                                 |
| Spring Boot      | 3.4.1                                                 |
| Java             | 21                                                    |

---

## Endpoints

### 1. Place Order

Creates a new order with one or more line items. Generates a UUID-based order number, calculates the total, reserves stock synchronously via `InventoryClient.reduceStock`, and publishes an `OrderPlaceEvent` to Kafka. The client immediately receives the order number + initial status so it can subscribe to realtime updates.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/order`                                       |
| Auth        | None (planned: JWT Bearer token)                   |
| Status      | `201 Created`                                      |

#### Request Body

```json
{
  "customerId": 12345,
  "orderLineItemsDtoList": [
    {
      "skuCode": "IPHONE-15-128GB",
      "price": 79999.00,
      "quantity": 1
    },
    {
      "skuCode": "AIRPODS-PRO-2",
      "price": 24999.00,
      "quantity": 2
    }
  ]
}
```

#### Request Schema

| Field                         | Type             | Required | Validation                          |
|-------------------------------|------------------|----------|-------------------------------------|
| `customerId`                  | `Long`           | Yes      | Must be > 0                         |
| `orderLineItemsDtoList`       | `List<OrderItemDto>` | Yes  | Must not be empty                   |
| `orderLineItemsDtoList[].skuCode`  | `String`    | Yes      | Must not be blank                   |
| `orderLineItemsDtoList[].price`    | `BigDecimal`| Yes      | Must be > 0                         |
| `orderLineItemsDtoList[].quantity` | `Integer`   | Yes      | Must be > 0                         |

#### Responses

**201 Created** — Order placed successfully
```json
{
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "RESERVED"
}
```

| Field         | Type     | Description                                     |
|---------------|----------|-------------------------------------------------|
| `orderNumber` | `String` | UUID business identifier, used as the SSE + STOMP subscription key |
| `status`      | `Enum`   | Initial state after placement (`RESERVED`, or `CANCELLED` if stock reservation failed) |

**400 Bad Request** — Validation failure
```json
{
  "status": 400,
  "message": "Customer ID must be greater than zero",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

**400 Bad Request** — Empty line items
```json
{
  "status": 400,
  "message": "Order line items list cannot be empty",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

**500 Internal Server Error** — Server-side failure
```json
{
  "status": 500,
  "message": "An unexpected error occurred",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8082/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 12345,
    "orderLineItemsDtoList": [
      {
        "skuCode": "IPHONE-15-128GB",
        "price": 79999.00,
        "quantity": 1
      },
      {
        "skuCode": "AIRPODS-PRO-2",
        "price": 24999.00,
        "quantity": 2
      }
    ]
  }'
```

---

### 2. Subscribe to Order Status (SSE)

Real-time stream of order status transitions for a customer. The frontend opens an EventSource here and receives an `order-status` event on every state change. The optional `orderNumber` query param replays the current status immediately, so a client connecting after placing an order still gets the latest state.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `GET`                                              |
| Path        | `/api/order/events/{customerId}`                   |
| Auth        | None (planned: JWT Bearer token)                   |
| Status      | `200 OK`                                           |
| Content-Type| `text/event-stream`                                |

#### Path/Query Parameters

| Parameter     | Type   | Required | Description                                        |
|---------------|--------|----------|----------------------------------------------------|
| `customerId`  | `Long` | Yes      | Customer whose order stream to subscribe to        |
| `orderNumber` | `String` | No    | Replays this order's current status on connect     |

#### Event Name

| Event         | Payload                            | Fired When                          |
|---------------|------------------------------------|-------------------------------------|
| `connected`   | `subscribed`                       | Connection established (heartbeat)  |
| `order-status`| `OrderStatusChangedEvent`          | Any status transition               |

#### cURL Example
```bash
curl -N "http://localhost:8082/api/order/events/12345?orderNumber=a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

---

## Order Status State Machine

Orders move through a fixed set of states (`OrderStatus` enum in `commons`). Every transition persists the new status, pushes an `OrderStatusChangedEvent` over SSE, and publishes it to Kafka (`order-status-updates`).

```
PENDING ──stock reserved──▶ RESERVED ──payment ok──▶ CONFIRMED ──▶ SHIPPED ──▶ DELIVERED
    │                            │                        │
    └──(insufficient stock)──────┘                        └──refunded (payment refund)──▶ CANCELLED
                                 └──payment failed / cancelled──▶ CANCELLED
```

| Status       | Description                                                          |
|--------------|----------------------------------------------------------------------|
| `PENDING`    | Order persisted; processing not started                              |
| `RESERVED`   | Stock reserved for all line items                                    |
| `CONFIRMED`  | Payment processed successfully; order finalized                      |
| `SHIPPED`    | Handed off to logistics partner *(enum defined, transition not wired)*|
| `DELIVERED`  | Customer received the order *(enum defined, transition not wired)*    |
| `CANCELLED`  | Terminal state after stock or payment failure, or a refunded payment |

The transitions to `CONFIRMED` and `CANCELLED` are driven by the payment result consumed from `paymentTopic` (see below). A refunded payment also drives a `CONFIRMED → CANCELLED` transition and releases the reserved stock.

---

## Domain Model

### Order Entity

| Column              | Type          | Constraints                  | Description                       |
|---------------------|---------------|------------------------------|-----------------------------------|
| `id`                | `BIGINT`      | PK, AUTO_INCREMENT           | Internal surrogate key            |
| `order_number`      | `VARCHAR(36)` | UNIQUE, NOT NULL             | UUID-based business identifier    |
| `customer_id`       | `BIGINT`      | NOT NULL                     | References the customer who placed the order |
| `total_amount`      | `DECIMAL`     | NOT NULL                     | Sum of (price × quantity) for all items |
| `status`            | `ENUM`        | NOT NULL, Default: `PENDING` | `PENDING`, `RESERVED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |

### OrderLineItems Entity

| Column              | Type          | Constraints                  | Description                       |
|---------------------|---------------|------------------------------|-----------------------------------|
| `id`                | `BIGINT`      | PK, AUTO_INCREMENT           | Internal surrogate key            |
| `order_id`          | `BIGINT`      | FK → `orders.id`             | Parent order reference            |
| `sku_code`          | `VARCHAR`     | —                            | Product variant identifier (links to Inventory) |
| `price`             | `DECIMAL`     | —                            | Unit price at time of order       |
| `quantity`          | `INTEGER`     | —                            | Number of units ordered           |

### OutBoxEvent Entity

| Column              | Type            | Constraints                  | Description                       |
|---------------------|-----------------|------------------------------|-----------------------------------|
| `id`                | `UUID`          | PK, AUTO_GENERATED           | Unique event identifier           |
| `aggregate_type`    | `VARCHAR`       | NOT NULL                     | Domain entity type (`ORDER`)      |
| `aggregate_id`      | `VARCHAR`       | NOT NULL                     | Business ID (`orderNumber`)       |
| `event_type`        | `VARCHAR`       | NOT NULL                     | Event name (`ORDER_PLACED`)       |
| `payload`           | `JSONB`         | —                            | Serialized event as JSON          |
| `retry_count`       | `INTEGER`       | Default: 0                   | Number of publish attempts        |
| `status`            | `ENUM`          | —                            | `PENDING`, `PROCESSING`, `PUBLISH`, `FAILED` |
| `created_at`        | `TIMESTAMP`     | Auto-generated               | Event creation time               |
| `last_modified_at`  | `TIMESTAMP`     | Auto-updated                 | Last retry attempt time           |

---

## Kafka Events

### OrderPlaceEvent (Published)

**Topic**: `notificationTopic`  
**Key**: `orderNumber`  
**Serialization**: JSON

```json
{
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "totalAmount": 129997.00,
  "orderTime": "2026-08-04T12:30:00Z",
  "items": [
    {
      "skuCode": "IPHONE-15-128GB",
      "price": 79999.00,
      "quantity": 1
    },
    {
      "skuCode": "AIRPODS-PRO-2",
      "price": 24999.00,
      "quantity": 2
    }
  ]
}
```

| Field         | Type              | Description                              |
|---------------|-------------------|------------------------------------------|
| `orderNumber` | `String`          | UUID order identifier                    |
| `customerId`  | `Long`            | Customer who placed the order            |
| `totalAmount` | `BigDecimal`      | Total order value                        |
| `orderTime`   | `Instant` (ISO-8601) | Timestamp of order placement          |
| `items`       | `List<OrderLineItem>` | Line items (skuCode, price, quantity) |

### OrderStatusChangedEvent (Published)

**Topic**: `order-status-updates`  
**Key**: `orderNumber`  
**Serialization**: JSON

Published on every status transition (including the initial PENDING → RESERVED and any RESERVED → CONFIRMED / RESERVED → CANCELLED driven by payment results). Also pushed to open SSE connections as the primary realtime channel.

```json
{
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "previousStatus": "PENDING",
  "currentStatus": "RESERVED",
  "changedAt": "2026-08-04T12:30:05Z"
}
```

| Field            | Type           | Description                              |
|------------------|----------------|------------------------------------------|
| `orderNumber`    | `String`       | UUID order identifier                    |
| `customerId`     | `Long`         | Customer who placed the order            |
| `previousStatus` | `OrderStatus`  | State before the transition              |
| `currentStatus`  | `OrderStatus`  | New state                                |
| `changedAt`      | `Instant` (ISO-8601) | Transition timestamp                |

### Payment Result Consumption (Consumed)

**Topic**: `paymentTopic`  
**Consumer Group**: `order-service-group`

`PaymentEventConsumer` listens for payment outcomes and drives the state machine:

| Event                  | Action                                              |
|------------------------|-----------------------------------------------------|
| `PaymentCompletedEvent`| `RESERVED` → `CONFIRMED`                            |
| `PaymentFailedEvent`   | `RESERVED` → `CANCELLED` + release stock via `InventoryClient.addStock` (saga compensation) |
| `PaymentRefundedEvent` | `CONFIRMED` → `CANCELLED` + release stock via `InventoryClient.addStock` (saga compensation) |

---

## Reliability Mechanisms

### Retry Policy (Kafka Publishing)
```
Attempt 1 → immediate
Attempt 2 → wait 2,000 ms
Attempt 3 → wait 4,000 ms (2,000 × attempt)
All failed → save to Outbox table for the relay worker
```
Publishing uses a blocking `.get(5, TimeUnit.SECONDS)` so a down Kafka actually triggers the fallback instead of failing asynchronously.

### Outbox Relay Worker
```
Polling interval: 2 seconds (near-realtime)
Max retries: 5
Status flow: PENDING → PROCESSING → PUBLISH (success) or PENDING (retry) or FAILED (exhausted)
Kafka send timeout: 5 seconds (blocking)
```

---

## Dependencies

| Dependency            | Usage                                            |
|-----------------------|--------------------------------------------------|
| `inventory-service`   | Sync HTTP call via `InventoryClient` for stock check and reduction |
| `kafka`               | Async event publishing for downstream consumers  |
| `order_db`            | Primary data store for orders and outbox events   |

---

## Configuration

### Application Properties (Key Settings)

| Property                                  | Value                | Description                |
|-------------------------------------------|----------------------|----------------------------|
| `server.port`                             | `8080`               | Container port             |
| `spring.datasource.url`                   | `jdbc:mysql://...`   | Injected via Docker env    |
| `spring.kafka.bootstrap-servers`          | `kafka:29092`        | Kafka broker address       |
| `inventory.service.url`                   | `http://inventory-service:8080` | Inventory Service base URL |

### Docker Ports
| Type             | Host     | Container |
|------------------|----------|-----------|
| Application      | `8082`   | `8080`    |
| Remote Debug     | `5002`   | `5000`    |
