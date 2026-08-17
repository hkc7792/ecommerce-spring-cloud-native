# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**ShopEase** — a cloud-native e-commerce platform built as a saga-orchestrated set of independently deployable Spring Boot services (Java 21, Spring Boot 3.4, Maven multi-module). Order placement is a cross-service saga: place order → reserve stock (sync HTTP) → process payment (Kafka) → confirm/cancel. Realtime updates flow to clients via SSE and WebSocket/STOMP.

## Commands

Build/test from the repo root (`mvn` assumed installed; no Maven wrapper):

```bash
# Build all modules (reactor builds commons first)
mvn clean install

# Run all tests
mvn test

# Build/test a single module and its reactor deps
mvn -pl order-service -am test

# Run a single test class
mvn -pl order-service -am test -Dtest=OrderServiceApplicationTests
```

Run the full stack:

```bash
# Everything (Kafka, Kafdrop, 4 MySQL DBs, all 6 services) — the canonical quick start
docker compose --env-file docker-compose.env up --build

# Infra only (Kafka + DBs), for running services locally from the IDE/jar
docker compose up kafka zookeeper kafdrop inventory-db order-db payment-db user-db
```

Run a single service locally without Docker (`spring-boot:run`), or after `mvn package`:

```bash
mvn -pl order-service spring-boot:run          # in-memory H2 + localhost:9092 Kafka by default
# or from prebuilt jars on Windows:
start-all.bat                                   # runs all 4 DB-backed services on 8081-8084
```

Useful URLs: Swagger UI per service at `/swagger-ui.html` (e.g. `http://localhost:8082/swagger-ui.html`), Kafdrop at `http://localhost:9000`, H2 console at `/h2-console`. Docker env/secrets live in `docker-compose.env` (`scott`/`tiger`, `JWT_SECRET`).

## Architecture

### Modules & ports

| Module | Host port | DB | Role |
|---|---|---|---|
| `gateway-service` | 8080 | — | Spring Cloud Gateway, single entry point |
| `inventory-service` | 8081 | inventory_db :3307 | Stock management, atomic reduce, stock SSE |
| `order-service` | 8082 | order_db :3308 | Order state machine, saga driver, outbox |
| `payment-service` | 8083 | payment_db :3309 | Simulated gateway, retry/DLQ, refunds |
| `user-service` | 8084 | user_db :3310 | Registration, JWT auth, profiles |
| `notification-service` | 8085 | — | WebSocket/STOMP fan-out hub |
| `commons` | — | — | Shared event contract + HTTP client (not a service) |

Each service has its own DB (database-per-service). Cross-service state flows through Kafka events defined in `commons`; the only synchronous inter-service call is order→inventory.

### The `commons` module — the shared contract

Everything that crosses a service boundary lives here (`artifact: common-library`): event records under `events/` (`OrderPlaceEvent`, `PaymentCompletedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent`, `OrderStatusChangedEvent`, `StockDepletedEvent`, `StockReplenishedEvent`, `LowStockAlertEvent`), the `OrderStatus` enum, `ErrorResponse`, and `client/InventoryClient` — a Spring HTTP Interface (`@HttpExchange`) backed by RestClient, used by order-service for sync stock calls.

Kafka payloads are serialized with Spring's `JsonSerializer`/`JsonDeserializer`. The serializer adds a type header by default; **exception**: payment-service disables type headers (`spring.json.use.type.headers: false`) and pins `spring.json.value.default.type` to `OrderPlaceEvent`, because it only consumes one event type. If you add an event the payment consumer must read, update that config.

### Order saga (how the pieces fit)

1. `OrderServiceImpl.placeOrder` persists the order as `PENDING`, then **synchronously** calls `InventoryClient.reduceStock`. Success → `RESERVED`; failure → order `CANCELLED`, `OutOfStockException` thrown (no event published).
2. `OrderPlaceEvent` is published to `notificationTopic` (see outbox below).
3. payment-service consumes it (`OrderEventConsumer`), simulates the gateway (₹>100,000 orders fail 20% of the time, others 5%), and publishes `PaymentCompletedEvent`/`PaymentFailedEvent` to `paymentTopic`.
4. order-service's `PaymentEventConsumer` (single listener, branches on concrete type) drives the state machine on `paymentTopic`: `RESERVED→CONFIRMED`, or `RESERVED→CANCELLED` **+ releases stock** (compensating transaction). `PaymentRefundedEvent` (from `POST /api/payment/{orderNumber}/refund`) triggers `CONFIRMED→CANCELLED` + stock release.

