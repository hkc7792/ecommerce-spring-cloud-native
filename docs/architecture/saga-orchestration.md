# Saga Orchestration Pattern — Order Fulfillment

## 1. Problem Statement

In a microservices architecture, a single business operation — **placing an order** — spans multiple services:

1. **Order Service**: Persist the order
2. **Inventory Service**: Reserve stock for ordered items
3. **Payment Service**: Charge the customer
4. **Notification Service**: Send order confirmation

Traditional ACID transactions cannot span service boundaries. If the payment fails after inventory has been reserved, we need a reliable mechanism to **undo the reservation** (compensating transaction). The **Saga Orchestration pattern** solves this by coordinating a sequence of local transactions with compensating actions.

---

## 2. Saga: Place Order

### 2.1 Happy Path (All Steps Succeed)

```
Customer                Order Service             Inventory Service         Payment Service          Notification
   │                         │                          │                        │                       │
   │──POST /api/order───────▶│                          │                        │                       │
   │                         │                          │                        │                       │
   │                         │──1. Save Order───────────│                        │                       │
   │                         │   (status: PENDING)      │                        │                       │
   │                         │                          │                        │                       │
   │                         │──2. Reserve Stock───────▶│                        │                       │
   │                         │   POST /api/inventory/   │                        │                       │
   │                         │        reduce            │                        │                       │
   │                         │◀─── 200 OK ─────────────│                        │                       │
   │                         │                          │                        │                       │
   │                         │──3. Process Payment──────│───────────────────────▶│                       │
   │                         │   (OrderPlaceEvent       │                        │                       │
   │                         │    via Kafka)            │                        │                       │
   │                         │                          │                        │                       │
   │                         │◀── PaymentCompleted ─────│────────────────────────│                       │
   │                         │                          │                        │                       │
   │                         │──4. Update Order─────────│                        │                       │
   │                         │   (status: CONFIRMED)    │                        │                       │
   │                         │                          │                        │                       │
   │                         │──5. Send Notification────│────────────────────────│──────────────────────▶│
   │                         │   (OrderConfirmedEvent)  │                        │                       │
   │                         │                          │                        │                       │
   │◀── 201 Created ────────│                          │                        │                       │
```

### 2.2 Compensation Path (Payment Fails)

```
Customer                Order Service             Inventory Service         Payment Service
   │                         │                          │                        │
   │──POST /api/order───────▶│                          │                        │
   │                         │──1. Save Order (PENDING)─│                        │
   │                         │──2. Reserve Stock───────▶│                        │
   │                         │◀─── 200 OK ─────────────│                        │
   │                         │──3. Process Payment──────│───────────────────────▶│
   │                         │                          │                        │
   │                         │◀── PaymentFailed ────────│────────────────────────│
   │                         │                          │                        │
   │                         │──4. COMPENSATE: ─────────│                        │
   │                         │   Release Stock ────────▶│                        │
   │                         │   POST /api/inventory/   │                        │
   │                         │        release           │                        │
   │                         │◀─── 200 OK ─────────────│                        │
   │                         │                          │                        │
   │                         │──5. Update Order─────────│                        │
   │                         │   (status: CANCELLED)    │                        │
   │                         │                          │                        │
   │◀── 409 Conflict ───────│                          │                        │
   │   "Payment failed"      │                          │                        │
```

---

## 3. Order Status State Machine

```
                    ┌─────────────┐
                    │   PENDING   │ ◀── Order created, awaiting processing
                    └──────┬──────┘
                           │
                    Stock reserved?
                    ┌──────┴──────┐
                    │             │
                   Yes           No
                    │             │
            ┌───────▼──────┐  ┌──▼───────────────┐
            │  RESERVED    │  │ CANCELLED         │
            │              │  │ (Insufficient     │
            └───────┬──────┘  │  Stock)           │
                    │         └───────────────────┘
                    │
             Payment result?
            ┌───────┴──────┐
            │              │
         Success        Failed
            │              │
    ┌───────▼──────┐  ┌────▼──────────────┐
    │  CONFIRMED   │  │  CANCELLED        │
    │              │  │  + Release Stock  │
    └───────┬──────┘  │  (Compensate)     │
            │         └───────────────────┘
            │
    ┌───────▼──────┐
    │  SHIPPED     │
    └───────┬──────┘
            │
    ┌───────▼──────┐
    │  DELIVERED   │
    └──────────────┘
```

### Status Definitions

| Status       | Description                                                          | Compensating Action        |
|--------------|----------------------------------------------------------------------|----------------------------|
| `PENDING`    | Order received, persisted to database. Processing has not started.   | Cancel order, no side effects. |
| `RESERVED`   | Inventory has been reserved for all line items.                      | Release reserved stock.    |
| `CONFIRMED`  | Payment processed successfully. Order is finalized.                  | Refund payment + release stock. |
| `CANCELLED`  | Order was cancelled due to failure at any step.                      | N/A (terminal state).      |
| `SHIPPED`    | Order handed off to logistics partner.                               | Initiate return process.   |
| `DELIVERED`  | Customer received the order.                                         | Initiate return/refund.    |

