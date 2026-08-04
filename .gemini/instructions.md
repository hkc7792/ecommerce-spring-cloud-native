# Ecommerce Spring Cloud Native — Coding Guidelines

## Project Overview

This is a production-grade, cloud-native e-commerce platform built with a **microservices architecture** using **Spring Boot 3.4.x** and **Java 21**. The platform handles the full order lifecycle — from browsing and cart management, through payment processing and inventory reservation, to order fulfillment and delivery tracking.

---

## Technology Stack

| Layer              | Technology                                                     |
|--------------------|----------------------------------------------------------------|
| Language           | Java 21 (LTS) — use records, sealed classes, pattern matching |
| Framework          | Spring Boot 3.4.x                                              |
| Build Tool         | Maven (multi-module parent POM)                                |
| Database           | MySQL 8.x (per-service databases)                              |
| Messaging          | Apache Kafka (Confluent Platform)                              |
| Containerization   | Docker + Docker Compose                                        |
| ORM                | Spring Data JPA / Hibernate                                    |
| Inter-Service Comm | Spring HTTP Interface (`@HttpExchange`) for sync calls         |
| Resilience         | Spring Retry (`@Retryable`, `@Recover`)                        |
| Observability      | Spring Boot Actuator                                           |
| Shared Libraries   | `commons` module (DTOs, HTTP clients, shared responses)        |

---

## Architecture Principles

### 1. Database-per-Service
Each microservice owns its database schema. **No shared databases.** Cross-service data access happens exclusively through APIs or events.

- `order-service` → `order_db` (MySQL, port 3308)
- `inventory-service` → `inventory_db` (MySQL, port 3307)
- `payment-service` → `payment_db` (MySQL, port 3309) *(planned)*
- `user-service` → `user_db` (MySQL, port 3310) *(planned)*

### 2. Event-Driven Communication
Services communicate asynchronously via **Kafka topics** for eventual consistency. Use synchronous HTTP calls only when an immediate response is required (e.g., stock availability checks).

### 3. Outbox Pattern
All domain events that must be published to Kafka are first persisted to a local `Out_Box` table within the same transaction as the business operation. A scheduled relay worker (`OutboxRelayWorker`) polls pending events and publishes them to Kafka with retry logic. This guarantees **at-least-once delivery** without distributed transactions.

### 4. Saga Orchestration (Target Architecture)
Multi-step business transactions (e.g., Place Order → Reserve Inventory → Process Payment) are coordinated using the **Saga Orchestration pattern** with compensating transactions for rollback.

---

## Coding Standards

### Package Structure (per service)
```
com.ecommerce.<service-name>/
├── controller/         # REST controllers (@RestController)
├── service/            # Service interfaces
│   └── impl/           # Service implementations
├── repository/         # Spring Data JPA repositories
├── entities/           # JPA entities (@Entity)
├── dto/                # Data Transfer Objects (prefer Java records)
├── events/             # Domain event records (for Kafka)
├── exceptions/         # Custom exceptions + @ControllerAdvice handlers
├── config/             # Configuration classes (@Configuration)
└── client/             # HTTP interface clients (in commons module)
```

### Naming Conventions
- **Entities**: Singular noun (`Order`, `Inventory`, `Payment`)
- **DTOs**: Suffix with `Dto` or `Request`/`Response` (`OrderRequest`, `InventoryResponse`)
- **Services**: Interface + `Impl` suffix (`OrderService` → `OrderServiceImpl`)
- **Controllers**: Suffix with `Controller` (`OrderController`)
- **Events**: Past-tense verb + noun (`OrderPlaceEvent`, `PaymentCompletedEvent`)
- **REST endpoints**: `/api/<service-name>/<resource>` (lowercase, kebab-case for multi-word)

### Entity Design Rules
- Always use `@GeneratedValue(strategy = GenerationType.IDENTITY)` for auto-increment IDs.
- Use `BigDecimal` for all monetary values — **never** `float` or `double`.
- Use `@Column(nullable = false)` for required fields.
- Use `@Builder` pattern from Lombok for entity construction.
- Business identifiers (e.g., `orderNumber`, `skuCode`) must be `@Column(unique = true)`.

### DTO Design Rules
- Prefer Java **records** for immutable DTOs.
- Add validation in compact constructors using Jakarta Validation annotations (`@NotBlank`, `@NotNull`, `@Min`).
- Throw `IllegalArgumentException` for business rule violations in constructors.

### Transaction Management
- Use `@Transactional` on service methods, not controllers.
- Use `@Transactional(readOnly = true)` for read-only operations.
- Use `@Transactional(rollbackFor = Exception.class)` for write operations to ensure rollback on checked exceptions.

### Error Handling
- Use `@ControllerAdvice` with `@ExceptionHandler` for global exception handling.
- Return structured `ErrorResponse` objects (defined in `commons`).
- Use appropriate HTTP status codes (400 for validation, 404 for not found, 409 for conflicts, 500 for server errors).

### Inter-Service Communication
- Define HTTP clients as interfaces in the `commons` module using `@HttpExchange`.
- Use `RestClient` (Spring 6.1+) as the underlying HTTP client.
- Apply `@Retryable` for transient failure handling on external calls.

---

## Docker & Infrastructure

### Service Ports
| Service             | Host Port | Container Port | Debug Port |
|---------------------|-----------|----------------|------------|
| Kafka               | 9092      | 9092           | —          |
| Kafdrop (Kafka UI)  | 9000      | 9000           | —          |
| inventory-db        | 3307      | 3306           | —          |
| order-db            | 3308      | 3306           | —          |
| inventory-service   | 8081      | 8080           | 5001       |
| order-service       | 8082      | 8080           | 5002       |

### Docker Compose Rules
- All services must define a `healthcheck` before dependents start.
- Use `depends_on` with `condition: service_healthy` for startup ordering.
- Store secrets in `docker-compose.env` (not committed to production repos).
- Use named volumes for database persistence.

---

## Git & Branching
- **Main branch**: `main` — always deployable.
- **Feature branches**: `feature/<service-name>/<short-description>`
- **Commit messages**: `<type>(<scope>): <description>` (e.g., `feat(order): add outbox pattern for event publishing`)

---

## Testing Standards
- Unit tests with JUnit 5 + Mockito.
- Integration tests with `@SpringBootTest` and Testcontainers for MySQL/Kafka.
- Test file naming: `<ClassName>Tests.java` (e.g., `OrderServiceApplicationTests.java`).
- Minimum 80% code coverage target for service layer.
