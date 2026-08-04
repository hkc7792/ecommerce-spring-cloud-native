# User Stories — ShopEase E-Commerce Platform

## Story Format
Each story follows the standard format:
> **As a** [role], **I want to** [action], **so that** [benefit].

Acceptance criteria use the Given-When-Then (GWT) structure.

---

## Epic 1: User Registration & Authentication

### US-101: Customer Registration
**As a** new customer, **I want to** register with my email and phone number, **so that** I can place orders on the platform.

**Acceptance Criteria:**
- **Given** I am on the registration page
- **When** I submit a valid email, phone number (10 digits), full name, and password (min 8 chars, 1 uppercase, 1 number)
- **Then** my account is created, I receive a verification email, and I am redirected to the login page

**Technical Notes:**
- POST `/api/user/register`
- Password stored as BCrypt hash (strength 12)
- Email uniqueness enforced at DB level

**Story Points:** 5  
**Priority:** Must Have

---

### US-102: Customer Login
**As a** registered customer, **I want to** log in with my email and password, **so that** I can access my account and place orders.

**Acceptance Criteria:**
- **Given** I have a verified account
- **When** I submit valid credentials
- **Then** I receive a JWT access token (24h expiry) and a refresh token (7d expiry)
- **And** invalid credentials return a 401 error with message "Invalid email or password"
- **And** after 5 consecutive failed attempts, the account is locked for 30 minutes

**Story Points:** 5  
**Priority:** Must Have

---

### US-103: Manage Shipping Addresses
**As a** customer, **I want to** save multiple shipping addresses, **so that** I can quickly select a delivery address during checkout.

**Acceptance Criteria:**
- **Given** I am logged in
- **When** I add a new address with label (Home/Office/Other), street, city, state, pincode (6 digits), and phone
- **Then** the address is saved to my profile and available during checkout
- **And** I can set one address as the default
- **And** I can have a maximum of 5 saved addresses

**Story Points:** 3  
**Priority:** Must Have

---

### US-104: Password Reset
**As a** customer who forgot my password, **I want to** reset it via email OTP, **so that** I can regain access to my account.

**Acceptance Criteria:**
- **Given** I request a password reset for a valid email
- **When** I enter the 6-digit OTP received within 10 minutes
- **Then** I can set a new password and log in immediately
- **And** expired or invalid OTPs return a 400 error

**Story Points:** 5  
**Priority:** Must Have

---

## Epic 2: Inventory Management

### US-201: Check Product Availability
**As a** customer browsing products, **I want to** see real-time stock availability, **so that** I know if an item can be ordered.

**Acceptance Criteria:**
- **Given** I am viewing a product page
- **When** the page loads
- **Then** the stock status is displayed: "In Stock" (green) if quantity > 0, "Out of Stock" (red) if quantity = 0
- **And** the API supports bulk checking for cart items (up to 20 SKUs in one call)

**Technical Notes:**
- GET `/api/inventory/items?skuCode=SKU001&skuCode=SKU002`
- Returns `List<InventoryResponse>` with `skuCode` and `isInStock` fields
- **Already implemented** in `InventoryController.isInStock()`

**Story Points:** 3  
**Priority:** Must Have ✅ Implemented

---

### US-202: Add Stock to Inventory
**As a** warehouse manager, **I want to** add stock for a product SKU, **so that** customers can purchase it.

**Acceptance Criteria:**
- **Given** I provide a valid SKU code and quantity ≥ 1
- **When** the SKU already exists → the quantity is added to the current stock
- **When** the SKU does not exist → a new inventory record is created with status `IN_STOCK`
- **Then** the inventory is updated and a 201 response is returned
- **And** invalid input (blank SKU, quantity < 1) returns a 400 validation error

**Technical Notes:**
- POST `/api/inventory/add` with body `{ "skuCode": "SKU001", "quantity": 50 }`
- Transactional with rollback on any exception
- **Already implemented** in `InventoryServiceImpl.addInventory()`

**Story Points:** 3  
**Priority:** Must Have ✅ Implemented

---

