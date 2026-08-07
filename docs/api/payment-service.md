# Payment Service — API Documentation

## Service Overview

| Property         | Value                                                |
|------------------|------------------------------------------------------|
| Service Name     | `payment-service`                                     |
| Base URL         | `http://localhost:8083/api/payment`                   |
| Database         | `payment_db` (MySQL 8.x, port 3309)                  |
| Package          | `com.ecommerce.payment`                               |
| Spring Boot      | 3.4.1                                                 |
| Java             | 21                                                    |
| Status           | ✅ **Implemented** — simulated gateway; Razorpay integration planned |

---

## Architecture Context

The Payment Service is a critical component in the order fulfillment saga. It consumes `OrderPlaceEvent` messages from Kafka, processes payments through a **simulated gateway** (a real gateway such as Razorpay/Stripe is planned for production), and publishes the outcome back to `paymentTopic` for the Order Service to act on. Consumption uses `@RetryableTopic` with a dead-letter topic, so a processing failure is never silently dropped.

```
Order Service                 Kafka                    Payment Service             Payment Gateway
     │                          │                           │                          │
     │──OrderPlaceEvent───────▶│                           │                          │
     │                          │──────────────────────────▶│                          │
     │                          │                           │──charge(amount)─────────▶│
     │                          │                           │◀────── success/fail ─────│
     │                          │◀─PaymentCompleted/Failed──│                          │
     │◀─────────────────────────│                           │                          │
```

---

## Endpoints

### 1. Get Payment by Order Number

Retrieves the payment details for a specific order.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `GET`                                              |
| Path        | `/api/payment/{orderNumber}`                       |
| Auth        | None (planned: JWT Bearer token)                   |
| Status      | `200 OK`                                           |

#### Response — 200 OK
```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "currency": "INR",
  "paymentMethod": "UPI",
  "gatewayTransactionId": "gw_txn_9876543210",
  "status": "COMPLETED",
  "failureReason": null,
  "paidAt": "2026-08-04T12:35:00Z",
  "createdAt": "2026-08-04T12:30:00Z"
}
```

#### Response — 404 Not Found
```json
{
  "status": 404,
  "message": "Payment not found for order: a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-08-04T12:30:00Z"
}
```

---

### 2. Get Payment History for Customer

Returns paginated payment history for a customer.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `GET`                                              |
| Path        | `/api/payment/history`                             |
| Auth        | None (planned: JWT Bearer token)                   |
| Status      | `200 OK`                                           |

#### Query Parameters

| Parameter    | Type      | Required | Default | Description                     |
|--------------|-----------|----------|---------|---------------------------------|
| `customerId` | `Long`    | Yes      | —       | Customer whose payments to list |
| `page`       | `Integer` | No       | `0`     | Page number (0-indexed)         |
| `size`       | `Integer` | No       | `20`    | Items per page                  |
| `status`     | `String`  | No       | All     | Filter: `COMPLETED`, `FAILED`, `REFUNDED` |

#### Response — 200 OK
```json
{
  "content": [
    {
      "paymentId": "pay_a1b2c3d4e5f6",
      "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "customerId": 12345,
      "amount": 129997.00,
      "currency": "INR",
      "paymentMethod": "UPI",
      "gatewayTransactionId": "gw_txn_9876543210",
      "status": "COMPLETED",
      "failureReason": null,
      "paidAt": "2026-08-04T12:35:00Z",
      "createdAt": "2026-08-04T12:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

#### cURL Example
```bash
curl "http://localhost:8083/api/payment/history?customerId=12345&page=0&size=20&status=COMPLETED"
```

---

### 3. Refund a Payment

Refunds a **completed** (COMPLETED) payment for an order. The payment is marked `REFUNDED` in place, and a `PaymentRefundedEvent` is published to `paymentTopic` so the Order Service cancels the confirmed order and releases the reserved stock (compensating transaction). The simulated gateway means no external refund call is made — a real gateway refund (idempotent on `paymentId`/`gatewayTransactionId`) is the planned production wiring.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/payment/{orderNumber}/refund`                |
| Auth        | None (planned: JWT Bearer token)                   |
| Status      | `200 OK`                                           |

#### Path Parameters

| Parameter     | Type     | Description                          |
|---------------|----------|--------------------------------------|
| `orderNumber` | `String` | Order whose payment should be refunded |

#### Response — 200 OK
```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "currency": "INR",
  "paymentMethod": "UPI",
  "gatewayTransactionId": "gw_txn_9876543210",
  "status": "REFUNDED",
  "failureReason": "REFUNDED",
  "paidAt": "2026-08-04T12:35:00Z",
  "createdAt": "2026-08-04T12:30:00Z"
}
```

