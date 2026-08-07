# End-to-End Testing Guide — Ecommerce Platform

> A **flow-by-flow** manual that walks every business flow in sequence, shows exactly
> what to run, what to expect at every hop, and — crucially — **why** it behaves that way.
>
> Written to be studied end-to-end: after this you can describe the platform's behavior,
> failure modes, and design trade-offs at a senior-Java-engineer level.

---

## 0. How to read this document

Each **Flow** section is a complete, runnable scenario in sequence:

1. **Setup** — preconditions (data, running services).
2. **Steps** — `curl` commands, with the *port* you should hit.
3. **Expect** — the exact response/behavior at each hop.
4. **Why it works** — the design reasoning to internalize.
5. **Expert notes** — the subtle points that separate "knows how to use it" from
   "understands the architecture."

Two ways to run everything:

| Mode | Base URL | Notes |
|------|----------|-------|
| **Gateway (recommended)** | `http://localhost:8080` | Single entry point; routes `/api/*` to the right service. Best demo of the architecture. |
| **Direct service** | `http://localhost:8081`…`8085` | Hits a service in isolation — useful when you want to isolate a specific service's behavior. |

| Service | Gateway path | Direct port |
|---------|--------------|-------------|
| inventory-service | `http://localhost:8080/api/inventory/**` | `http://localhost:8081` |
| order-service | `http://localhost:8080/api/order/**` | `http://localhost:8082` |
| payment-service | `http://localhost:8080/api/payment/**` | `http://localhost:8083` |
| user-service | `http://localhost:8080/api/auth/**`, `/api/user/**` | `http://localhost:8084` |
| notification-service | `http://localhost:8080/ws`, `/api/notification/**` | `http://localhost:8085` |
| Kafka UI | Kafdrop | `http://localhost:9000` |

**Stack bring-up** (Docker):

```bash
export $(grep -v '^#' docker-compose.env | xargs)   # or source your env
docker compose up -d --build
docker compose ps                                   # all services healthy
```

Local (IDE) mode: run each service on its own (H2 in-memory DB), start Kafka locally on `:9092`.

---

## 1. System Topology at a Glance

```
                        ┌─────────────────────────────┐
                        │    API Gateway :8080        │  routes /api/* + /ws
                        └──────┬──────┬──────┬─────────┘
        WebSocket/STOMP        │      │      │
   ws://localhost:8080/ws      │      │      │
        ┌──────────────────────┼──────┼──────┼───────────────┐
        ▼                      ▼      ▼      ▼               ▼
┌───────────────┐  ┌───────────────┐ ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐
│ Order Service │  │Inventory Svc  │ │Payment Svc  │ │ User Service │ │ Notification Svc  │
│  :8082        │  │  :8081        │ │  :8083      │ │  :8084       │ │  :8085 (WS hub)  │
│ order_db      │  │ inventory_db  │ │ payment_db  │ │  user_db     │ │  (no database)   │
└───────┬───────┘  └──────┬────────┘ └─────┬───────┘ └──────┬───────┘ └──────────────────┘
        └─────── Kafka event bus: notificationTopic / paymentTopic /
                order-status-updates / inventory-stock-events / inventory-alerts ────────┘
```

**Kafka topics and their contracts** (memorize these — every flow is a transaction across them):

| Topic | Producer | Consumer(s) | Payload records |
|-------|----------|-------------|-----------------|
| `notificationTopic` | order-service | payment-service, notification-service | `OrderPlaceEvent` |
| `paymentTopic` | payment-service | order-service | `PaymentCompletedEvent` / `PaymentFailedEvent` / `PaymentRefundedEvent` |
| `order-status-updates` | order-service | notification-service | `OrderStatusChangedEvent` |
| `inventory-stock-events` | inventory-service | — (future) | `StockDepletedEvent` / `StockReplenishedEvent` |
| `inventory-alerts` | inventory-service | — (future) | `LowStockAlertEvent` |

Plus auto-created retry topics (`notificationTopic-retry-1000/2000/4000`) and the
dead-letter topic `notificationTopic-dlt` (payment-service consumer only).

**Seeded inventory** (`data.sql`):

| SKU | Qty | Status |
|-----|-----|--------|
| `iphone_15` | 100 | IN_STOCK |
| `iphone_15_pro` | 50 | IN_STOCK |
| `pixel_8` | 0 | OUT_OF_STOCK |

**Simulated payment gateway** (deterministic-ish, this drives the failure flows):

| Order total | Failure rate | Failure reasons |
|-------------|--------------|-----------------|
| ≤ ₹100,000 | **5%** | `CARD_DECLINED`, `GATEWAY_TIMEOUT`, `PROCESSING_ERROR` (random) |
| > ₹100,000 | **20%** | `INSUFFICIENT_FUNDS` |

> 💡 **Testing tip:** the "payment failed" flow is probabilistic. Use a **high-value order**
> (> ₹100,000) to get a 1-in-5 failure per attempt. Each order is an independent roll.

---

## 2. Flow 1 — User Registration & Authentication

The identity flow that front-ends everything else. `user-service` issues JWTs used by the
secured `/api/user/**` endpoints.

