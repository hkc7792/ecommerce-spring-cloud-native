# Payment Service — API Documentation (Planned)

## Service Overview

| Property         | Value                                                |
|------------------|------------------------------------------------------|
| Service Name     | `payment-service`                                     |
| Base URL         | `http://localhost:8083/api/payment`                   |
| Database         | `payment_db` (MySQL 8.x, port 3309)                  |
| Package          | `com.ecommerce.payment`                               |
| Spring Boot      | 3.4.1                                                 |
| Java             | 21                                                    |
| Status           | 🔲 **Planned** — Not yet implemented                 |

---

## Architecture Context

The Payment Service is a critical component in the order fulfillment saga. It consumes `OrderPlaceEvent` messages from Kafka, processes payments through an external gateway (Razorpay/Stripe), and publishes the outcome back to Kafka for the Order Service to act on.

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
| Auth        | JWT Bearer token (Customer or Admin)               |
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
  "gatewayTransactionId": "razorpay_txn_9876543210",
  "status": "COMPLETED",
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
| Auth        | JWT Bearer token                                   |
| Status      | `200 OK`                                           |

#### Query Parameters

| Parameter  | Type      | Required | Default | Description                     |
|------------|-----------|----------|---------|---------------------------------|
| `page`     | `Integer` | No       | `0`     | Page number (0-indexed)         |
| `size`     | `Integer` | No       | `20`    | Items per page                  |
| `status`   | `String`  | No       | All     | Filter: `COMPLETED`, `FAILED`, `REFUNDED` |

