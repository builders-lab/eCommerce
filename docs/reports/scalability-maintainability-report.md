# Scalability and Maintainability Report

Date: 2026-03-15  
Project: `backend-services` (Spring Boot)

## Scope
- Runtime scalability fixes from the execution plan
- Checkout/payment architecture changes for Stripe hosted checkout
- Database evolution safety with migration tooling intentionally disabled

## Open Findings

### 1. Versioned Migration Tooling Is Still Intentionally Disabled (Medium)
**Locations**:
- `backend-services/src/main/resources/application.properties`
- `backend-services/src/main/resources/application-dev.properties`
- `DatabaseInit.sql`
- `docs/operations/db-change-log.md`

**Current State**:
- Flyway/Liquibase runtime integration is intentionally removed for now.
- Schema governance is currently baseline SQL (`DatabaseInit.sql`) plus manual changelog discipline.

**Risk**:
- Safe for the current phase, but still weaker than fully versioned migrations for multi-environment rollout and rollback automation.

**Next Step**:
- Keep this temporary model in place.
- In a later phase, baseline and adopt versioned migrations for all non-local environments.

## Resolved in This Execution
- Product list N+1 risk removed from list mapping path:
  - list endpoint now uses list DTO (`ProductListItemResponse`) and does not traverse discounts.
  - list/detail read models are separated.
- Growth-prone endpoints are paginated:
  - products/users already paginated and now capped.
  - reviews migrated to paginated `PageResponse<ReviewResponse>` with deterministic sort (`createdAt DESC, id DESC`).
- Max page-size validation enforced on paginated endpoints:
  - `page >= 0`, `1 <= size <= 100` via controller validation annotations.
- Hot-path indexes added/verified in baseline SQL:
  - `review(product_id, created_at, id)`
  - `discount(product_id, start_date, end_date)`
  - `user_roles(role_id, user_id)`
  - existing `product(category)` and `cart_items(cart_id, product_id)` retained.
- DTO-based read contracts maintained across updated endpoints (no entity/map leakage introduced).
- Transaction/concurrency hardening:
  - optimistic-lock exceptions mapped to `409 CONFLICT` with standard `ApiError` envelope.
  - exception messages are preserved in server logs.
- Provider-backed checkout introduced with full order/payment ledger:
  - `checkout_orders`, `checkout_order_items`, `payment_transactions`, `payment_webhook_events`.
  - `POST /api/v1/checkout` now creates a pending order plus provider checkout session and returns checkout session DTO.
  - `GET /api/v1/checkout/{orderId}` added for status polling.
  - `POST /api/v1/payments/webhook/{gateway}` added with signature verification and idempotent event handling.
  - cart is cleared only after successful payment webhook event.
- DB evolution safety discipline started:
  - `docs/operations/db-change-log.md` added with forward and rollback notes.

## Expected Outcome
Runtime scalability bottlenecks targeted in Phase 1 are addressed, checkout is now payment-provider backed with auditable order/payment state, and DB change discipline is in place while migration tooling remains intentionally deferred.
