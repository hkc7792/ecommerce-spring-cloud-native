# Implementation Status & Feature Gap Analysis

> **Verified against source code as of 2026-08-09.** Every item below was checked in the
> actual controllers/services/entities, not just the docs. Where the docs (BRD, user stories,
> saga doc, README) disagree with the code, the **code is the source of truth** and that is
> called out inline.
>
> Legend: ✅ implemented · ◑ partially implemented (details in the row) · ⬜ not implemented (**future scope**)
>
> **In sync with:** the [BRD](requirements/brd-ecommerce.md) (status column on every
> functional-requirement table + corrected phased plan) and [User Stories](requirements/user-stories.md)
> (per-story `Status:` line + story-map summary) carry the same ✅/◑/⬜ markers.
>
> **Frontend counterpart:** [`IMPLEMENTATION_STATUS.md`](../../frontend-projects/ecommerce-app-frontend/IMPLEMENTATION_STATUS.md)
> (Angular app) tracks the same features on the UI side so backend ↔ frontend parity can be checked.

---

## 1. Summary

| Domain | What's done | Main gaps (future scope) |
|--------|-------------|--------------------------|
| User | Register, login (JWT), profile read, add address | Password reset, profile/address edit-delete, email verification, refresh token, lockout, admin mgmt, RBAC |
| Inventory | SKU stock, atomic reduce, add, bulk check, low-stock alerts, SSE | Stock reservation w/ TTL, stock audit log, multi-warehouse, no consumers for stock/alert topics |
| Order | Place order, outbox fallback, saga state machine, SSE | Order history API, user-initiated cancel, SHIPPED/DELIVERED, admin dashboard |
| Payment | Kafka-triggered payment, completed/failed/refunded events, history, retry+DLQ | Real gateway, partial refund + `Refund` entity, receipts, GST/settlement reports, payment outbox |
| Notification | WebSocket/STOMP fan-out hub | Email/SMS channels, Redis pub/sub multi-instance broker |
| Gateway | Path routing, global CORS | Rate limiting, auth enforcement, service discovery |
| Infra (cross-cutting) | Docker Compose, per-service H2/MySQL, Actuator, Swagger | Tracing, centralized logs, K8s, CI/CD, load testing, search, recommendations, seller portal, mobile BFF |