#### Response — 200 OK
```json
{
  "content": [
    {
      "paymentId": "pay_a1b2c3d4e5f6",
      "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "amount": 129997.00,
      "currency": "INR",
      "paymentMethod": "UPI",
      "status": "COMPLETED",
      "paidAt": "2026-08-04T12:35:00Z"
    },
    {
      "paymentId": "pay_g7h8i9j0k1l2",
      "orderNumber": "b2c3d4e5-f6g7-8901-bcde-fg2345678901",
      "amount": 4999.00,
      "currency": "INR",
      "paymentMethod": "CREDIT_CARD",
      "status": "REFUNDED",
      "paidAt": "2026-08-03T10:15:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

---

### 3. Initiate Refund

Initiates a full or partial refund for a completed payment.

| Property    | Value                                             |
|-------------|---------------------------------------------------|
| Method      | `POST`                                             |
| Path        | `/api/payment/refund`                              |
| Auth        | JWT Bearer token (Admin or System)                 |
| Status      | `202 Accepted`                                     |

#### Request Body
```json
{
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "refundAmount": 24999.00,
  "reason": "ITEM_RETURNED",
  "notes": "Customer returned AIRPODS-PRO-2 (damaged packaging)"
}
```

#### Request Schema

| Field          | Type          | Required | Validation                                 |
|----------------|---------------|----------|--------------------------------------------|
| `orderNumber`  | `String`      | Yes      | Must match an existing completed payment   |
| `refundAmount` | `BigDecimal`  | Yes      | Must be > 0 and ≤ original payment amount  |
| `reason`       | `String`      | Yes      | Enum: `ORDER_CANCELLED`, `ITEM_RETURNED`, `DUPLICATE_CHARGE`, `OTHER` |
| `notes`        | `String`      | No       | Free-text description (max 500 chars)      |

#### Response — 202 Accepted
```json
{
  "refundId": "rfnd_m3n4o5p6q7r8",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "refundAmount": 24999.00,
  "status": "PROCESSING",
  "estimatedCompletionDate": "2026-08-11",
  "createdAt": "2026-08-04T14:00:00Z"
}
```

#### Response — 409 Conflict
```json
{
  "status": 409,
  "message": "Refund already in progress for order: a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2026-08-04T14:00:00Z"
}
```

---

## Domain Model

### Payment Entity

| Column                   | Type          | Constraints            | Description                           |
|--------------------------|---------------|------------------------|---------------------------------------|
| `id`                     | `BIGINT`      | PK, AUTO_INCREMENT     | Internal surrogate key                |
| `payment_id`             | `VARCHAR(20)` | UNIQUE, NOT NULL       | Business ID (e.g., `pay_a1b2c3d4`)   |
| `order_number`           | `VARCHAR(36)` | NOT NULL, INDEX        | References the associated order       |
| `customer_id`            | `BIGINT`      | NOT NULL               | Customer who made the payment         |
| `amount`                 | `DECIMAL`     | NOT NULL               | Payment amount in smallest currency unit |
| `currency`               | `VARCHAR(3)`  | NOT NULL, Default: INR | ISO 4217 currency code                |
| `payment_method`         | `ENUM`        | NOT NULL               | `CREDIT_CARD`, `DEBIT_CARD`, `UPI`, `NET_BANKING`, `WALLET` |
| `gateway_transaction_id` | `VARCHAR`     | UNIQUE                 | Payment gateway's reference ID        |
| `status`                 | `ENUM`        | NOT NULL               | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `REFUNDED` |
| `failure_reason`         | `VARCHAR`     | Nullable               | Reason for failure (if applicable)    |
| `paid_at`                | `TIMESTAMP`   | Nullable               | When payment was confirmed            |
| `created_at`             | `TIMESTAMP`   | Auto-generated         | Record creation time                  |
| `updated_at`             | `TIMESTAMP`   | Auto-updated           | Last modification time                |

### Refund Entity

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
**Processing**: Extract `orderNumber`, `customerId`, `totalAmount` → call payment gateway → publish result

### Published Events

#### PaymentCompletedEvent (to `paymentTopic`)
```json
{
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": 12345,
  "amount": 129997.00,
  "paymentMethod": "UPI",
  "gatewayTransactionId": "razorpay_txn_9876543210",
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

#### RefundProcessedEvent (to `paymentTopic`)
```json
{
  "refundId": "rfnd_m3n4o5p6q7r8",
  "paymentId": "pay_a1b2c3d4e5f6",
  "orderNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "refundAmount": 24999.00,
  "status": "COMPLETED",
  "completedAt": "2026-08-11T09:00:00Z"
}
```

---

## Payment Gateway Integration (Razorpay)

### Flow
1. Receive `OrderPlaceEvent` from Kafka
2. Look up customer's saved payment method (or use default)
3. Create a Razorpay `Payment` object with amount, currency, and order reference
4. Submit to Razorpay API (`POST https://api.razorpay.com/v1/payments`)
5. Handle response:
   - **Success**: Save payment record with status `COMPLETED`, publish `PaymentCompletedEvent`
   - **Failure**: Save with status `FAILED` and `failureReason`, publish `PaymentFailedEvent`

### Idempotency
- Use `orderNumber` as the idempotency key when calling the gateway
- If a duplicate event is received (same `orderNumber`), check the database first:
  - If payment already `COMPLETED` → skip, publish `PaymentCompletedEvent` again
  - If payment `FAILED` → retry the payment

### Security
- API keys stored in environment variables (dev) / Vault (production)
- No card data stored locally — all card operations delegated to gateway (PCI-DSS compliant)
- All gateway communication over HTTPS/TLS 1.3

---

## Planned Package Structure

```
com.ecommerce.payment/
├── PaymentServiceApplication.java
├── config/
│   ├── KafkaConsumerConfig.java
│   └── RazorpayConfig.java
├── controller/
│   └── PaymentController.java
├── service/
│   ├── PaymentService.java
│   └── impl/
│       ├── PaymentServiceImpl.java
│       └── RefundServiceImpl.java
├── repository/
│   ├── PaymentRepository.java
│   └── RefundRepository.java
├── entities/
│   ├── Payment.java
│   └── Refund.java
├── dto/
│   ├── PaymentResponse.java
│   ├── RefundRequest.java
│   └── RefundResponse.java
├── events/
│   ├── PaymentCompletedEvent.java
│   ├── PaymentFailedEvent.java
│   └── RefundProcessedEvent.java
├── consumer/
│   └── OrderEventConsumer.java
├── gateway/
│   └── RazorpayGatewayClient.java
└── exceptions/
    ├── PaymentNotFoundException.java
    ├── RefundException.java
    └── GlobalExceptionHandler.java
```

---

## Configuration

### Docker Ports (Planned)
| Type             | Host     | Container |
|------------------|----------|-----------|
| Application      | `8083`   | `8080`    |
| Remote Debug     | `5003`   | `5000`    |

### Database (Planned)
| Property                 | Value                                              |
|--------------------------|----------------------------------------------------|
| URL                      | `jdbc:mysql://payment_db_container:3306/payment_db` |
| Host Port                | `3309`                                              |
| Container Port           | `3306`                                              |
| Credentials              | `scott` / `tiger` (dev only)                       |
