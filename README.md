# Cart Checkout + Mock Payment System

A production-grade **Cart Checkout & Payment** backend built with **Java 17 + Spring Boot 3.2**, demonstrating domain-driven design, state machine enforcement, idempotent webhook processing, and data consistency guarantees.

---

## Table of Contents

- [Quick Start](#quick-start)
- [Architecture Overview](#architecture-overview)
- [Domain Model](#domain-model)
- [Order State Machine](#order-state-machine)
- [Payment Safety & Idempotency](#payment-safety--idempotency)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Postman Collection](#postman-collection)
- [Key Architectural Decisions](#key-architectural-decisions)
- [Trade-offs](#trade-offs)
- [Future Extensibility](#future-extensibility)

---

## Quick Start

### Prerequisites
- **Java 17+** (JDK)
- **Maven 3.8+**
- **Docker** (optional, for containerized run)

### Option 1 — Run with Maven (Recommended for development)

```bash
# Clone the repository
git clone https://github.com/mohamedarafat1994/cart-checkout-service.git
cd cart-checkout-service

# Build and run
mvn clean install
mvn spring-boot:run
```

The application starts on **http://localhost:8080**

### Option 2 — Run with Docker

```bash
docker build -t cart-checkout-service .
docker run -p 8080:8080 cart-checkout-service
```

### Option 3 — Docker Compose

```bash
docker-compose up --build
```

### Verify it's running

```bash
curl -X POST http://localhost:8080/carts
```

You should get a `201 Created` response with a cart JSON.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                  Single Spring Boot Service          │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │   Cart   │  │  Order   │  │     Payment       │  │
│  │Controller│  │Controller│  │   Controller      │  │
│  └────┬─────┘  └────┬─────┘  └────┬──────────────┘  │
│       │              │             │                  │
│  ┌────▼─────┐  ┌────▼─────┐  ┌───▼──────────────┐  │
│  │   Cart   │  │  Order   │  │    Payment       │  │
│  │ Service  │  │ Service  │  │   Service        │  │
│  └────┬─────┘  └────┬─────┘  └───┬──────────────┘  │
│       │              │             │                  │
│  ┌────▼─────────────▼─────────────▼──────────────┐  │
│  │              Domain Model                      │  │
│  │  Cart ←→ CartItem    Order ←→ PaymentAttempt  │  │
│  │              OrderStatus (State Machine)       │  │
│  └───────────────────┬───────────────────────────┘  │
│                      │                               │
│  ┌───────────────────▼───────────────────────────┐  │
│  │           H2 In-Memory Database               │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  ┌───────────────────────────────────────────────┐  │
│  │         Mock Payment Provider (@Async)        │  │
│  │    Simulates external gateway + webhook       │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**Layered Architecture:**

| Layer | Responsibility |
|-------|----------------|
| **Controller** | HTTP handling, request validation, DTO mapping |
| **Service** | Business logic, transaction boundaries, orchestration |
| **Domain Model** | Entities with behavior, state machine enforcement, invariants |
| **Repository** | Data access with JPA, locking strategies |

---

## Domain Model

### Entities

| Entity | Key Fields | Purpose |
|--------|-----------|---------|
| `Cart` | id, status, items | Shopping cart with line items |
| `CartItem` | productId, quantity, price | Individual cart line item |
| `Order` | id, cart, status, totalAmount, version | Order created from checkout |
| `PaymentAttempt` | id, order, status, idempotencyKey | Individual payment attempt with idempotency |

### Key Design Choices

- **Rich Domain Model**: Business rules live in entities (e.g., `Cart.addItem()` validates state, `Order.transitionTo()` enforces state machine).
- **Immutable after creation**: Cart is locked on checkout; orders track history via PaymentAttempt records.
- **Factory methods**: `Cart.create()`, `Order.createFromCart()` — no public constructors.

---

## Order State Machine

```
          ┌──────────┐
          │ CREATED  │
          └────┬─────┘
               │ startPayment()
               ▼
     ┌─────────────────┐
     │ PENDING_PAYMENT │◄───────────┐
     └────┬────────┬───┘            │
          │        │           retry │
  webhook │  webhook               │
CONFIRMED │   FAILED               │
          ▼        ▼                │
     ┌────────┐  ┌──────────────┐  │
     │  PAID  │  │PAYMENT_FAILED├──┘
     └────────┘  └──────┬───────┘
      (terminal)        │
                        ▼
                   ┌──────────┐
                   │CANCELLED │
                   └──────────┘
                    (terminal)
```

### State Transition Rules

| From | To | Trigger |
|------|----|---------|
| CREATED | PENDING_PAYMENT | `POST /orders/{id}/payment/start` |
| CREATED | CANCELLED | Cancel before payment |
| PENDING_PAYMENT | PAID | Webhook: CONFIRMED |
| PENDING_PAYMENT | PAYMENT_FAILED | Webhook: FAILED |
| PAYMENT_FAILED | PENDING_PAYMENT | Retry payment |
| PAYMENT_FAILED | CANCELLED | Cancel after failure |

**Invariants enforced:**
- You cannot skip states (e.g., CREATED → PAID is blocked)
- Terminal states (PAID, CANCELLED) cannot transition anywhere
- The state machine is defined in the `OrderStatus` enum with `validateTransitionTo()`

---

## Payment Safety & Idempotency

### Double Payment Prevention

1. **Guard: `order.canStartPayment()`** — Only CREATED and PAYMENT_FAILED orders can start payment
2. **Guard: `order.hasActivePendingPayment()`** — Blocks if there's already a PENDING payment attempt
3. **Pessimistic locking: `findByIdWithLock()`** — Prevents race conditions during webhook processing

### Idempotent Webhook Processing

| Scenario | Behavior |
|----------|----------|
| First webhook | Processes normally, updates payment + order state |
| Duplicate webhook (same idempotency key) | Detects terminal state, returns OK, no state change |
| Concurrent webhooks | Pessimistic write lock serializes access |

**Strategy:**
- Each PaymentAttempt has a unique `idempotencyKey` (format: `{orderId}_attempt_{N}`)
- Webhook looks up by idempotency key
- If the attempt is already in a terminal state (CONFIRMED/FAILED), returns success without side effects
- Optimistic locking (`@Version`) on Order prevents lost updates

### Concurrency Protection

| Mechanism | Purpose |
|-----------|---------|
| `@Version` on Order | Optimistic locking — detects concurrent modifications |
| `PESSIMISTIC_WRITE` lock | Serializes webhook processing for same order |
| Unique index on `idempotencyKey` | DB-level guarantee against duplicate attempts |
| `@Transactional` | Atomic state changes — all or nothing |

---

## API Endpoints

### Cart

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| POST | `/carts` | Create a new cart | 201 |
| GET | `/carts/{cartId}` | Get cart details | 200 |
| POST | `/carts/{cartId}/items` | Add item to cart | 200 |

### Checkout / Order

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| POST | `/carts/{cartId}/checkout` | Checkout cart → create order | 201 |
| GET | `/orders/{orderId}` | Get order details | 200 |

### Payment

| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| POST | `/orders/{orderId}/payment/start` | Start payment | 202 |
| POST | `/payments/webhook` | Receive payment webhook | 200 |

### Error Responses

All errors return structured JSON:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot start payment for order in state: PAID",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

---

## Testing

### Run Tests

```bash
mvn test
```

### Test Coverage

| Test | Type | What It Covers |
|------|------|----------------|
| `OrderStatusTest` | Unit | All valid/invalid state transitions, terminal state detection |
| `FullFlowIntegrationTest` | Integration | Happy path, duplicate webhook, payment failure + retry |

### Test Strategy

- **Unit tests** validate domain invariants (state machine) in isolation — no Spring context needed
- **Integration tests** use `@SpringBootTest` + `MockMvc` to test full HTTP flow
- `MockPaymentProvider` is `@MockBean`'d in integration tests to prevent async webhook interference — webhooks are sent manually to test each scenario deterministically

---

## Postman Collection

Import `postman/Cart-Checkout-Service.postman_collection.json` into Postman.

**Run order matters** — execute flows sequentially (Flow 1, then Flow 2, then Flow 3, then Edge Cases).

### Included Flows:

1. **Happy Path** — Cart → Items → Checkout → Pay → PAID
2. **Payment Failure + Retry** — Pay → FAILED → Retry → PAID
3. **Duplicate Webhook** — Same webhook sent twice → system stays correct
4. **Edge Cases** — Add to locked cart, double checkout, pay paid order

> **Important**: When running manually, send the webhook **before** the mock provider auto-fires (within 2 seconds). Or set `mock.payment.delay-ms=60000` in `application.properties` to give yourself time.

---

## Key Architectural Decisions

### 1. State Machine in Enum (not a framework)

**Decision**: Implemented state transitions as an `EnumSet` map inside `OrderStatus` rather than using Spring State Machine.

**Why**: For 5 states, a framework adds unnecessary complexity. The enum approach is explicit, testable, and easy to understand during code review.

### 2. Rich Domain Model over Anemic

**Decision**: Business rules live in entities (`Cart.addItem()`, `Order.transitionTo()`), not in services.

**Why**: Prevents invalid state from ever existing. The domain objects protect their own invariants.

### 3. Pessimistic Locking for Webhooks

**Decision**: `PESSIMISTIC_WRITE` lock when processing webhooks, combined with optimistic locking (`@Version`) on the Order.

**Why**: Webhooks can arrive concurrently (duplicates, retries). Pessimistic lock serializes access. Optimistic lock catches any remaining edge cases.

### 4. Idempotency Key Strategy

**Decision**: `{orderId}_attempt_{N}` format, stored in a unique-indexed column.

**Why**: Simple, deterministic, collision-free. The DB unique constraint is the final safety net.

### 5. Mock Provider as @Async Internal Component

**Decision**: The mock provider is an `@Async` Spring bean that calls back via HTTP.

**Why**: Simulates real-world async webhook behavior without external dependencies. Easy to disable in tests via `@MockBean`.

---

## Trade-offs

| Decision | Benefit | Cost |
|----------|---------|------|
| H2 in-memory DB | Zero setup, fast tests | Data lost on restart (acceptable for assessment) |
| Single service | Simple deployment | Would need splitting at scale |
| Pessimistic lock | Guarantees correctness | Slight throughput reduction under high concurrency |
| No event sourcing | Simpler mental model | Less audit trail (PaymentAttempt records mitigate this) |
| Synchronous checkout | Simpler flow | Could block under heavy load |

---

## Future Extensibility

The architecture is designed for these extensions **without breaking core invariants**:

| Feature | How to Add |
|---------|-----------|
| **Refunds** | Add `REFUND_PENDING`, `REFUNDED` to OrderStatus with valid transitions from PAID |
| **Partial payments** | Track `amountPaid` on Order; allow multiple confirmed PaymentAttempts |
| **Cancellation** | CANCELLED state already exists; add a `POST /orders/{id}/cancel` endpoint |
| **Real payment provider** | Replace `MockPaymentProvider` with a real adapter; webhook endpoint stays the same |
| **PostgreSQL** | Change `application.properties` datasource; JPA handles the rest |
| **Event publishing** | Add Spring Events on state transitions for notifications, analytics |

---

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 17 | Language |
| Spring Boot 3.2.5 | Framework |
| Spring Data JPA | Data access |
| H2 Database | In-memory persistence |
| Lombok | Boilerplate reduction |
| JUnit 5 | Unit & integration testing |
| MockMvc | HTTP-level integration tests |
| Docker | Containerization |
| GitHub Actions | CI/CD pipeline |

---

## H2 Console

Available at `http://localhost:8080/h2-console` during development.

- **JDBC URL**: `jdbc:h2:mem:ecommercedb`
- **Username**: `sa`
- **Password**: *(empty)*