### 2.1 Register a user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Rahul Sharma","email":"rahul.sharma@gmail.com","phone":"9876543210","password":"Secret@123"}'
```

**Expect:** `201 Created`, body `User registered successfully`.

**What happened internally:** `AuthServiceImpl.register` checks `existsByEmail` → hashes the
password with **BCrypt** (`BCryptPasswordEncoder`) → saves the `User` (role defaults to
`CUSTOMER`). **Never stores plaintext passwords.**

### 2.2 Register the same email again (validation)

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Rahul Sharma","email":"rahul.sharma@gmail.com","phone":"9876543210","password":"Secret@123"}'
```

**Expect:** `400` — `DuplicateEmailException` (`Email ... is already in use.`).
`/api/auth/**` is `permitAll` in `SecurityConfig`; everything else is authenticated.

### 2.3 Login — happy path

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"rahul.sharma@gmail.com","password":"Secret@123"}' | jq -r .token)
echo "$TOKEN"
```

**Expect:** `200`, body like:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "userId": 1,
  "email": "rahul.sharma@gmail.com",
  "fullName": "Rahul Sharma",
  "role": "CUSTOMER"
}
```

**Why it works:** `JwtTokenProvider` builds an **HMAC-SHA256** JWT with subject=email,
custom claims `userId` + `role`, `exp = now + 24h` (`jwt.expiration-ms`). The secret comes
from `JWT_SECRET` env var. Stateless — no session stored server-side.

### 2.4 Login — wrong password

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"rahul.sharma@gmail.com","password":"wrongpass"}'
```

**Expect:** `400` with `Invalid email or password.` — deliberately **ambiguous** so you
cannot enumerate which emails exist.

### 2.5 Access a protected endpoint

```bash
# No token → 401
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/user/profile

# With token → 200
curl -s http://localhost:8080/api/user/profile -H "Authorization: Bearer $TOKEN"
```

**Expect:** `401` without token; `200` with profile JSON (userId, name, email, phone, role,
status, addresses).

**Why it works:** `JwtAuthenticationFilter` (a `OncePerRequestFilter`) reads
`Authorization: Bearer`, validates the signature/expiry, and puts the email into the
`SecurityContext`. `@AuthenticationPrincipal String email` in `UserController` receives it.

### 2.6 Add an address

```bash
curl -s -X POST http://localhost:8080/api/user/addresses \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"label":"HOME","street":"12 MG Road","city":"Bengaluru","state":"KA","pincode":"560001","phone":"9876543210","isDefault":true}'
```

**Expect:** `201`, `Address added successfully`. Then `GET /api/user/profile` shows it nested
under `addresses`.

### 🔎 Expert deep-dive — auth

- **BCrypt is chosen over SHA/plaintext** because it is computationally expensive and
  salted — brute-force resistant.
- The JWT is **signed, not encrypted** — anyone can read claims (don't put secrets in it),
  but nobody can forge them.
- Auth filter sets a **fixed** authority `ROLE_USER` — the `role` claim exists in the token
  but **is not yet used for RBAC** at the controller level. A senior engineer would call
  this out as the next step (method-level `@PreAuthorize`, or a `ROLE_ADMIN` filter).
- **Test drill:** hit `/api/user/profile` with an expired or tampered token → `401`
  (filter silently skips setting auth, request fails auth). Verify `validateToken` is the
  only gate.

---

## 3. Flow 2 — Inventory Management & Real-Time Stock

Inventory is the *reservation ledger* of the saga. It exposes CRUD + an SSE channel.

### 3.1 Check stock availability

```bash
curl -s "http://localhost:8080/api/inventory/items?skuCode=iphone_15&skuCode=iphone_15_pro&skuCode=pixel_8"
```

**Expect:**
```json
[
  {"skuCode":"iphone_15","isInStock":true},
  {"skuCode":"iphone_15_pro","isInStock":true},
  {"skuCode":"pixel_8","isInStock":false}
]
```

`isInStock = quantity > 0`. This is the **read-model** endpoint the frontend uses for
"Add to cart" gating.

### 3.2 Add inventory (replenishment)

```bash
curl -s -X POST http://localhost:8080/api/inventory/add \
  -H "Content-Type: application/json" \
  -d '{"skuCode":"iphone_15","quantity":50}'
```

**Expect:** `201`. Internal: existing row **incremented** (`100 + 50 = 150`) and a
`StockReplenishedEvent` published to `inventory-stock-events` + pushed to SSE subscribers.

If the SKU is new, a row is **created** with status `IN_STOCK`.

### 3.3 Reduce stock (the atomic guard)

```bash
curl -s -X POST http://localhost:8080/api/inventory/reduce \
  -H "Content-Type: application/json" \
  -d '[{"skuCode":"iphone_15","quantity":5},{"skuCode":"iphone_15_pro","quantity":2}]'
```

**Expect:** `200`. Now try **overselling** a single SKU (ask for more than exists):

```bash
curl -s -X POST http://localhost:8080/api/inventory/reduce \
  -H "Content-Type: application/json" \
  -d '[{"skuCode":"pixel_8","quantity":1}]'        # pixel_8 has 0 in stock
