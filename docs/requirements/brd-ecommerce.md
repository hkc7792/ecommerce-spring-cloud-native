# Business Requirements Document (BRD) — ShopEase E-Commerce Platform

## Document Control

| Field              | Value                                    |
|--------------------|------------------------------------------|
| Project Name       | ShopEase — Cloud-Native E-Commerce       |
| Version            | 1.0                                      |
| Status             | Active Development                       |
| Business Owner     | E-Commerce Division                      |
| Technical Lead     | Platform Engineering Team                |

---

## 1. Executive Summary

ShopEase is a modern, cloud-native e-commerce platform that enables consumers to browse products, place orders, make payments, and track deliveries through a unified digital experience. The platform is built as a microservices architecture to support independent team ownership, rapid feature delivery, and horizontal scaling for high-traffic events such as flash sales and festival seasons.

### Business Objectives
1. **Revenue Growth**: Enable ₹50 Cr+ annual GMV (Gross Merchandise Value) within 18 months of launch
2. **Customer Acquisition**: Onboard 500,000+ registered users in Year 1
3. **Operational Efficiency**: Reduce order processing time from 45 minutes (legacy system) to under 2 minutes
4. **Scalability**: Handle 10x traffic spikes during promotional events without degradation
5. **Vendor Ecosystem**: Support 200+ third-party sellers with self-service onboarding

---

## 2. Business Context

### 2.1 Current Pain Points (Legacy System)
- **Monolithic Architecture**: Single deployment unit; a bug in the payment module brings down the entire platform
- **Inventory Overselling**: Lack of real-time stock tracking leads to 8% order cancellation rate due to stockouts
- **Deployment Bottleneck**: 2-week release cycles due to monolith coupling; teams block each other
- **No Event Visibility**: No audit trail for order state changes; customer support operates blindly
- **Poor Peak Performance**: System crashes during Diwali and year-end sales (2,000+ orders/min overwhelm the single DB)

### 2.2 Target State
A distributed, event-driven platform where:
- Each business domain (Orders, Inventory, Payments, Users) operates as an **independent, deployable service**
- All state changes produce **domain events** consumed by interested services asynchronously
- The system can **scale horizontally** — add more instances of the bottleneck service during peak load
- Teams can **deploy independently** on their own release cadence
- Every order state transition is **auditable** and **recoverable**

---

## 3. Stakeholders

| Stakeholder            | Role                           | Interest                                          |
|------------------------|--------------------------------|---------------------------------------------------|
| End Customers          | Buyers on the platform         | Fast, reliable shopping experience                |
| Sellers / Vendors      | Product suppliers               | Inventory management, order visibility             |
| Customer Support       | Issue resolution                | Order tracking, payment status, refund processing |
| Warehouse Operations   | Fulfillment                     | Pick lists, stock levels, replenishment alerts    |
| Finance Team           | Revenue reconciliation          | Payment reports, refund tracking, GST compliance  |
| Platform Engineering   | System design & maintenance     | Scalability, reliability, observability            |
| Product Management     | Feature prioritization          | Conversion rates, user engagement metrics          |

---

## 4. Functional Requirements

> **Status legend:** ✅ implemented · ◑ partially implemented · ⬜ not implemented (future scope).
> Verified against source code on 2026-08-09 — see [Implementation Status](../implementation-status.md).

### 4.1 User Management (user-service)

| ID       | Requirement                                                                    | Priority  | Status |
|----------|--------------------------------------------------------------------------------|-----------|--------|
| UM-001   | Users can register with email, phone number, and password                      | Must Have | ✅ |
| UM-002   | Users can log in and receive a JWT token valid for 24 hours                    | Must Have | ✅ |
| UM-003   | Users can manage multiple shipping addresses (add, edit, delete, set default)  | Must Have | ◑ add only |
| UM-004   | Users can update profile information (name, phone, email)                      | Must Have | ◑ read only |
| UM-005   | Password reset via email OTP with 10-minute expiry                             | Must Have | ⬜ |
| UM-006   | Social login (Google, Facebook) as alternative authentication                  | Nice to Have | ⬜ |
| UM-007   | Admin users can view/search/disable customer accounts                          | Must Have | ⬜ |
| UM-008   | User preferences (language, currency, notification channels) are persisted     | Nice to Have | ⬜ |

### 4.2 Product Catalog & Inventory (inventory-service)

