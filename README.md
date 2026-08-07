# ShopEase — Cloud-Native E-Commerce Platform

A microservices e-commerce platform with **realtime order tracking, live inventory, and WebSocket/SSE push** — built as a saga-orchestrated set of independently deployable Spring Boot services.

---

## 1. Tech Stack

| Layer            | Technology                                                                    |
|------------------|-------------------------------------------------------------------------------|
| **Language**     | Java 21                                                                       |
| **Framework**    | Spring Boot 3.4.1 (Web MVC, Data JPA, Security, Validation)                  |
| **Gateway**      | Spring Cloud Gateway (planned entry point)                                    |
| **Messaging**    | Apache Kafka — event-driven saga + realtime event streaming                   |
| **Realtime**     | WebSocket/STOMP (notification hub) + Server-Sent Events (SSE push)            |
| **Database**     | MySQL 8 per-service (H2 for local dev), JPA/Hibernate                         |
| **Cache/Relay**  | Redis (multi-instance pub/sub fan-out)                                        |
| **Auth**         | JWT (OAuth2 Resource Server) for user service                                 |
| **Build**        | Maven multi-module, Docker Compose orchestration                              |
| **Docs/API**     | springdoc OpenAPI (Swagger UI) per service                                    |
| **Utilities**    | Lombok, SLF4J, RestClient HTTP interfaces                                     |

---

## 2. Architecture

```
                        ┌─────────────────────────────┐
                        │    API Gateway              │  http://localhost:8080
                        │  routes /api/* + /ws        │
                        └──────┬──────┬──────┬─────────┘
        WebSocket/STOMP        │      │      │
   ws://localhost:8080/ws      │      │      │
   (direct: 8085)              │      │      │
        ┌──────────────────────┼──────┼──────┼───────────────┐
        ▼                      ▼      ▼      ▼               ▼
┌───────────────┐  ┌───────────────┐ ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐
│ Order Service │  │Inventory Svc  │ │Payment Svc  │ │ User Service │ │ Notification Svc  │
│  :8082        │  │  :8081        │ │  :8083      │ │  :8084       │ │  :8085 (WS hub)  │
│ order_db :3308│  │ inventory_db  │ │ payment_db  │ │  user_db     │ │  (no database)   │
│               │  │  :3307        │ │  :3309      │ │  :3310       │ │                  │
└───────┬───────┘  └──────┬────────┘ └─────┬───────┘ └──────┬───────┘ └──────────────────┘
        │                 │               │                 │
        └─────── Kafka event bus (notificationTopic / paymentTopic / order-status-updates
                / inventory-stock-events / inventory-alerts)  +  MySQL + Redis ────────┘
```

**Kafka topics** (single source of truth for cross-service state):

| Topic                     | Payload                                              | Producer            | Consumers                              |
|---------------------------|------------------------------------------------------|---------------------|----------------------------------------|
| `notificationTopic`       | `OrderPlaceEvent`                                    | order-service       | payment-service, notification-service  |
| `paymentTopic`            | `PaymentCompletedEvent` / `PaymentFailedEvent` / `PaymentRefundedEvent` | payment-service     | order-service                          |
| `order-status-updates`    | `OrderStatusChangedEvent`                            | order-service       | notification-service                   |
| `inventory-stock-events`  | `StockDepletedEvent` / `StockReplenishedEvent`       | inventory-service   | —                                      |
| `inventory-alerts`        | `LowStockAlertEvent`                                 | inventory-service   | —                                      |

---

## 3. Functional Requirements

- **Order lifecycle with realtime status** — order moves `PENDING → RESERVED → CONFIRMED → (SHIPPED → DELIVERED)` or `→ CANCELLED`, and every transition is pushed to the customer instantly.
- **Atomic inventory reservation** — stock is reserved at order placement via a guarded atomic update, preventing overselling during flash sales.
- **Saga-orchestrated payment** — payment runs as a saga step; success confirms the order, failure cancels it and releases reserved stock (compensating transaction).
- **Refund flow** — a completed payment can be refunded (`POST /api/payment/{orderNumber}/refund`); the order is cancelled and its stock released via a `PaymentRefundedEvent` compensating transaction.
- **Live inventory visibility** — stock depletion / replenishment / low-stock alerts stream to browsers in realtime.
- **Realtime push hub** — order placed + status change events are broadcast over WebSocket (STOMP) to every subscribed client.
- **User accounts & auth** — registration, JWT login, profile and address management.
- **Payment history** — per-order and per-customer payment records (simulated gateway).