```

**Expect:** `500`/exception `Item not found...`? No — **`pixel_8` exists** but has 0 qty, so
`affectedRows == 0` → `RuntimeException("Insufficient stock for: pixel_8")`. The whole batch
**rolls back** (`@Transactional(rollbackFor = Exception.class)`).

> ⚠️ `500` for insufficient stock on the **standalone** `/reduce` endpoint (raw
> RuntimeException). **Note:** the *order flow* maps this to `409` via `OutOfStockException`
> — see Flow 4. This inconsistency (500 vs 409) is worth knowing for interviews.

### 3.4 Watch stock events in real time (SSE)

```bash
# Terminal 1 — subscribe
curl -N http://localhost:8080/api/inventory/events

# Terminal 2 — trigger a mutation
curl -s -X POST http://localhost:8080/api/inventory/add \
  -H "Content-Type: application/json" -d '{"skuCode":"iphone_15","quantity":1}'
```

**Expect** in Terminal 1: first an event `event: connected`, `data: subscribed`, then
`event: stock-update` carrying the `StockReplenishedEvent` JSON.

**Why it works:** `StockSseService` keeps open `SseEmitter`s (never time out) in a
`ConcurrentHashMap`; `publish()` broadcasts to all. Channel is **global** (not per-customer).

### 3.5 Trigger the low-stock alert

Reduce `iphone_15_pro` (50) down to below the threshold of **10**:

```bash
curl -s -X POST http://localhost:8080/api/inventory/reduce \
  -H "Content-Type: application/json" \
  -d '[{"skuCode":"iphone_15_pro","quantity":45}]'
```

**Expect:** `200`. Watch SSE: you'll get a `LowStockAlertEvent` (qty 5 < threshold 10) and a
`StockDepletedEvent` **only when qty hits exactly 0** (`quantityAfter <= 0`). Both go to
Kafka (`inventory-alerts`, `inventory-stock-events`) **and** SSE.

### 🔎 Expert deep-dive — inventory

- **Atomicity, not just transactions.** The killer line is
  `UPDATE Inventory SET quantity = quantity - :qty, status = :newStatus
   WHERE skuCode = :skuCode AND quantity >= :qty`. The **`quantity >= :qty` predicate is the
   lock** — under concurrent flash-sale requests, the DB guarantees only one transaction can
   claim stock, so `quantity` can **never** go negative. This is the classic
   **conditional update / optimistic inventory** pattern (no SELECT-FOR-UPDATE needed).
- **`affectedRows == 0`** is the branch that means "not found **or** insufficient" — the
  repo can't distinguish, hence the `500` above.
- **Known gap a senior would flag:** `publishReductionEvents` runs **inside the loop**,
  before later items may fail. If SKU-2 fails, the DB rolls back **but** the Kafka/SSE
  events for SKU-1 were already emitted → consumers can observe stock events that never
  happened. Fix: collect outcomes first, publish after the loop / after commit
  (or transactional outbox). Same for `publishReplenished` (fire-and-forget `send`, no ack).

---

## 4. Flow 3 — Place Order (Happy Path — the Core Saga)

This is the **money flow**. Memorize the exact sequence of synchronous calls + Kafka hops.

### 4.1 The order placement request

```bash
ORDER=$(curl -s -X POST http://localhost:8080/api/order \
  -H "Content-Type: application/json" \
  -d '{
        "customerId": 1,
        "orderLineItemsDtoList": [
          {"skuCode":"iphone_15","price":65000.00,"quantity":1},
          {"skuCode":"iphone_15_pro","price":84999.00,"quantity":1}
        ]
      }')
echo "$ORDER"
```

**Expect:** `201 Created`:
```json
{ "orderNumber": "3f4a…-uuid", "status": "RESERVED" }
```

Note: the response comes back **before** payment finishes — orderNumber + current status.
Total = 65000 + 84999 = **₹149,999** (high-value → 20% failure chance; see why below).

### 4.2 What actually happened, hop by hop

| # | Who | What | Channel |
|---|-----|------|---------|
| 1 | order-service | Persist `Order` (status `PENDING`), persist line items. | DB |
| 2 | order-service → inventory | `POST /api/inventory/reduce` (synchronous via `InventoryClient`) | **HTTP** |
| 3 | inventory | Atomic decrement; if ok → order-service sets status `RESERVED` | DB |
| 4 | order-service | Build `OrderPlaceEvent`, publish to `notificationTopic` | **Kafka** |
| 5 | payment-service | Consumes `OrderPlaceEvent` (group `payment-service-group`) | Kafka |
| 6 | payment-service | Idempotency check → create `Payment` (PROCESSING) → simulate gateway (200–800ms) | DB |
| 7 | payment-service | On success: `Payment` → COMPLETED + `gatewayTransactionId`, publish `PaymentCompletedEvent` to `paymentTopic` | Kafka |
| 8 | order-service | Consumes `PaymentCompletedEvent` → `RESERVED → CONFIRMED` | Kafka |
| 9 | order-service | Publish `OrderStatusChangedEvent` to `order-status-updates` **and** push to SSE | Kafka + SSE |
| 10 | notification-service | Consumes both topics → WebSocket/STOMP push to `/topic/orders/{orderNumber}` | Kafka → WS |

**Key insight — why the response is `201` while the payment is async:** the saga is
**orchestrated**; order-service does not block on the payment. The client gets the order
number immediately and **subscribes to realtime updates** for the final outcome.

### 4.3 Verify the happy path completed

```bash
# 1. Order is CONFIRMED
curl -s http://localhost:8080/api/order/events/1?orderNumber=$(echo $ORDER | jq -r .orderNumber)