**Known quality gaps** (acknowledged in the E2E guide's "honest gaps" + confirmed in code) are
listed in §9 — these are functional risks, not missing features.

---

## 2. User Management (`user-service`)

### ✅ Implemented
| Requirement | Status / Notes |
|---|---|
| UM-001 Register (email, phone, password) | BCrypt hash, `DuplicateEmailException` on duplicate |
| UM-002 Login → JWT (24h) | HMAC token (`JwtTokenProvider`), `jwt.expiration-ms = 86400000` |
| UM-003 Address — **add** (max 5, `isDefault` flag) | `POST /api/user/addresses` |
| UM-004 Profile — **read** | `GET /api/user/profile` (name, email, phone, role, status, addresses) |
| — | `Role` (`CUSTOMER/SELLER/ADMIN`) and `UserStatus` (`ACTIVE/SUSPENDED/DELETED`) enums exist on the entity |

### ⬜ Future scope
- **UM-005 Password reset via email OTP** (US-104) — not implemented.
- **UM-006 Social login** (Google/Facebook) — not implemented.
- **UM-007 Admin user management** (view/search/disable) — not implemented; no admin endpoints.
- **UM-008 User preferences** — not implemented.
- **Email verification** on registration — account is usable immediately (BRD/story implies a verification step).
- **Refresh token** (7d, per US-102) — only a single access token is issued.
- **Account lockout** after 5 failed logins (US-102) — login has no attempt tracking.
- **Profile update** (name/phone/email) — read-only today.
- **Address edit / delete / set-default** endpoints — only add exists; `isDefault` is a flag at add-time only.
- **RBAC enforcement** — the JWT `role` claim is issued but **not used**: `JwtAuthenticationFilter`
  grants a fixed `ROLE_USER` and there are no `@PreAuthorize`/`hasRole` checks, so SELLER/ADMIN
  roles are currently inert (E2E Flow 1 deep-dive calls this out).

---

## 3. Inventory (`inventory-service`)

### ✅ Implemented
| Requirement | Status / Notes |
|---|---|
| INV-001 Unique SKU tracking | `sku_code` unique |
| INV-002 Real-time per-SKU quantity | |
| INV-003 `IN_STOCK` / `OUT_OF_STOCK` auto-status | `qty > 0 → IN_STOCK`, `qty <= 0 → OUT_OF_STOCK` |
| INV-004 Bulk stock check (US-201) | `GET /api/inventory/items?skuCode=...` |
| INV-005 Add stock, upsert (US-202) | `POST /api/inventory/add` — increments existing or creates new |
| INV-006 Atomic reduce w/ rollback (US-203) | `POST /api/inventory/reduce`, `UPDATE ... WHERE quantity >= :qty` |
| INV-007 Low-stock alerts (US-204) | Threshold `inventory.low-stock-threshold=10`; `LowStockAlertEvent` → `inventory-alerts` + SSE |
| — | `StockDepletedEvent` / `StockReplenishedEvent` → `inventory-stock-events` + SSE |
| — | Realtime SSE stream `GET /api/inventory/events` |

### ⬜ Future scope
- **INV-008 Stock reservation with TTL** (temporary hold during checkout) — the current model
  **deducts** stock at order placement instead of reserving it; there is no TTL release.
- **INV-009 Stock history / audit log** of add/reduce operations — not implemented.
- **INV-010 Multi-warehouse stock** — single warehouse only.
- **Consumers for `inventory-stock-events` / `inventory-alerts`** — events are published but
  **no service subscribes** to them (topics are effectively fire-and-forget).
- Seeded SKUs (`data.sql`) are hardcoded; no CRUD for creating/editing SKUs beyond add/reduce.

---

## 4. Order (`order-service`)

### ✅ Implemented
| Requirement | Status / Notes |
|---|---|
| ORD-001 Place order w/ multiple line items (US-301) | `POST /api/order`, synchronous stock reservation |
| ORD-002 UUID order number | |
| ORD-003 Validation | customerId>0, ≥1 line item, price>0, qty>0 (record compact constructors) |
| ORD-004 Total = Σ(price × qty) | `BigDecimal` |
| ORD-005 Publish `OrderPlaceEvent` → `notificationTopic` | Blocking publish, 3 attempts (2s/4s backoff) |
| ORD-006 Outbox fallback (US-302) | On total publish failure → `Out_Box`; `OutboxRelayWorker` polls **every 2s**, 5 retries → `FAILED` |
| ORD-007 State machine (partial) | `PENDING → RESERVED → CONFIRMED` and `→ CANCELLED` (stock fail / payment fail / refund) fully wired; **`SHIPPED`/`DELIVERED` in the enum but never set** |
| — | SSE `GET /api/order/events/{customerId}` w/ connect-time replay |
| — | `OrderStatusChangedEvent` → `order-status-updates` (best-effort) |

### ⬜ Future scope
- **ORD-008 Order history API (US-303)** — the repository has `findByCustomerId(Pageable)` but
  **no controller endpoint exposes it**. There is no "my orders" read path today.
- **ORD-009 User-initiated cancellation (US-304)** — no cancel endpoint. Cancellation only
  happens automatically via insufficient stock, payment failure, or refund.
- **ORD-007 SHIPPED / DELIVERED transitions** — the fulfillment/logistics step does not exist;
  orders can never leave `CONFIRMED`/`CANCELLED`.
- **ORD-011 Admin dashboard** (all orders, filter by status/date/customer) — not implemented.
- **BR-004 caps** (max 10 SKUs / max qty 5 per SKU) — **not enforced**; validation only checks positivity.
- **Duplicate-order detection** (BR-007) — not implemented.
- Shipping/free-shipping logic (BR-005) and GST (BR-006) — not implemented.

---

## 5. Payment (`payment-service`)

### ✅ Implemented
| Requirement | Status / Notes |
|---|---|
| PAY-002 Process payment on `OrderPlaceEvent` (US-401) | Consumes `notificationTopic`; idempotent on `orderNumber` |
| PAY-003 Publish `PaymentCompletedEvent` / `PaymentFailedEvent` | → `paymentTopic`, 3-attempt retry |
| PAY-004 Full refund (US-402, partial) | `POST /api/payment/{orderNumber}/refund` — guard `COMPLETED`, flips to `REFUNDED`, publishes `PaymentRefundedEvent`; order cancels + releases stock |
| PAY-006 Transaction log + history (US-403, partial) | `Payment` entity (paymentId, gatewayTxnId, amount, status, timestamps) + `GET /api/payment/history` (paginated, status filter) + `GET /api/payment/{orderNumber}` |
| PAY-001 Payment methods (partial) | `PaymentMethod` enum (`CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING, WALLET`) — but the gateway is **simulated**: method is picked randomly, no real charge |
| Reliability (NFR) | `@RetryableTopic` consumption: 4 attempts, exp. backoff → `notificationTopic-dlt`; `enable-auto-commit=false`, `ack-mode=record` |
| PAY-010 PCI-DSS (by design) | No card data is ever stored — the gateway is simulated |

### ⬜ Future scope
- **PAY-001 / PAY-010 Real payment gateway** (Razorpay/Stripe per BRD integration points) —
  current "gateway" is `simulatePaymentGateway` (₹>100,000 → 20% fail, else 5%).
- **PAY-005 Partial refund + dedicated `Refund` entity** — full refund only; `failureReason`
  column doubles as the refund marker (E2E Flow 6 deep-dive).
- **US-403 Receipt download (PDF)** — not implemented.
- **PAY-008 GST-compliant invoices** — not implemented.
- **PAY-009 Daily settlement reports** — not implemented.
- **Payment-result publish outbox** — acknowledged gap: after 3 retries the event is logged and
  **lost** if Kafka is down (order can be stuck `RESERVED`); a `log.error("... Implement an outbox
  pattern (follow-up).")` comment marks it (E2E Drill C).
- **US-402 5–7 business-day settlement** / a distinct `RefundProcessedEvent` — refund is
  immediate; the compensating event is `PaymentRefundedEvent`.

---

## 6. Notification (`notification-service`)

### ✅ Implemented
- WebSocket/STOMP hub (`/ws`, SockJS) consuming `notificationTopic` + `order-status-updates`,
  broadcasting to `/topic/orders/{orderNumber}` — order-placed + every status change.
- Best-effort push: a WS send failure is logged, never breaks the consumer.

### ⬜ Future scope
- **US-501 Order-confirmation email** (SendGrid/SES) — not implemented.
- **US-502 Shipping/delivery SMS or email** — not implemented.
- **Any external notification channel** — today the service only pushes to connected STOMP
  clients; email/SMS integration points in the BRD are unused.
- **Redis pub/sub relay** — the simple in-memory broker is single-instance; multi-instance
  WebSocket fan-out (Redis) is documented as planned but not wired.

---

## 7. Gateway (`gateway-service`)

### ✅ Implemented
- Path-based routing for `/api/inventory/**`, `/api/order/**`, `/api/payment/**`,
  `/api/user/**`, `/api/auth/**`, `/api/notification/**`, and `/ws` on port 8080.
- Global CORS for `http://localhost:4200` (one place, all routes).
- Stateless, horizontally scalable.

### ⬜ Future scope
- **Rate limiting** (NFR: 100 req/min per user, 1000 req/min per service) — not configured.
- **Auth enforcement at the gateway** — JWT is currently only validated inside user-service;
  the gateway does not reject unauthenticated calls to other services.
- **Service discovery** (Eureka/Nacos) — routes are **hardcoded** docker service names; no
  `lb://` load balancing, so scaling a service to N replicas is not wired.

---

## 8. Cross-Cutting / Infrastructure (BRD Phase 3–4)

### ⬜ Future scope (all not implemented)
- **Distributed tracing** (Zipkin/Jaeger) — only planned.
- **Centralized logging** (ELK) — services log to stdout/container logs only.
- **Kubernetes manifests + service mesh** — Docker Compose only.
- **CI/CD pipeline** (GitHub Actions → registry → deploy) — none present.
- **Load testing** (Gatling/JMeter) & performance tuning — none; NFRs (2,000 orders/min) untested.
- **Product search** (Elasticsearch) and **recommendation engine** — no catalog/search service.
- **Seller portal / onboarding** — no seller-facing features; `SELLER` role is inert (see §2).
- **Mobile app BFF** — not present.
- **TLS 1.3 / AES-256 at rest / Vault secrets** (NFR security) — dev-only plaintext env vars.
- **Alerting on FAILED outbox rows** — `FAILED` events sit in `Out_Box` with no alert wiring.

---

## 9. Known Quality Gaps (functional risks in the current code)

These are confirmed in code and called out in the E2E guide's "honest gaps"; fixing them is
future scope too:

| # | Gap | Consequence |
|---|-----|-------------|
| 1 | **Inventory reduction events published inside the loop**, before later SKUs may fail | On batch rollback, `StockDepleted/LowStock` events for earlier SKUs were already emitted — consumers can observe stock changes that never committed. Fix: publish after commit (or transactional outbox). |
| 2 | **Replenish publish is fire-and-forget** (`kafkaTemplate.send`, no ack) | `StockReplenishedEvent` can be lost silently. |
| 3 | **Standalone `/inventory/reduce` returns 500** on insufficient stock | The order flow maps this to `409`, but the raw endpoint leaks `RuntimeException` → `500` (inconsistent status codes). |
| 4 | **Compensation (`releaseStock`) is best-effort per item** — try/catch + log only, no retry/outbox | If inventory-service is down during a payment-failure/refund, stock is **not** restored and nothing retries it. |
| 5 | **Payment-result publish has no outbox** | Kafka down ⇒ payment result event lost; order stays `RESERVED` forever (E2E Drill C). |
| 6 | **Refund endpoint is not idempotent** | A repeated refund on an already-`REFUNDED` payment returns `409` instead of a no-op returning current state. |
| 7 | **Payment history `status` filter is unvalidated** | `PaymentStatus.valueOf(status.toUpperCase())` throws on garbage input → unhandled `500`. |
| 8 | **Outbox insert is outside the order's transaction** | It's "outbox-as-fallback" (insert happens after `placeOrder` commits), not a classic transactional outbox — a crash between commit and insert can still lose an event. |
| 9 | **Client-supplied prices are trusted** | `OrderRequest` prices come from the client; there is no server-side price lookup/verification against a catalog. |
| 10 | **Docs lag code** | README / system-overview / saga doc still mark gateway, payment, user services as "planned" and state outbox polling is 30s (code: 2s). |

---

## 10. How This Was Verified

Read **every controller, service impl, entity, repository, config, and `application.yml`**
across all 7 modules, plus `docker-compose.yml`, `docker-compose.env`, the BRD, user stories,
the saga doc, and the E2E testing guide. Endpoints were cross-checked against the actual
`@RequestMapping`/`@GetMapping`/`@PostMapping` annotations. Anything documented but absent from
code is listed above as future scope.