#### Response — 404 Payment Not Found
```json
{
  "status": 404,
  "message": "Payment not found for order: a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-08-04T14:00:00Z"
}
```

#### Response — 409 Conflict (not refundable)
Only `COMPLETED` payments can be refunded; `PENDING` / `PROCESSING` / `FAILED` / already-`REFUNDED` payments are rejected.

```json
{
  "status": 409,
  "message": "Cannot refund payment for order a1b2c3d4-e5f6-7890-abcd-ef1234567890 in status FAILED; only COMPLETED payments can be refunded",
  "timestamp": "2026-08-04T14:00:00Z"
}
```

#### Side Effect — Compensating Transaction
The refund is a saga compensating step: payment-service publishes `PaymentRefundedEvent` → order-service transitions the order `CONFIRMED → CANCELLED` and releases the stock reserved at placement.

---

## Domain Model

### Payment Entity

| Column                   | Type          | Constraints            | Description                           |
|--------------------------|---------------|------------------------|---------------------------------------|
| `id`                     | `BIGINT`      | PK, AUTO_INCREMENT     | Internal surrogate key                |
| `payment_id`             | `VARCHAR(30)` | UNIQUE, NOT NULL       | Business ID (e.g., `pay_a1b2c3d4`)   |
| `order_number`           | `VARCHAR(36)` | NOT NULL, INDEX        | References the associated order       |
| `customer_id`            | `BIGINT`      | NOT NULL               | Customer who made the payment         |
| `amount`                 | `DECIMAL(12,2)` | NOT NULL            | Payment amount in smallest currency unit |
| `currency`               | `VARCHAR(3)`  | NOT NULL, Default: INR | ISO 4217 currency code                |
| `payment_method`         | `ENUM`        | NOT NULL               | `CREDIT_CARD`, `DEBIT_CARD`, `UPI`, `NET_BANKING`, `WALLET` |
| `gateway_transaction_id` | `VARCHAR`     | UNIQUE                 | Payment gateway's reference ID        |
| `status`                 | `ENUM`        | NOT NULL               | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `REFUNDED` |
| `failure_reason`         | `VARCHAR`     | Nullable               | Reason for failure (if applicable)    |
| `paid_at`                | `TIMESTAMP`   | Nullable               | When payment was confirmed            |
| `created_at`             | `TIMESTAMP`   | Auto-generated         | Record creation time                  |
| `updated_at`             | `TIMESTAMP`   | Auto-updated           | Last modification time                |

### Refund Entity — *Planned*

The current implementation handles full refunds in place: the `Payment` row is flipped to `REFUNDED` (with `failure_reason` = `REFUNDED`) and no separate refund record is kept. A dedicated `Refund` entity is a planned follow-up to support partial refunds and a durable refund audit trail.

| Column                   | Type          | Constraints            | Description                           |
|--------------------------|---------------|------------------------|---------------------------------------|
| `id`                     | `BIGINT`      | PK, AUTO_INCREMENT     | Internal surrogate key                |
| `refund_id`              | `VARCHAR(20)` | UNIQUE, NOT NULL       | Business ID (e.g., `rfnd_m3n4o5p6`)  |
| `payment_id`             | `VARCHAR(20)` | FK → `payment.payment_id` | Original payment reference          |
| `order_number`           | `VARCHAR(36)` | NOT NULL               | Order being refunded                  |
| `refund_amount`          | `DECIMAL`     | NOT NULL               | Amount to refund                      |
| `reason`                 | `ENUM`        | NOT NULL               | `ORDER_CANCELLED`, `ITEM_RETURNED`, `DUPLICATE_CHARGE`, `OTHER` |
| `notes`                  | `VARCHAR(500)`| Nullable               | Free-text description                 |
| `status`                 | `ENUM`        | NOT NULL               | `PROCESSING`, `COMPLETED`, `FAILED`  |
| `gateway_refund_id`      | `VARCHAR`     | Nullable               | Gateway's refund reference            |
| `completed_at`           | `TIMESTAMP`   | Nullable               | When refund was confirmed             |
| `created_at`             | `TIMESTAMP`   | Auto-generated         | Refund request time                   |

---

## Kafka Events

### Consumed Events

#### OrderPlaceEvent (from `notificationTopic`)
```json
{
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "totalAmount": 129997.00,
  "orderTime": "2026-08-04T12:30:00Z",
  "items": [...]
}
```