# 2. Payment record exists and is COMPLETED
curl -s http://localhost:8080/api/payment/$(echo $ORDER | jq -r .orderNumber)

# 3. Stock was actually reduced (iphone_15: 150→149, iphone_15_pro: 50→48 after prior steps)
curl -s "http://localhost:8080/api/inventory/items?skuCode=iphone_15&skuCode=iphone_15_pro"
```

**Expect:** order status `CONFIRMED`; payment JSON with `"status":"COMPLETED"`,
`gatewayTransactionId:"gw_txn_…"`, `paidAt` set; inventory quantities reduced.

### 4.4 Watch it live over both realtime channels

**SSE (direct from order-service):**
```bash
curl -N "http://localhost:8080/api/order/events/1"
# place a new order in another terminal → you should see event: order-status
# PENDING→RESERVED, then RESERVED→CONFIRMED (or →CANCELLED)
```

The `?orderNumber=` param **replays the current status** on connect — so a client that joins
after placement still learns the latest state (important "catch-up" detail).

**WebSocket/STOMP (via notification-service):** use a STOMP client. Endpoint `/ws`
(SockJS), subscribe to `/topic/orders/{orderNumber}`. You'll receive **both** the
`OrderPlaceEvent` (order placed) and each `OrderStatusChangedEvent` (status transitions).

### 🔎 Expert deep-dive — the saga

- **Two realtime paths for the same event**: order-service pushes **SSE directly**, *and*
  publishes `OrderStatusChangedEvent` to Kafka that notification-service relays over
  **WebSocket**. Why both? SSE is simple/live for order status; WS/STOMP is the general push
  hub (also fan-outs "order placed"). The redundancy is a deliberate resilience choice —
  if Kafka is down, SSE still works.
- **Idempotency via `orderNumber`**: payment-service checks
  `existsByOrderNumber(orderNumber)` first — at-least-once Kafka delivery cannot double-charge.
  Every consumer treats `orderNumber` as the idempotency key.
- **Message key = orderNumber**: producers send with key → Kafka guarantees **per-key
  ordering**. This matters because CONFIRMED must follow RESERVED, CANCELLED follows CONFIRMED.
- **No blocking `.get()` on the status event**: `publishOrderStatusEvent` is
  **best-effort** (async `.send()` + log on failure). SSE remains the primary channel, so a
  Kafka failure must never fail the order flow. Contrast with the `OrderPlaceEvent` publish,
  which **is** blocking with retry + outbox (Flow 9) — different reliability contracts for
  different events.

---

## 5. Flow 4 — Place Order: Insufficient Stock (Reservation Failure)

Tests the **first compensation point** — before payment ever starts.

### 5.1 Place an order for an out-of-stock SKU

```bash
curl -s -X POST http://localhost:8080/api/order \
  -H "Content-Type: application/json" \
  -d '{
        "customerId": 1,
        "orderLineItemsDtoList": [
          {"skuCode":"pixel_8","price":29999.00,"quantity":1}
        ]
      }'
```

**Expect:** `409 Conflict`:
```json
{
  "status": 409,
  "message": "Insufficient stock for order: Insufficient stock for: pixel_8",
  "timestamp": "..."
}
```

### 5.2 What happened internally

1. Order persisted as `PENDING`.
2. `inventoryClient.reduceStock(...)` throws `RuntimeException` (affectedRows==0).
3. `placeOrder` catches it → **transitions order to `CANCELLED`** → rethrows as
   `OutOfStockException`.
4. `@Transactional(noRollbackFor = OutOfStockException.class)` → the `CANCELLED` write
   **commits** (the business state change is intentional, not an error to roll back).
5. `GlobalExceptionHandler` maps `OutOfStockException` → **`409`**.

**Crucial:** because stock reservation failed, **no `OrderPlaceEvent` is ever published** →
payment-service never sees this order. Nothing to compensate — the saga stops before step 3.

### 🔎 Expert deep-dive

- Note the deliberate `noRollbackFor`: a normal `@Transactional` would **undo** the
  `CANCELLED` status we just wrote. The flag says "this domain exception is a *result*, not a
  *failure* — keep my state change."
- The message `"Insufficient stock for order: Insufficient stock for: pixel_8"` double-wraps
  the cause — cosmetic, but a sign the raw exception is leaking through. A senior would wrap
  into a clean domain message.
- **Verify no side effects:** `GET /api/payment/{orderNumber}` → `404` (no payment was ever
  created). Stock unchanged.

---

## 6. Flow 5 — Payment Failure (Saga Compensation)

Tests the **second compensation point**: stock was reserved, then the payment failed — we
must undo the reservation.

### 6.1 Force a failure

Because the simulated gateway is probabilistic, use a **high-value order** (> ₹100,000 →
**20%** failure, reason always `INSUFFICIENT_FUNDS`) and repeat until you see `CANCELLED`:

```bash
for i in $(seq 1 10); do
  ORD=$(curl -s -X POST http://localhost:8080/api/order \
    -H "Content-Type: application/json" \
    -d '{"customerId":1,"orderLineItemsDtoList":[{"skuCode":"iphone_15","price":65000.00,"quantity":2}]}')
  echo "$ORD"
  sleep 2   # give the async payment time to land
  ORDER_NO=$(echo "$ORD" | jq -r .orderNumber)
  ST=$(curl -s "http://localhost:8080/api/order/events/1?orderNumber=$ORDER_NO")
  echo "→ $ORDER_NO"
  curl -s http://localhost:8080/api/payment/$ORDER_NO | jq '.status, .failureReason'
  echo "---"