---

## 4. Saga Steps — Detailed Design

### Step 1: Create Order
| Property         | Value                                        |
|------------------|----------------------------------------------|
| Service          | Order Service                                |
| Action           | Persist `Order` + `OrderLineItems` to DB     |
| Status After     | `PENDING`                                    |
| Compensate       | Delete or mark order as `CANCELLED`          |
| Transaction      | Local DB transaction                         |

### Step 2: Reserve Inventory
| Property         | Value                                        |
|------------------|----------------------------------------------|
| Service          | Inventory Service                            |
| Action           | `POST /api/inventory/reduce` — deduct stock  |
| Status After     | `RESERVED`                                   |
| Compensate       | `POST /api/inventory/release` — restore stock|
| Communication    | Synchronous HTTP via `InventoryClient`       |
| Failure Mode     | `RuntimeException` on insufficient stock     |

### Step 3: Process Payment
| Property         | Value                                        |
|------------------|----------------------------------------------|
| Service          | Payment Service                              |
| Action           | Consume `OrderPlaceEvent`, charge customer   |
| Status After     | `CONFIRMED`                                  |
| Compensate       | Issue refund via `RefundEvent`               |
| Communication    | Async via Kafka (`notificationTopic`)        |
| Failure Mode     | `PaymentFailedEvent` published to Kafka      |

### Step 4: Send Notification
| Property         | Value                                        |
|------------------|----------------------------------------------|
| Service          | Notification Service (planned)               |
| Action           | Send email/SMS confirmation to customer      |
| Status After     | No status change                             |
| Compensate       | N/A (idempotent, best-effort)                |
| Communication    | Async via Kafka (`notificationTopic`)        |

---

## 5. Kafka Topics for Saga Coordination

| Topic                    | Producer        | Consumer(s)         | Payload                    |
|--------------------------|-----------------|---------------------|----------------------------|
| `notificationTopic`     | Order Service   | Payment, Notification| `OrderPlaceEvent`          |
| `paymentTopic`          | Payment Service | Order Service        | `PaymentCompletedEvent` / `PaymentFailedEvent` |
| `inventory-release`     | Order Service   | Inventory Service    | `InventoryReleaseEvent`    |
| `order-status-updates`  | Order Service   | Notification Service | `OrderStatusChangedEvent`  |

---

## 6. Failure Handling Matrix

| Failure Point                     | Detection                  | Recovery Strategy                                        |
|-----------------------------------|----------------------------|----------------------------------------------------------|
| Order DB write fails              | SQL exception              | Return 500, no side effects                              |
| Inventory Service unreachable     | HTTP timeout / connection  | Retry 3x with backoff → cancel order                    |
| Insufficient stock                | 400/RuntimeException       | Cancel order immediately, return 409                     |
| Kafka publish fails               | Send exception             | Save to Outbox table → relay worker retries (max 5)     |
| Payment Service rejects           | `PaymentFailedEvent`       | Release inventory → cancel order → notify customer      |
| Payment Service timeout           | No event within SLA        | Scheduled job queries payment status → decide            |
| Duplicate events                  | Idempotency key            | All consumers use `orderNumber` as idempotency key      |

---

## 7. Idempotency

All saga participants must be **idempotent** — processing the same event twice must produce the same result.

### Implementation Strategy
- **Order Service**: `orderNumber` (UUID) serves as the natural idempotency key. Duplicate orders are rejected via `@Column(unique = true)`.
- **Inventory Service**: Stock reduction is tied to `orderNumber`. A processed-events table tracks which orders have already been fulfilled.
- **Payment Service**: Payment gateway's transaction ID + `orderNumber` ensure no double-charging.

---

## 8. Current Implementation Status

| Component                          | Status          | Notes                                      |
|------------------------------------|-----------------|--------------------------------------------|
| Order creation + persistence       | ✅ Implemented  | UUID-based orderNumber, line items          |
| Kafka event publishing             | ✅ Implemented  | With retry + outbox fallback               |
| Outbox pattern (relay worker)      | ✅ Implemented  | 30s polling, 5 max retries                 |
| Inventory stock check (sync HTTP)  | ✅ Implemented  | Via `InventoryClient`                      |
| Inventory stock reduction          | ✅ Implemented  | Transactional with rollback                |
| Order status state machine         | ✅ Implemented  | `OrderStatus` enum; PENDING→RESERVED→CONFIRMED/CANCELLED wired; SHIPPED/DELIVERED pending |
| Payment Service                    | ✅ Implemented  | Simulated gateway; publishes completed/failed events |
| Compensating transactions          | 🔲 Planned      | Stock release on payment failure done; payment refund pending |
| Saga orchestrator                  | 🔲 Planned      | Central coordinator in Order Service       |
| Dead letter queue                  | 🔲 Planned      | For permanently failed events              |