## 4. Non-Functional Requirements

- **Realtime** — order status and stock updates reach clients within ~100ms of the state change (SSE + WebSocket push, no polling).
- **Reliability / no silent loss** — Kafka publishing has retry + outbox fallback; payment consumption has exponential-backoff retries and a dead-letter topic (`-dlt`) for operator review.
- **Consistency** — per-service databases with a single shared event contract (commons module); idempotent consumers keyed by `orderNumber`.
- **Scalability** — stateless services, horizontally scalable; Kafka partitions + Redis pub/sub enable multi-instance fan-out.
- **Concurrency-safe** — atomic SQL `UPDATE ... WHERE quantity >= :qty` prevents inventory overselling under peak load.
- **Observability** — SLF4J structured logging, springdoc OpenAPI per service.
- **Testability** — each service carries an application-context smoke test; H2 in-memory profiles for local dev.

---

## 5. Services & Ports

| Service               | Port (host:container) | Database      | One-line description                                        |
|-----------------------|-----------------------|---------------|-------------------------------------------------------------|
| **gateway-service**   | `8080:8080`           | — (none)      | Spring Cloud Gateway — single entry point routing `/api/*` + `/ws` |
| **order-service**     | `8082:8080`           | order_db :3308| Places orders, reserves stock, tracks the order state machine|
| **inventory-service** | `8081:8080`           | inventory_db  | Manages stock with atomic reductions + realtime stock SSE   |
| **payment-service**   | `8083:8080`           | payment_db    | Processes payments (simulated gateway) with retry + DLQ     |
| **user-service**      | `8084:8080`           | user_db       | Registration, JWT auth, profiles, addresses                 |
| **notification-service** | `8085:8080`        | — (none)      | WebSocket/STOMP hub that fans order events out to clients   |
| Kafka                 | `9092` / Kafdrop `9000` | —            | Event bus for the saga                                      |
| Redis                 | `6379`                | —             | Pub/sub relay for multi-instance WebSocket fan-out          |

---

## 6. Realtime Endpoints

| Endpoint                                          | Service          | Type    | What it pushes                                   |
|---------------------------------------------------|------------------|---------|--------------------------------------------------|
| `GET /api/order/events/{customerId}`              | order-service    | SSE     | `order-status` events for a customer's orders    |
| `GET /api/inventory/events`                       | inventory-service| SSE     | `stock-update` events for all stock changes      |
| `ws://localhost:8085/ws` (STOMP, `/topic/orders/{orderNumber}`) | notification-service | WebSocket | order placed + status change broadcasts |

---

## 7. Quick Start

```bash
# 1. Start the full stack (Kafka, MySQL, Redis, all services)
docker compose --env-file docker-compose.env up --build

# 2. Seed some inventory
curl -X POST http://localhost:8081/api/inventory/add -H "Content-Type: application/json" \
     -d '{"skuCode":"IPHONE-15-128GB","quantity":100}'

# 3. Watch realtime stock updates
curl -N http://localhost:8081/api/inventory/events

# 4. Place an order and watch it flow PENDING -> RESERVED -> CONFIRMED
curl -N http://localhost:8082/api/order/events/1 &
curl -X POST http://localhost:8082/api/order -H "Content-Type: application/json" \
     -d '{"customerId":1,"orderLineItemsDtoList":[{"skuCode":"IPHONE-15-128GB","price":79999.00,"quantity":1}]}'

# 5. Check Swagger UI per service, e.g. http://localhost:8082/swagger-ui.html
```

---

## 8. Project Docs

| Document                                              | Covers                                    |
|-------------------------------------------------------|-------------------------------------------|
| [System Overview](docs/architecture/system-overview.md) | High-level architecture & topology      |
| [Saga Orchestration](docs/architecture/saga-orchestration.md) | Order-fulfillment saga + compensating txs |
| [API: Order](docs/api/order-service.md)               | Order endpoints, events, reliability     |
| [API: Inventory](docs/api/inventory-service.md)       | Inventory endpoints, atomicity, stock events |
| [API: Payment](docs/api/payment-service.md)           | Payment endpoints, retry/DLQ, refund     |
| [API: User](docs/api/user-service.md)                 | Auth + profile endpoints                 |
| [BRD](docs/requirements/brd-ecommerce.md)             | Business requirements                    |
| [User Stories](docs/requirements/user-stories.md)     | Story-by-story acceptance criteria       |