done
```

Stop when a payment shows `"FAILED"` — expect ~1–2 per 10 attempts.

### 6.2 Verify the compensation

```bash
# Order must be CANCELLED
curl -s "http://localhost:8080/api/order/events/1?orderNumber=$ORDER_NO" | jq
# Payment must be FAILED with a reason
curl -s http://localhost:8080/api/payment/$ORDER_NO | jq '{status, failureReason}'
# Stock must be RESTORED: iphone_15 returns to its pre-order quantity (it was deducted then re-added)
curl -s "http://localhost:8080/api/inventory/items?skuCode=iphone_15" | jq
```

### 6.3 The compensation trace

| # | Who | What |
|---|-----|------|
| 1 | order-service | Persist PENDING → reserve stock → RESERVED → publish `OrderPlaceEvent` |
| 2 | payment-service | `Payment` PROCESSING → gateway simulation **fails** |
| 3 | payment-service | `Payment` → `FAILED` + `failureReason`, publish `PaymentFailedEvent` to `paymentTopic` |
| 4 | order-service | Consumes `PaymentFailedEvent` → `RESERVED → CANCELLED` |
| 5 | order-service | **`releaseStock(order)`** → calls `POST /api/inventory/add` for **each** line item (synchronous) |
| 6 | inventory | Stock restored → `StockReplenishedEvent` published |
| 7 | order-service | `OrderStatusChangedEvent` → SSE + Kafka → notification WS |

### 🔎 Expert deep-dive — compensation semantics

- `releaseStock` calls `InventoryClient.addStock` — i.e., the **compensating transaction**
  is a plain *replenish*, keyed on the same SKU/qty the order consumed. Because `addStock`
  is idempotent in effect (increment by qty), re-running it is safe.
- The compensation is **best-effort per item**: each call is wrapped in try/catch, a failure
  is logged, and the loop continues. A senior would note there is **no retry/outbox for the
  compensation** — if inventory-service is down at that moment, the stock is silently not
  restored (gap to call out, and a natural "what would you improve?" answer).
- `handlePaymentFailed` **guards the state machine**: it only acts if the order is currently
  `RESERVED`. If a duplicate/out-of-order `PaymentFailedEvent` arrives later, it's ignored —
  no double-cancel, no stock double-release.

---

## 7. Flow 6 — Refund (Compensating Transaction for a Confirmed Order)

The newest flow. Refund = reversing a **confirmed** order (payment already succeeded, stock
already consumed at placement and never released).

### 7.1 Precondition: a CONFIRMED order

Run Flow 3 (happy path). If you hit a `CANCELLED`, just place another order and wait for
`CONFIRMED`.

### 7.2 Issue the refund

```bash
# Use a confirmed order's number
curl -s -X POST http://localhost:8080/api/payment/$ORDER_NO/refund
```

**Expect:** `200` with the payment flipped to REFUNDED:
```json
{
  "paymentId": "pay_…",
  "orderNumber": "…",
  "customerId": 1,
  "amount": 149999.00,
  "currency": "INR",
  "paymentMethod": "UPI",
  "gatewayTransactionId": "gw_txn_…",
  "status": "REFUNDED",
  "failureReason": "REFUNDED",
  "paidAt": "…",
  "createdAt": "…"
}
```

### 7.3 Verify the full compensation

```bash
# Order: CONFIRMED → CANCELLED
curl -s "http://localhost:8080/api/order/events/1?orderNumber=$ORDER_NO" | jq
# Payment: REFUNDED
curl -s http://localhost:8080/api/payment/$ORDER_NO | jq '.status'
# Stock: restored (replenished back)
curl -s "http://localhost:8080/api/inventory/items?skuCode=iphone_15" | jq
```

### 7.4 The refund trace

| # | Who | What |
|---|-----|------|
| 1 | payment-service | `refund(orderNumber)` — guard: status **must be `COMPLETED`** |
| 2 | payment-service | Flip `Payment` → `REFUNDED`, set `failureReason="REFUNDED"` (reuses the failure-reason column as the refund marker) |
| 3 | payment-service | Publish `PaymentRefundedEvent` to `paymentTopic` (same 3-attempt retry helper) |
| 4 | order-service | Consumes `PaymentRefundedEvent` → `CONFIRMED → CANCELLED` |
| 5 | order-service | `releaseStock(order)` → `POST /api/inventory/add` per line item |
| 6 | order-service | `OrderStatusChangedEvent` → SSE + Kafka → notification WS |

### 7.5 Negative tests (state machine guard)

```bash
# 1. Refund a NON-existent order → 404
curl -s -X POST http://localhost:8080/api/payment/no-such-order/refund
# 2. Refund an order whose payment is NOT COMPLETED (e.g. FAILED or already REFUNDED) → 409
curl -s -X POST http://localhost:8080/api/payment/$ORDER_NO/refund   # now already REFUNDED
```

**Expect:** `404` (`PaymentNotFoundException`) for unknown order; `409 Conflict`
(`IllegalStateException` → `Cannot refund payment for order … in status REFUNDED; only
COMPLETED payments can be refunded`) for a second refund.

### 🔎 Expert deep-dive — refund

- **Refund is a *full* refund only** (no partial amount/notes in the request — the old
  design doc described partials; the implementation deliberately kept it simple). `failureReason`
  doubles as the refund marker — pragmatic, but a separate `Refund` entity is the planned
  follow-up for partial refunds + audit trail.
- **Idempotency gap to name:** a repeated `refund` call on an already-REFUNDED payment
  returns `409`, not a no-op. A *truly* idempotent endpoint would return the existing
  REFUNDED state on repeat. Interview gold.
- **Guard logic** lives in two places: payment-service (`status == COMPLETED` to *allow*)
  and order-service (`status == CONFIRMED` to *transition*). Both guards are what make
  duplicate/late events harmless.

---

## 8. Flow 7 — Payment History & Lookup (Read Models)

### 8.1 Get payment by order number

```bash
curl -s http://localhost:8080/api/payment/$ORDER_NO
```

### 8.2 Get paginated history for a customer

```bash
curl -s "http://localhost:8080/api/payment/history?customerId=1&page=0&size=5"
curl -s "http://localhost:8080/api/payment/history?customerId=1&page=0&size=20&status=COMPLETED"
curl -s "http://localhost:8080/api/payment/history?customerId=1&status=REFUNDED"
```

**Expect:** a Spring Data `Page<PaymentResponse>` (`content`, `totalElements`, `pageable`,
`sort`). The `status` filter does an exact `findByCustomerIdAndStatus`. Sorted by
`createdAt` descending.

### 🔎 Expert deep-dive

- `history` maps **entity → response** via `mapToResponse` — no lazy-loading traps because
  the DTO only reads the entity's own fields.
- `PaymentStatus.valueOf(status.toUpperCase())` will throw on garbage input → unhandled →
  `500`. A senior would add a `@Pattern`/`@Valid` or a `400` mapper (another honest gap).

---

## 9. Flow 8 — Gateway Routing & CORS (Single Entry Point)

Now re-run **any** earlier flow against `http://localhost:8080` instead of the per-service
port — that's the gateway.