| ID       | Requirement                                                                    | Priority  | Status |
|----------|--------------------------------------------------------------------------------|-----------|--------|
| INV-001  | Each product variant is tracked by a unique SKU code                           | Must Have | ✅ |
| INV-002  | Real-time stock quantity is maintained per SKU                                 | Must Have | ✅ |
| INV-003  | Stock status auto-updates: `IN_STOCK` (qty > 0) / `OUT_OF_STOCK` (qty = 0)  | Must Have | ✅ |
| INV-004  | Bulk stock check: given a list of SKUs, return availability for each           | Must Have | ✅ |
| INV-005  | Stock addition: add quantity to existing SKU or create new SKU entry           | Must Have | ✅ |
| INV-006  | Stock reduction: atomically deduct quantity; reject if insufficient            | Must Have | ✅ |
| INV-007  | Low-stock alerts when quantity falls below configurable threshold              | Should Have | ✅ |
| INV-008  | Stock reservation: temporarily hold stock during checkout (TTL: 15 minutes)   | Should Have | ⬜ |
| INV-009  | Stock history log: audit trail of all add/reduce operations with timestamp     | Should Have | ⬜ |
| INV-010  | Warehouse-level stock tracking (multi-warehouse support)                       | Nice to Have | ⬜ |

### 4.3 Order Management (order-service)

| ID       | Requirement                                                                    | Priority  | Status |
|----------|--------------------------------------------------------------------------------|-----------|--------|
| ORD-001  | Customers can place orders with one or more line items                         | Must Have | ✅ |
| ORD-002  | Each order receives a unique, system-generated order number (UUID)             | Must Have | ✅ |
| ORD-003  | Order must validate: customer ID > 0, at least 1 line item, price > 0, qty > 0| Must Have | ✅ |
| ORD-004  | Order total is calculated as sum of (price × quantity) for all line items      | Must Have | ✅ |
| ORD-005  | Upon placement, an `OrderPlaceEvent` is published to Kafka for downstream processing | Must Have | ✅ |
| ORD-006  | If Kafka is unavailable, events are saved to an Outbox table and retried      | Must Have | ✅ |
| ORD-007  | Order status tracks full lifecycle: PENDING → RESERVED → CONFIRMED → SHIPPED → DELIVERED | Should Have | ◑ SHIPPED/DELIVERED pending |
| ORD-008  | Customers can view order history filtered by date range and status             | Must Have | ⬜ |
| ORD-009  | Customers can cancel orders in PENDING or RESERVED status                      | Must Have | ⬜ |
| ORD-010  | Order cancellation triggers compensating transactions (release stock, refund)  | Should Have | ◑ compensation wired on payment-fail/refund; no cancel endpoint |
| ORD-011  | Admin dashboard: view all orders, filter by status/date/customer               | Should Have | ⬜ |

### 4.4 Payment Processing (payment-service)

| ID       | Requirement                                                                    | Priority  | Status |
|----------|--------------------------------------------------------------------------------|-----------|--------|
| PAY-001  | Support multiple payment methods: Credit Card, Debit Card, UPI, Net Banking, Wallet | Must Have | ◑ enum + simulated gateway |
| PAY-002  | Process payment upon receiving `OrderPlaceEvent` from Kafka                   | Must Have | ✅ |
| PAY-003  | Publish `PaymentCompletedEvent` or `PaymentFailedEvent` after processing      | Must Have | ✅ |
| PAY-004  | Full refund for cancelled orders within 7 days                                 | Must Have | ✅ |
| PAY-005  | Partial refund for returned items                                              | Should Have | ⬜ |
| PAY-006  | Payment transaction log with gateway reference ID, amount, status, timestamp  | Must Have | ✅ |
| PAY-007  | Retry failed payments up to 3 times with exponential backoff                   | Must Have | ◑ consume retry+DLQ; no payment re-processing |
| PAY-008  | Generate GST-compliant invoices for each successful payment                    | Should Have | ⬜ |
| PAY-009  | Daily settlement reports for finance reconciliation                            | Should Have | ⬜ |
| PAY-010  | PCI-DSS compliance: no card data stored; delegate to payment gateway           | Must Have | ✅ by design (simulated) |

---

## 5. Non-Functional Requirements

### 5.1 Performance

| Metric                  | Target                        | Measurement                      |
|-------------------------|-------------------------------|----------------------------------|
| Order placement latency | < 500ms (p95)                 | From API request to 201 response |
| Stock check latency     | < 100ms (p95)                 | Per SKU lookup                   |
| Kafka publish latency   | < 200ms (p95)                 | From send to broker ack          |
| Peak throughput         | 2,000 orders/minute           | Load test with JMeter/Gatling    |
| Database query time     | < 50ms for indexed queries    | MySQL slow query log             |

### 5.2 Availability & Reliability

| Metric                  | Target                        |
|-------------------------|-------------------------------|
| Uptime SLA              | 99.9% (8.76 hours downtime/year) |
| RTO (Recovery Time)     | < 15 minutes                  |
| RPO (Recovery Point)    | < 1 minute (with Kafka replay)|
| Zero data loss          | Guaranteed via Outbox pattern |
| Graceful degradation    | Payment failures don't block order creation |

### 5.3 Scalability