**Consumer Group**: `payment-service-group`  
**Processing**: Extract `orderNumber`, `customerId`, `totalAmount` → call simulated gateway → publish result  
**Reliability**: Listener uses `@RetryableTopic` — a processing failure is retried with exponential backoff (1s → 2s → 4s, 4 total attempts) and then dead-lettered to the auto-created `notificationTopic-dlt` for operator review. `enable-auto-commit=false` + `AckMode.RECORD` ensures offsets only advance after a record is processed or safely routed to retry/DLT.

### Published Events

**Publishing reliability**: every published event (completion / failure / refund) is sent via a small retry helper — 3 attempts with 500ms backoff, blocking on broker acknowledgement (5s timeout). If still failing after 3 attempts the event is logged at ERROR for operator follow-up (an outbox is a planned follow-up).

#### PaymentCompletedEvent (to `paymentTopic`)
```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "paymentMethod": "UPI",
  "gatewayTransactionId": "gw_txn_9876543210",
  "paidAt": "2026-08-04T12:35:00Z"
}
```

#### PaymentFailedEvent (to `paymentTopic`)
```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "failureReason": "INSUFFICIENT_FUNDS",
  "failedAt": "2026-08-04T12:35:00Z"
}
```

#### PaymentRefundedEvent (to `paymentTopic`)

Published when a completed payment is refunded (via `POST /api/payment/{orderNumber}/refund`). Consumed by Order Service to cancel the `CONFIRMED` order and release its stock.

```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "reason": "REFUNDED",
  "refundedAt": "2026-08-04T14:05:00Z"
}
```

---

## Payment Gateway Integration

### Current Implementation (Simulated Gateway)
1. Receive `OrderPlaceEvent` from Kafka (`notificationTopic`)
2. Create a `Payment` record with status `PROCESSING`, random payment method
3. Call `simulatePaymentGateway(amount)` — 200–800ms latency; failure rates are 5% (normal orders) and 20% (orders > ₹100,000)
4. Handle response:
   - **Success**: Save with status `COMPLETED` + `gatewayTransactionId`, publish `PaymentCompletedEvent`
   - **Failure**: Save with status `FAILED` + `failureReason`, publish `PaymentFailedEvent`
5. On `POST /api/payment/{orderNumber}/refund` for a `COMPLETED` payment: mark the payment `REFUNDED` and publish `PaymentRefundedEvent` (compensating transaction → order cancelled, stock released)

### Planned (Production) — Razorpay
- Replace the simulation with a real Razorpay API call (`POST https://api.razorpay.com/v1/payments`)
- API keys stored in environment variables (dev) / Vault (production)
- No card data stored locally — all card operations delegated to gateway (PCI-DSS compliant)
- All gateway communication over HTTPS/TLS 1.3

### Idempotency (Implemented)
- `orderNumber` is the idempotency key — at-least-once Kafka delivery is safe
- On each event, `paymentRepository.existsByOrderNumber(orderNumber)` is checked first: a duplicate order is skipped (no double-charging)

---

## Package Structure

```
com.ecommerce.payment/
├── PaymentServiceApplication.java
├── config/
│   └── KafkaConfig.java            (@EnableKafkaRetryTopic + retry scheduler)
├── controller/
│   └── PaymentController.java
├── service/
│   ├── PaymentService.java
│   └── impl/
│       └── PaymentServiceImpl.java
├── repository/
│   └── PaymentRepository.java
├── entities/
│   └── Payment.java
├── dto/
│   └── PaymentResponse.java
├── consumer/
│   └── OrderEventConsumer.java     (@RetryableTopic + @DltHandler)
└── exceptions/
    ├── PaymentNotFoundException.java
    └── GlobalExceptionHandler.java
```

*Planned additions: a `Refund` entity + repository for partial refunds and audit, and a `RazorpayGatewayClient` to replace the simulated gateway (including real gateway refunds).*

---

## Configuration

### Kafka
| Property                     | Value                                              |
|------------------------------|----------------------------------------------------|
| Bootstrap servers            | `localhost:9092` (dev) / `kafka:29092` (Docker)    |
| Consumer group               | `payment-service-group`                            |
| `enable-auto-commit`         | `false` — manual ack, `ack-mode: record`           |
| Retry / DLT                  | `@RetryableTopic` → `notificationTopic-dlt`        |

### Docker Ports
| Type             | Host     | Container |
|------------------|----------|-----------|
| Application      | `8083`   | `8080`    |
| Remote Debug     | `5003`   | `5000`    |

### Database
| Property                 | Value                                              |
|--------------------------|----------------------------------------------------|
| URL                      | `jdbc:mysql://payment_db_container:3306/payment_db` |
| Host Port                | `3309`                                              |
| Container Port           | `3306`                                              |
| Credentials              | `scott` / `tiger` (dev only)                       |