### 9.1 Route verification

```bash
curl -s http://localhost:8080/api/inventory/items?skuCode=iphone_15      # → inventory
curl -s http://localhost:8080/api/payment/history?customerId=1&size=1   # → payment
curl -s -X POST http://localhost:8080/api/order ...                      # → order
curl -s -X POST http://localhost:8080/api/auth/login ...                 # → user-service auth
# Unknown prefix → 404 from the gateway itself
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/nonexistent
```

### 9.2 CORS preflight (frontend is Angular on :4200)

```bash
curl -s -i -X OPTIONS http://localhost:8080/api/order \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type" | head -20
```

**Expect:** `200` with `Access-Control-Allow-Origin: http://localhost:4200` and the allowed
methods/headers echoed. CORS is configured **globally** in `gateway-service/application.yml`
(`globalcors`) — one place for all routes.

### 9.3 WebSocket through the gateway

STOMP client connects to `ws://localhost:8080/ws` (gateway routes `/ws` → notification
`:8085`). The direct endpoint `ws://localhost:8085/ws` still works for isolation testing.

### 🔎 Expert deep-dive — gateway

- **Path-prefix routing only** (`Path=/api/order/**`). There is **no service discovery**
  (no Eureka/Nacos) — routes are hard-coded docker service names. Simple and predictable;
  you'd introduce discovery + `lb://` when services scale horizontally.
- The notification service binds **`8085` in-container** (unlike others which bind 8080) —
  that's why its route URI is `http://notification-service:8085`. A nice detail to notice.
- Gateway itself is **stateless** — horizontal scaling is trivial. It also centralizes CORS,
  which would otherwise be repeated in every service's `CorsConfig`.

---

## 10. Flow 9 — Reliability Drills (the senior-engineer differentiators)

These prove the **non-functional** requirements. Run them deliberately; they're the best
interview material in the whole system.

### Drill A — Kafka down: order still succeeds via the Outbox

**Purpose:** prove `OrderPlaceEvent` is never lost even when Kafka is unreachable.

1. `docker compose stop kafka zookeeper` (or Ctrl-C your local Kafka).
2. Place an order:
   ```bash
   curl -s -X POST http://localhost:8080/api/order -H "Content-Type: application/json" \
     -d '{"customerId":1,"orderLineItemsDtoList":[{"skuCode":"iphone_15","price":65000.00,"quantity":1}]}'
   ```
3. **Expect:** order still returns `201 RESERVED`. Stock is reserved. But:
   - `publishOrderEventWithFallback` tries Kafka 3× (2s → 4s backoff, blocking `.get(5s)`),
     all fail, then calls `outboxService.saveFailedEvent(event)`.
   - Check the outbox: H2 console of order-service (`/h2-console`, URL
     `jdbc:h2:mem:testdb` / or MySQL `order_db`) → table **`Out_Box`**:
     `aggregateType='ORDER'`, `eventType='ORDER_PLACED'`, `status='PENDING'`, `retryCount=0`.