Every status change also pushes to the customer's SSE stream (`OrderStatusSseService`) and publishes an `OrderStatusChangedEvent` to `order-status-updates` (best-effort — failure is logged, never fails the flow).

### Realtime (two parallel paths)

- **SSE** (primary): order-service `GET /api/order/events/{customerId}`, inventory-service `GET /api/inventory/events`. Direct push, used by the order/inventory services themselves.
- **WebSocket/STOMP**: notification-service consumes `notificationTopic` + `order-status-updates` and broadcasts to `/topic/orders/{orderNumber}`. Simple in-memory broker (single-instance). Redis pub/sub multi-instance fan-out is planned but not wired.

### Reliability patterns

- **Outbox (order-service only)**: `OrderPlaceEvent` publish retries 3× with backoff, then persists to the `Out_Box` table in the same DB. `OutboxRelayWorker` polls every **2s** (code — the saga doc's "30s" is stale), marks PENDING→PROCESSING→PUBLISH, max 5 retries then FAILED. Payment-service has **no outbox** — its publish is retry-then-ERROR-log.
- **Retry + DLQ (payment-service)**: the `@RetryableTopic` listener never silently drops — 4 attempts with exponential backoff, then the message lands on `notificationTopic-dlt` (`@DltHandler` logs for operator review). Requires the `TaskScheduler` bean in `KafkaConfig`.
- **Idempotency**: consumers key on `orderNumber` (payment checks `existsByOrderNumber`; order numbers are UUIDs with a unique column) so at-least-once delivery is safe.

### Config profiles & gotchas

- **Datasource**: every DB-backed service defaults to **in-memory H2** (`jdbc:h2:mem:...`); Docker passes MySQL via `SPRING_DATASOURCE_*` env vars. `ddl-auto: update` everywhere.
- **Gateway routes** are hardcoded to Docker service names (`uri: http://inventory-service:8080`). The gateway only resolves them inside the compose network — running it standalone (IDE/jar) will fail to route.
- `inventory.service.url` defaults to `http://inventory-service:8080` in order-service; when running order-service locally, set `INVENTORY_SERVICE_URL=http://localhost:8081`.
- **Docs lag the code** — `README.md` and `docs/` still mark gateway, payment, and user services as "planned". Code is the source of truth.

## Conventions (from `.gemini/instructions.md`)

- Per-service package layout: `controller/`, `service/` + `service/impl/`, `repository/`, `entities/`, `dto/`, `events/`, `exceptions/` (+ `@ControllerAdvice` handlers), `config/`, `realtime/`.
- DTOs are Java **records** with validation in compact constructors; `BigDecimal` for money, never float/double; business identifiers (`orderNumber`, `skuCode`) `@Column(unique = true)`; entities built with Lombok `@Builder`.
- `@Transactional` on service methods (not controllers); `readOnly = true` for reads; `rollbackFor = Exception.class` for writes.
- Inter-service HTTP clients are `@HttpExchange` interfaces in `commons` (RestClient-backed), not per-service clients.
- Naming: DTO suffix `Request`/`Response`, services interface + `Impl`, events past-tense (`OrderPlaceEvent`), endpoints `/api/<service>/<resource>`.
- Tests: `@SpringBootTest` application-context smoke tests (one per service); JUnit 5.
- Git: feature branches `feature/<service>/<short-desc>`; conventional-commit messages (`feat(order): ...`, `docs: ...`).

Relevant docs: `docs/architecture/saga-orchestration.md` (state machine + failure matrix), `docs/architecture/system-overview.md`, per-service API docs under `docs/api/`, `docs/testing/end-to-end-testing.md` (flow-by-flow manual test guide).
