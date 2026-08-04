# System Architecture Overview

## 1. Introduction

**ShopEase** is a cloud-native e-commerce platform designed to handle the complete online shopping lifecycle — product discovery, cart management, order placement, payment processing, inventory management, and delivery fulfillment. The system is architected as a distributed microservices platform, optimized for independent deployability, horizontal scalability, and fault tolerance.

### Business Context
The platform serves a mid-to-large scale B2C online marketplace with:
- **50,000+ SKUs** across categories (electronics, fashion, home, groceries)
- **~10,000 concurrent users** during peak hours (flash sales, festivals)
- **~2,000 orders/minute** target throughput during peak traffic
- **99.9% uptime SLA** for core ordering flow

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        API Gateway (planned)                         │
│                   (Rate Limiting, Auth, Routing)                     │
└──────────┬──────────────┬──────────────┬──────────────┬──────────────┘
           │              │              │              │
    ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼───────┐ ┌───▼────────┐
    │   Order     │ │  Inventory │ │  Payment   │ │   User     │
    │  Service    │ │  Service   │ │  Service   │ │  Service   │
    │  (8082)     │ │  (8081)    │ │  (8083)    │ │  (8084)    │
    └──────┬──────┘ └─────┬──────┘ └────┬───────┘ └───┬────────┘
           │              │              │              │
    ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼───────┐ ┌───▼────────┐
    │  order_db   │ │inventory_db│ │ payment_db │ │  user_db   │
    │  (MySQL)    │ │  (MySQL)   │ │  (MySQL)   │ │  (MySQL)   │
    │  Port:3308  │ │  Port:3307 │ │  Port:3309 │ │  Port:3310 │
    └─────────────┘ └────────────┘ └────────────┘ └────────────┘

                    ┌──────────────────────────┐
                    │      Apache Kafka        │
                    │   (Event Bus / Broker)   │
                    │      Port: 9092          │
                    └──────────────────────────┘
                    ┌──────────────────────────┐
                    │       Kafdrop UI         │
                    │      Port: 9000          │
                    └──────────────────────────┘
```

---

## 3. Service Decomposition

### 3.1 Order Service (`order-service`)
**Responsibility**: Manages the complete order lifecycle from placement to fulfillment.

| Aspect          | Detail                                                                 |
|-----------------|------------------------------------------------------------------------|
| Base URL        | `http://localhost:8082/api/order`                                       |
| Database        | `order_db` on MySQL (port 3308)                                        |
| Key Entities    | `Order`, `OrderLineItems`, `OutBoxEvent`                               |
| Publishes       | `OrderPlaceEvent` → `notificationTopic` (Kafka)                       |
| Consumes        | Inventory Service (sync HTTP for stock check)                          |
| Patterns        | Outbox Pattern, Retry with exponential backoff                         |

**Core Flow**:
1. Receives `OrderRequest` with customer ID and line items
2. Validates and persists the order with a UUID-based `orderNumber`
3. Publishes `OrderPlaceEvent` to Kafka for downstream consumers
4. On Kafka failure: saves event to `Out_Box` table → relay worker retries every 30s

### 3.2 Inventory Service (`inventory-service`)
**Responsibility**: Tracks real-time stock levels for all SKUs and handles stock reservations/deductions.

| Aspect          | Detail                                                                 |
|-----------------|------------------------------------------------------------------------|
| Base URL        | `http://localhost:8081/api/inventory`                                   |
| Database        | `inventory_db` on MySQL (port 3307)                                    |
| Key Entities    | `Inventory` (skuCode, quantity, status)                                |
| Publishes       | *(planned)* `StockDepletedEvent`, `StockReplenishedEvent`              |
| Consumed By     | Order Service (sync HTTP via `InventoryClient`)                        |

**Core Operations**:
- **Stock Check** (`GET /items?skuCode=...`): Returns availability for a list of SKUs
- **Add Stock** (`POST /add`): Upserts inventory — creates new or increments existing
- **Reduce Stock** (`POST /reduce`): Atomically decrements stock with validation; throws on insufficient stock

### 3.3 Payment Service (`payment-service`) — *Planned*
**Responsibility**: Handles payment processing, refunds, and payment method management.

| Aspect          | Detail                                                                 |
|-----------------|------------------------------------------------------------------------|
| Base URL        | `http://localhost:8083/api/payment`                                     |
| Database        | `payment_db` on MySQL (port 3309)                                      |
| Key Entities    | `Payment`, `PaymentTransaction`, `Refund`                              |
| Consumes        | `OrderPlaceEvent` from `notificationTopic`                             |
| Publishes       | `PaymentCompletedEvent`, `PaymentFailedEvent`                          |

### 3.4 User Service (`user-service`) — *Planned*
**Responsibility**: User registration, authentication, profile management, and address book.

| Aspect          | Detail                                                                 |
|-----------------|------------------------------------------------------------------------|
| Base URL        | `http://localhost:8084/api/user`                                        |
| Database        | `user_db` on MySQL (port 3310)                                         |
| Key Entities    | `User`, `Address`, `UserPreferences`                                   |
| Auth            | JWT-based authentication with Spring Security                          |