4. `docker compose start kafka zookeeper`.
5. Within ~2s the **`OutboxRelayWorker`** (a `@Scheduled(fixedDelay=2000)` poller) picks the
   row, marks it `PROCESSING`, publishes to Kafka, marks it `PUBLISH` (or `retryCount++` and
   back to `PENDING`). Watch Kafdrop for `OrderPlaceEvent` on `notificationTopic`.
6. The saga then completes as normal — payment processes, order `CONFIRMED`.

**Why it works — the transactional-outbox-ish pattern:** the event write is **atomic with
the order's own DB transaction** (same `@Transactional`), so we never emit an event for an
order that rolled back, and we never lose one when Kafka is down. This is the textbook answer
to "how do you guarantee delivery without distributed transactions?"

**Limits to name honestly:** the outbox insert itself is not in the **same** transaction as
the order save (it happens *after* `placeOrder` commits, inside `publishOrderEventWithFallback`
— really an "outbox-as-fallback", not a classic transactional outbox). Polling is 2s, max 5
retries then `FAILED` (no alert wiring). These are the growth points.

### Drill B — Payment processing retries + DLQ

**Purpose:** prove `@RetryableTopic` never silently drops a failing message.

**Mechanism (no code change needed to observe):**
- `OrderEventConsumer.consumeOrderPlaceEvent` is annotated
  `@RetryableTopic(attempts=4, backoff=@Backoff(delay=1000, multiplier=2, maxDelay=10000))`.
- On a thrown exception: the message is retried on **auto-created retry topics**
  `notificationTopic-retry-1000 → retry-2000 → retry-4000` (non-blocking, backoff via the
  `kafkaRetryTaskScheduler` bean).
- After all retries: moved to `notificationTopic-dlt` and handled by `@DltHandler handleDlt`
  (logs `permanently failed … moved to DLQ` with the exception message).
- `enable-auto-commit=false` + `ack-mode=record` → offsets advance **only** after a record is
  processed or routed to retry/DLT. A crash mid-processing = safe redelivery.

**How to actually trigger it (for a demo):** temporarily throw in `processPayment` (or point
`bootstrap-servers` at a topic where deserialization fails). Watch Kafdrop: the record moves
through `retry-1000/2000/4000` then sits on `notificationTopic-dlt`. The DLT is the
**operator's inbox** — a human decides replay vs. compensate.

**Why this is senior-level:** most teams "handle" Kafka failures with a try/catch and a log.
Here the failure is **first-class** — retried, dead-lettered, inspected. This directly
delivers the NFR "no silent loss."

### Drill C — Payment result publish retry (no outbox)

**Purpose:** contrast two reliability contracts.
- OrderPlaceEvent publish: **blocking + outbox fallback** (Drill A).
- Payment result publish: **3 attempts, 500ms backoff**, and if still failing →
  `log.error("… event lost. Implement an outbox pattern (follow-up).")`.