| Dimension               | Approach                                      |
|--------------------------|-----------------------------------------------|
| Horizontal scaling       | Stateless services; scale via container replicas |
| Database scaling         | Read replicas for queries; sharding for high-write services |
| Kafka partitioning       | Partition by `orderNumber` for ordered processing |
| Connection pooling       | HikariCP with tuned pool sizes per service    |

### 5.4 Security

| Requirement              | Implementation                                |
|--------------------------|-----------------------------------------------|
| Authentication           | JWT tokens (RS256) via User Service            |
| Authorization            | Role-based: CUSTOMER, SELLER, ADMIN            |
| Data encryption          | TLS 1.3 in transit; AES-256 at rest            |
| API rate limiting        | 100 req/min per user; 1000 req/min per service |
| Secrets management       | Environment variables (dev); Vault (production)|
| SQL injection prevention | Parameterized queries via JPA                  |
| Input validation         | Jakarta Bean Validation on all DTOs            |

---

## 6. Business Rules

| Rule ID | Description                                                                              |
|---------|------------------------------------------------------------------------------------------|
| BR-001  | An order cannot be placed if any line item has insufficient stock                       |
| BR-002  | Order cancellation is only allowed in PENDING or RESERVED states                        |
| BR-003  | Refunds are processed within 5-7 business days to the original payment method           |
| BR-004  | Maximum 10 unique SKUs per order; maximum quantity per SKU per order is 5                |
| BR-005  | Free shipping for orders above ₹499; flat ₹49 shipping below ₹499                      |
| BR-006  | GST is calculated at the product category level (5%, 12%, 18%, or 28%)                  |
| BR-007  | Duplicate order detection: same customer + same items within 5 minutes → flag for review |
| BR-008  | Stock replenishment alerts trigger when SKU quantity drops below 10 units                |

---

## 7. Integration Points

| External System          | Integration Method | Purpose                               |
|--------------------------|--------------------|---------------------------------------|
| Payment Gateway (Razorpay/Stripe) | REST API   | Payment processing, refunds           |
| Email Service (SendGrid/SES)      | REST API   | Transactional emails (order confirm)  |
| SMS Gateway (Twilio/MSG91)        | REST API   | OTP, delivery notifications           |
| Logistics Partner (Delhivery/Shiprocket) | Webhook | Shipping tracking updates       |
| GST Filing System                 | Batch export | Monthly tax compliance reports       |

---

## 8. Success Metrics

| KPI                           | Baseline (Legacy) | Target (ShopEase) | Measurement Period |
|-------------------------------|--------------------|--------------------|---------------------|
| Order Cancellation Rate       | 8%                 | < 2%               | Monthly             |
| Average Order Processing Time | 45 minutes         | < 2 minutes        | Per order           |
| System Uptime                 | 95%                | 99.9%              | Monthly             |
| Deployment Frequency          | Bi-weekly           | Daily (per service)| Weekly              |
| Customer Support Tickets      | 500/day            | < 100/day          | Weekly              |
| Cart Abandonment Rate         | 72%                | < 55%              | Monthly             |

---

## 9. Phased Delivery Plan

> Status reflects verified implementation as of 2026-08-09 — see [Implementation Status](../implementation-status.md).

### Phase 1 — Foundation (Months 1-3) ✅ Completed
- [x] Order Service with Kafka event publishing
- [x] Inventory Service with stock management
- [x] Outbox pattern for guaranteed delivery
- [x] Docker Compose for local development
- [x] Commons library for shared DTOs and clients

### Phase 2 — Core Commerce (Months 4-6) ◑ Mostly complete
- [x] Payment Service — implemented with a **simulated** gateway (PAY-002/003/004 ✅);
      real Razorpay/Stripe integration is **future scope** (PAY-001/005/008/009 ⬜)
- [x] User Service with JWT authentication (UM-001/002 ✅; reset, RBAC, admin ⬜)
- [~] Order status state machine — PENDING→RESERVED→CONFIRMED/CANCELLED wired;
      SHIPPED/DELIVERED pending (ORD-007)
- [x] Saga orchestration for order fulfillment — saga + compensating transactions
      (stock release on payment-fail/refund)
- [~] API Gateway — routing + CORS done; **rate limiting pending**

### Phase 3 — Operational Excellence (Months 7-9) ⬜ Future scope
- [ ] Distributed tracing (Zipkin/Jaeger)
- [ ] Centralized logging (ELK Stack)
- [ ] Kubernetes deployment manifests
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Load testing (Gatling) and performance tuning

### Phase 4 — Growth Features (Months 10-12) ⬜ Future scope
- [ ] Product search with Elasticsearch
- [ ] Recommendation engine
- [ ] Multi-warehouse inventory
- [ ] Seller portal and onboarding
- [ ] Mobile app BFF (Backend for Frontend)