---

## 4. Communication Patterns

### 4.1 Synchronous (HTTP)
Used when the caller **needs an immediate response** to continue processing.

| Caller          | Callee           | Protocol       | Purpose                     |
|-----------------|------------------|----------------|-----------------------------|
| Order Service   | Inventory Service| HTTP (REST)    | Check stock availability    |
| Order Service   | Inventory Service| HTTP (REST)    | Reduce stock after order    |

Implementation: Spring HTTP Interface (`@HttpExchange`) defined in `commons` module via `InventoryClient`.

### 4.2 Asynchronous (Kafka Events)
Used for **eventual consistency** and decoupling services.

| Producer        | Topic              | Consumer(s)        | Event                  |
|-----------------|--------------------|--------------------|------------------------|
| Order Service   | `notificationTopic`| Payment Service    | `OrderPlaceEvent`      |
| Order Service   | `notificationTopic`| Notification Svc   | `OrderPlaceEvent`      |
| Payment Service | `paymentTopic`     | Order Service      | `PaymentCompletedEvent`|

### 4.3 Outbox Pattern (Guaranteed Delivery)
```
┌────────────────────────────────────────────────────────────────┐
│                     Order Service                               │
│                                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────────────────┐       │
│  │ Business │───▶│  Save    │───▶│  Same DB Transaction │       │
│  │  Logic   │    │  Order   │    │  Save to Out_Box     │       │
│  └──────────┘    └──────────┘    └──────────┬───────────┘       │
│                                              │                   │
│  ┌──────────────────────────┐               │                   │
│  │  OutboxRelayWorker       │◀──────────────┘                   │
│  │  (Scheduled @30s)        │                                    │
│  │  PENDING → PROCESSING   │──────▶  Kafka                     │
│  │  Success → PUBLISH       │                                    │
│  │  Fail → Retry (max 5)   │                                    │
│  │  Max retries → FAILED   │                                    │
│  └──────────────────────────┘                                    │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. Infrastructure Components

### 5.1 Apache Kafka
- **Broker**: Single-node (Confluent Platform image)
- **Zookeeper**: Required for Kafka cluster coordination
- **Listeners**: Dual-listener configuration — `LISTENER_DOCKER` (internal, port 29092) and `LISTENER_HOST` (external, port 9092)
- **Monitoring**: Kafdrop UI at `http://localhost:9000`

### 5.2 MySQL Databases
- **Version**: MySQL 8.x
- **Isolation**: Each service has a dedicated database instance in Docker
- **Credentials**: Managed via `docker-compose.env` (`scott`/`tiger` for dev)
- **Persistence**: Docker named volumes (`order-data`, `inventory-data`)
- **Health Checks**: `mysqladmin ping` with 5s intervals, 10 retries

### 5.3 Docker Compose
All services are orchestrated via `docker-compose.yml` with:
- **Custom bridge network** (`ecommerce-network`) for inter-service DNS resolution
- **Health-check gated startup** — services only start after their database is healthy
- **Remote debugging** enabled via `JAVA_TOOL_OPTIONS` on port 5000 (mapped to host ports 5001/5002)
- **Environment variable substitution** from `docker-compose.env`

---

## 6. Shared Commons Module

The `commons` module (`com.ecommerce.commons`) contains cross-cutting code shared between services:

| Package                    | Contents                                          |
|----------------------------|---------------------------------------------------|
| `commons.client`           | `InventoryClient` — HTTP interface for inventory  |
| `commons.requests`         | `InventoryRequest` — validated input DTOs         |
| `commons.responses`        | `InventoryResponse`, `ErrorResponse`              |

This module is published as a Maven dependency (`common-library:0.0.1-SNAPSHOT`) and consumed by services that need inter-service communication.

---

## 7. Cross-Cutting Concerns

### Observability
- **Spring Boot Actuator** enabled on all services (`/actuator/health`, `/actuator/info`)
- *(Planned)* Distributed tracing with Micrometer + Zipkin
- *(Planned)* Centralized logging with ELK stack (Elasticsearch, Logstash, Kibana)

### Resilience
- **Spring Retry**: Configurable retry policies with exponential backoff (`maxAttempts=3`, `delay=2000ms`, `multiplier=2`)
- **Recovery**: `@Recover` methods for graceful degradation when retries exhaust
- *(Planned)* Circuit breaker with Resilience4j

### Security *(Planned)*
- JWT-based authentication via User Service
- API Gateway for centralized auth enforcement
- Service-to-service authentication with mutual TLS

---

## 8. Deployment Topology

### Local Development
```bash
# Start all infrastructure + services
docker compose --env-file docker-compose.env up --build

# Individual service development (run from IDE, DB in Docker)
docker compose up inventory-db order-db kafka zookeeper kafdrop
```

### Production *(Target)*
- **Container Orchestration**: Kubernetes (EKS/GKE)
- **Service Mesh**: Istio for mTLS, traffic management
- **CI/CD**: GitHub Actions → Docker Hub → Kubernetes rolling deployment
- **Config Management**: Spring Cloud Config Server / Kubernetes ConfigMaps