Run Kafka down *after* the order is placed but *before* payment completes — the payment
result is dropped and **nothing retries it later** (unlike the order event). The order stays
`RESERVED` forever. This is a real, acknowledged gap (there's even a comment in the code).
Being able to *say* this precisely, and propose the transactional-outbox fix, is the mark of
someone who has actually read the code.

### Drill D — Duplicate event delivery (idempotency)

Kafka is **at-least-once**: redeliveries happen. Prove each consumer tolerates them:

1. **Payment idempotency:** publish a second `OrderPlaceEvent` for the *same* orderNumber
   (e.g., via Kafdrop, or re-run the outbox relay). `existsByOrderNumber` → skipped with
   `Skipping duplicate.` log → **no double charge, no new payment row**.
2. **State-machine guards:** replay an old `PaymentCompletedEvent` against an order that is
   now `CANCELLED`. `handlePaymentCompleted` sees `previous != RESERVED` → logs and skips.
   Same for failed/refunded events. **No phantom state changes, no double stock release.**

### Drill E — Concurrency: overselling prevention

Run N parallel reduces against one SKU that has enough stock for only a subset:

```bash
# iphone_15 has 149. Fire 10 parallel requests each asking 20 (need 200 total) — at most 7 succeed
seq 1 10 | xargs -P10 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8080/api/inventory/reduce -H "Content-Type: application/json" \
  -d '[{"skuCode":"iphone_15","quantity":20}]' | sort | uniq -c
```

**Expect:** some `200`, some `500` (`Insufficient stock`), and **final quantity is exactly
149 − (20 × successes)**, never negative. The DB constraint (not a distributed lock) is what
makes this safe.

### Drill F — Partial outage: inventory-service down during payment failure

Order placed, payment fails, order-service tries to compensate but inventory is down:
`releaseStock` catches the exception per item and logs. Order is still `CANCELLED`; stock
**not** restored until manual re-add. Good demonstration of the compensation being
best-effort (see Flow 5 expert notes).

---

## 11. Database Verification (the ground truth)

| Service | Local (H2) JDBC URL | Docker DB |
|---------|---------------------|-----------|
| order-service | `jdbc:h2:mem:testdb` (`/h2-console`) | `order_db` (MySQL, host `3308`) |
| inventory-service | `jdbc:h2:mem:testdb` | `inventory_db` (3307) |
| payment-service | `jdbc:h2:mem:paymentdb` | `payment_db` (3309) |
| user-service | `jdbc:h2:mem:userdb` | `user_db` (3310) |

Key tables and what to look for after each flow:

```sql
-- order-service
SELECT order_number, status FROM orders ORDER BY id DESC LIMIT 10;
SELECT * FROM order_line_items WHERE order_id = <id>;
SELECT status, retry_count, aggregate_id FROM Out_Box ORDER BY created_at DESC LIMIT 10;

-- payment-service
SELECT payment_id, order_number, status, failure_reason, gateway_transaction_id
FROM payments ORDER BY id DESC LIMIT 10;

-- inventory-service
SELECT sku_code, quantity, status FROM inventory ORDER BY sku_code;

-- user-service
SELECT id, email, role, status FROM users;
```

**Assertions per flow:** after happy path, `orders.status='CONFIRMED'` and
`payments.status='COMPLETED'`; after payment-failure, both are `CANCELLED`/`FAILED` and stock
is back; after refund, `payments.status='REFUNDED'`, `orders.status='CANCELLED'`, stock back.

---

## 12. Observability Toolbox

| Tool | URL | Use |
|------|-----|-----|
| Kafdrop | `http://localhost:9000` | Inspect topics, replay/duplicate messages, watch retry/DLT |
| Swagger UI per service | `/swagger-ui.html` on each port | Explore/execute endpoints |
| H2 console | `/h2-console` on each service | Direct DB inspection |
| Actuator health | `/actuator/health` per service | Liveness/readiness probes (K8s-ready) |
| Logs | `docker compose logs -f <service>` | Trace each hop; look for `INFO` event markers |

**Log markers to grep for while testing:**
- `OrderPlaceEvent for … published to Kafka (attempt 1)` — order event delivered.
- `Processing payment … for order …` → `Payment COMPLETED for order …` — gateway outcome.
- `Order … CONFIRMED after payment …` / `CANCELLED after payment failure …` /
  `CANCELLED after refund of payment …` — consumer transitions.
- `Stock depleted for SKU …` / `Low stock for SKU …` — inventory events.
- `All 3 publish attempts exhausted … Saving to Outbox` — outbox triggered.
- `message moved to DLQ … for operator review` — DLT hit.

---

## 13. Quick-Reference Cheat Sheet

**Order state machine (the single most important diagram):**
```
PENDING ──stock reserved──▶ RESERVED ──payment ok──▶ CONFIRMED ──▶ SHIPPED ──▶ DELIVERED
    │                            │                        │
    └──(insufficient stock)──────┘                        └──refunded──▶ CANCELLED
                                 └──payment failed──▶ CANCELLED
```

**Who compensates what (memorize the pairs):**

| Failed step | Compensation | Triggered by |
|-------------|--------------|--------------|
| Stock reservation | Order → `CANCELLED`, no event sent | `OutOfStockException` → 409 |
| Payment | Order `RESERVED → CANCELLED` + `addStock` (release) | `PaymentFailedEvent` |
| Confirmed order (post-payment) | Order `CONFIRMED → CANCELLED` + `addStock` (release) | `PaymentRefundedEvent` |

**Reliability contract per publish (great one-liner for interviews):**

| Event | Publish strategy | If Kafka down |
|-------|------------------|---------------|
| `OrderPlaceEvent` | Blocking, 3× (2s/4s), then **Outbox** | Restored on broker restart (~2s) |
| `PaymentCompleted/Failed/RefundedEvent` | 3× (500ms), then log ERROR | **Lost** (acknowledged gap) |
| `OrderStatusChangedEvent` | Async best-effort | Logged only (SSE still works) |

---

## 14. Ten Interview-Readiness Takeaways

If you internalize only ten things from this system, make them these:

1. **Saga orchestration** = a sequence of local transactions + compensating actions;
   order-service is the orchestrator, inventory/payment/notification are participants.
2. **Asynchronous boundary at payment** — the API returns `201/RESERVED` immediately; the
   final outcome is delivered asynchronously (event → state machine) and pushed realtime.
3. **Idempotency key = `orderNumber`** on every consumer; guards on the state machine make
   duplicate/late events harmless.
4. **`orderNumber` as Kafka message key** guarantees per-order ordering across hops.
5. **Outbox-as-fallback** for `OrderPlaceEvent` — publish retried 3× then persisted, relayed
   on a 2s poller; no event lost when Kafka is down.
6. **`@RetryableTopic` + DLQ** for payment consumption — 4 attempts, exponential backoff,
   dead-lettered for operator review, manual ack (no silent loss).
7. **Atomic conditional update** `UPDATE … WHERE quantity >= :qty` prevents overselling
   without locks — the DB is the concurrency authority.
8. **Two realtime paths** — SSE (direct, per-customer, with connect-time replay) and
   WebSocket/STOMP (via Kafka fan-out). Redundancy = resilience.
9. **Compensation is best-effort** — `releaseStock` per-item try/catch; a senior knows this
   gap and would fix it with retry/outbox on the compensation path too.
10. **Known honest gaps to name when asked "what would you improve?"**: transactional outbox
    for payment events, partial refunds + `Refund` entity, real RBAC from the JWT `role`
    claim, low-stock alert consumers, service discovery for the gateway, Redis pub/sub broker
    for multi-instance WebSocket fan-out.