### US-203: Reduce Stock on Order Fulfillment
**As the** order system, **I want to** atomically reduce stock for ordered items, **so that** inventory accurately reflects available quantity.

**Acceptance Criteria:**
- **Given** a list of SKUs with quantities to reduce
- **When** all items have sufficient stock
- **Then** stock is deducted atomically and status updated (`IN_STOCK` / `OUT_OF_STOCK`)
- **When** any item has insufficient stock
- **Then** the entire operation rolls back and a `RuntimeException` is thrown
- **And** no partial deductions occur (all-or-nothing)

**Technical Notes:**
- POST `/api/inventory/reduce` with body `[{ "skuCode": "SKU001", "quantity": 2 }]`
- Uses `@Transactional(rollbackFor = Exception.class)` for atomicity
- **Already implemented** in `InventoryServiceImpl.reduceStock()`

**Story Points:** 5  
**Priority:** Must Have ✅ Implemented

---

### US-204: Low Stock Alerts
**As a** warehouse manager, **I want to** receive alerts when stock drops below a threshold, **so that** I can reorder before stockouts.

**Acceptance Criteria:**
- **Given** a configurable threshold per SKU (default: 10 units)
- **When** a stock reduction brings quantity below the threshold
- **Then** a `LowStockAlertEvent` is published to Kafka topic `inventory-alerts`
- **And** the event includes: SKU code, current quantity, threshold, timestamp

**Story Points:** 3  
**Priority:** Should Have

---

## Epic 3: Order Placement

### US-301: Place a New Order
**As a** customer, **I want to** place an order with items from my cart, **so that** I can purchase the products I selected.

**Acceptance Criteria:**
- **Given** I submit an order with my customer ID and a list of line items (SKU, price, quantity)
- **When** customer ID > 0, list is non-empty, all prices > 0, and all quantities > 0
- **Then** the order is persisted with a UUID order number, total amount calculated, and HTTP 201 returned
- **And** an `OrderPlaceEvent` is published to Kafka topic `notificationTopic`
- **When** any validation fails
- **Then** a 400 error is returned with a descriptive message

**Technical Notes:**
- POST `/api/order` with body:
  ```json
  {
    "customerId": 12345,
    "orderLineItemsDtoList": [
      { "skuCode": "IPHONE-15-128GB", "price": 79999.00, "quantity": 1 },
      { "skuCode": "AIRPODS-PRO-2", "price": 24999.00, "quantity": 2 }
    ]
  }
  ```
- **Already implemented** in `OrderController.placeOrder()`

**Story Points:** 8  
**Priority:** Must Have ✅ Implemented

---

### US-302: Guaranteed Event Delivery (Outbox Pattern)
**As the** platform, **I want** order events to be reliably published even when Kafka is temporarily unavailable, **so that** no orders are lost.

**Acceptance Criteria:**
- **Given** an `OrderPlaceEvent` fails to publish to Kafka after 3 retries (2s backoff, 2x multiplier)
- **When** the `@Recover` method is invoked
- **Then** the event is serialized as JSON and saved to the `Out_Box` table with status `PENDING`
- **And** the `OutboxRelayWorker` polls every 30 seconds and retries publishing
- **And** successfully published events are marked as `PUBLISH`
- **And** events that fail 5 times are marked as `FAILED` (dead letter)

**Technical Notes:**
- Outbox statuses: `PENDING` → `PROCESSING` → `PUBLISH` | `FAILED`
- **Already implemented** in `OutboxService` and `OutboxRelayWorker`

**Story Points:** 8  
**Priority:** Must Have ✅ Implemented

---

### US-303: View Order History
**As a** customer, **I want to** view my past orders, **so that** I can track their status and reorder items.

**Acceptance Criteria:**
- **Given** I am logged in
- **When** I navigate to "My Orders"
- **Then** I see a paginated list (20 per page) of my orders sorted by date (newest first)
- **And** each order shows: order number, date, total amount, status, and item count
- **And** I can filter by status (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)

**Story Points:** 5  
**Priority:** Must Have

---

### US-304: Cancel an Order
**As a** customer, **I want to** cancel an order that hasn't been shipped, **so that** I can get a refund if I change my mind.

**Acceptance Criteria:**
- **Given** my order is in `PENDING` or `RESERVED` status
- **When** I request cancellation
- **Then** the order status changes to `CANCELLED`
- **And** reserved stock is released back to inventory
- **And** if payment was processed, a refund is initiated
- **When** my order is in `CONFIRMED`, `SHIPPED`, or `DELIVERED` status
- **Then** the cancellation is rejected with message "Order cannot be cancelled in current state"

**Story Points:** 5  
**Priority:** Must Have

---

## Epic 4: Payment Processing

### US-401: Process Payment for Confirmed Orders
**As the** payment system, **I want to** charge customers when an order is confirmed, **so that** revenue is captured.

**Acceptance Criteria:**
- **Given** a `OrderPlaceEvent` is received from Kafka
- **When** the payment method is valid and funds are sufficient
- **Then** the payment gateway is called, amount is charged, and `PaymentCompletedEvent` is published
- **When** the payment fails
- **Then** `PaymentFailedEvent` is published with reason (insufficient funds, card declined, gateway error)
- **And** the order service marks the order as CANCELLED and releases inventory

**Story Points:** 13  
**Priority:** Must Have

---

### US-402: Full Refund on Order Cancellation
**As a** customer who cancelled an order, **I want to** receive a full refund, **so that** I get my money back.

**Acceptance Criteria:**
- **Given** my order was `CONFIRMED` (payment already charged) and I request cancellation
- **When** the refund is initiated
- **Then** the full amount is credited to the original payment method within 5-7 business days
- **And** a `RefundProcessedEvent` is published
- **And** the refund transaction is logged with reference ID, amount, and status

**Story Points:** 8  
**Priority:** Must Have

---

### US-403: View Payment History
**As a** customer, **I want to** view my payment transaction history, **so that** I can reconcile charges with my bank.

**Acceptance Criteria:**
- **Given** I am logged in
- **When** I navigate to "Payment History"
- **Then** I see a list of all transactions: order number, amount, payment method, status, date
- **And** I can download a receipt for each successful payment (PDF)

**Story Points:** 5  
**Priority:** Should Have

---

## Epic 5: Notifications & Communication

### US-501: Order Confirmation Email
**As a** customer, **I want to** receive an email confirmation when my order is placed, **so that** I have a record of my purchase.

**Acceptance Criteria:**
- **Given** an order is successfully placed
- **When** the `OrderPlaceEvent` is consumed by the notification service
- **Then** an email is sent to the customer's registered email with: order number, items, total amount, estimated delivery date

**Story Points:** 5  
**Priority:** Must Have

---

### US-502: Shipping Status Notifications
**As a** customer, **I want to** receive SMS/email updates when my order is shipped and delivered, **so that** I know when to expect it.

**Acceptance Criteria:**
- **Given** the order status changes to `SHIPPED`
- **Then** a notification is sent with tracking number and estimated delivery date
- **Given** the order status changes to `DELIVERED`
- **Then** a notification is sent with delivery confirmation and feedback request link

**Story Points:** 5  
**Priority:** Should Have

---

## Story Map Summary

| Epic                  | Must Have | Should Have | Nice to Have | Total |
|-----------------------|-----------|-------------|--------------|-------|
| User Management       | 4         | 0           | 2            | 6     |
| Inventory Management  | 3 ✅      | 2           | 1            | 6     |
| Order Placement       | 3 (2 ✅) | 1           | 0            | 4     |
| Payment Processing    | 2         | 1           | 0            | 3     |
| Notifications         | 1         | 1           | 0            | 2     |
| **Total**             | **13**    | **5**       | **3**        | **21** |

---

## Definition of Done (DoD)

A story is considered **Done** when:
- [ ] Code passes all unit tests (≥ 80% coverage on service layer)
- [ ] Code reviewed and approved by at least 1 peer
- [ ] Integration tests pass with Testcontainers
- [ ] API documentation updated (request/response examples)
- [ ] Docker Compose tested end-to-end
- [ ] No critical or high-severity SonarQube issues
- [ ] Observability: Actuator endpoints return healthy status
