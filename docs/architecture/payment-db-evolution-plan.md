# Runtime Scalability + DB Evolution + Payment Integration Plan

Date: 2026-03-15  
Project: `backend-services`

## Execution Status (2026-03-15)
- Completed:
  - Product list/detail DTO split and catalog list-path N+1 removal.
  - Reviews pagination with deterministic sort and validated `page`/`size`.
  - Max page-size caps (`1..100`) on product/user/review paginated endpoints.
  - Hot-path index updates in `DatabaseInit.sql`.
  - Hosted checkout implementation with order/payment ledger.
  - Payment webhook endpoint with signature verification and event idempotency.
  - Checkout status polling endpoint.
  - Optimistic-lock conflict mapping to `409 CONFLICT`.
  - DB change tracking doc added at `docs/operations/db-change-log.md`.
- Remaining for local verification:
  - Full integration-test execution in an environment with Docker/Testcontainers available.

## Goal
- Phase 1: fix real runtime scalability bottlenecks first.
- Phase 2: make database evolution safer without reintroducing migration tooling yet.
- Add payment-provider-backed checkout processing as the checkout backend.

## Constraints and Standards
- Keep migration tooling disabled for now (no Flyway/Liquibase runtime integration in this phase).
- Keep constructor-based dependency injection and module-oriented packaging aligned with current Spring Boot 4 guidance.
- Keep DTO-based API contracts and explicit validation (`@Valid`) aligned with current codebase style.
- Follow Java 25 style consistency (clear naming, immutable DTO/read models where possible, explicit error handling).

## Phase 1: Runtime Scalability Remediation (Highest Priority)

### 1. Remove N+1 query patterns
1. Product list mapping (`ProductService.getProducts`) currently touches `product.getDiscounts()` per row.
2. Replace entity-to-DTO lazy traversal with one of these concrete patterns:
   - Preferred: dedicated repository read query with projection for product list payload (`ProductListItemDto`) and discount summary prefetch.
   - Alternative: `@EntityGraph`/`join fetch` list query with pagination-safe strategy.
3. Split list and detail read models:
   - `ProductListItemDto`: fields needed for catalog grid/list only.
   - `ProductDetailDto` (existing detail response can be retained/extended).
4. Ensure list endpoint mapping does not trigger per-row child fetches.

### 2. Paginate every growth-prone endpoint
1. Keep existing pagination for products/users.
2. Add pagination for reviews:
   - Replace `List<ReviewResponse>` with `PageResponse<ReviewResponse>` on `GET /api/v1/products/{productId}/reviews`.
   - Deterministic sort: `createdAt DESC, id DESC` (or `id DESC` until timestamp exists).
3. Preserve cart endpoint semantics as user-scoped (not global growth-prone), but ensure internal mapping stays bounded.

### 3. Add max page-size validation
1. Enforce caps on all paginated endpoints at controller boundary:
   - `page >= 0`
   - `1 <= size <= 100` (default `20`)
2. Standardize validation via request DTO or validated query params (`@Min`, `@Max`) and consistent API error response.
3. Add integration tests for out-of-range pagination inputs.

### 4. Add proper indexes for hot queries
1. Keep existing index: `product(category)`.
2. Add/verify:
   - `review(product_id, id)` for paged reviews per product.
   - `discount(product_id, start_date, end_date)` for active discount selection.
   - `user_roles(role_id, user_id)` to speed role-based joins/filtering.
   - `cart_items(cart_id, product_id)` already unique; retain as key access path.
3. Maintain `DatabaseInit.sql` as canonical baseline reference for index declarations in this phase.

### 5. Use DTO-based read models consistently
1. Ensure list/read endpoints return projection DTOs, not JPA entity graphs.
2. Avoid nested lazy collection traversal during controller serialization/mapping.
3. Keep mapper methods in services deterministic and side-effect free.

### 6. Review transactional write flows
1. Validate all mutating service methods remain `@Transactional`.
2. Keep optimistic locking on cart/cart-item and add explicit conflict handling:
   - return `409 CONFLICT` (or mapped domain error) on optimistic lock failures.
3. Ensure checkout/order write flow is atomic around persisted payment/order state transitions (see Stripe phase).

### Phase 1 Acceptance Criteria
1. Product list, user list, and review list endpoints all paginated and size-capped.
2. Measurable query count reduction for product listing (no `1 + N` discount fetch pattern).
3. Added indexes reflected in `DatabaseInit.sql`.
4. Integration tests pass for pagination boundaries, review pagination, and optimistic-lock conflict paths.

## Phase 2: Safer Database Evolution (Without Enabling Migration Tool Yet)

### 1. Keep `DatabaseInit.sql` as baseline reference
1. Treat `DatabaseInit.sql` as snapshot baseline for full environment bootstrapping.
2. Require every schema/index change to be updated in this file during this phase.

### 2. Introduce migration-ready discipline now
1. Add `docs/operations/db-change-log.md` (or equivalent) to track schema deltas chronologically.
2. For each DB change PR:
   - record rationale,
   - record forward SQL delta,
   - record rollback note.
3. Keep `spring.jpa.hibernate.ddl-auto=validate` outside local/dev profile.

### 3. Future migration-tool onboarding plan (later phase)
1. When approved, initialize versioned migrations from current baseline state.
2. Convert all future schema changes from changelog entries into versioned migration files.
3. Lock non-local environments to migration-driven schema evolution only.

### Phase 2 Acceptance Criteria
1. DB changes are traceable with explicit forward/rollback notes.
2. Baseline SQL and runtime config remain consistent.
3. No automatic runtime schema mutation in non-dev environments.

## Payment API Processing Plan

### 1. Payment domain model additions
1. Introduce order/payment persistence (new module or `checkout` submodule):
   - `Order` (id, userId, status, totalAmount, currency, createdAt).
   - `OrderItem` snapshot (productId, title, unitPrice, quantity, appliedDiscount metadata).
   - `PaymentTransaction` (provider-aware, providerSessionId, paymentIntentId, status, idempotencyKey, timestamps).
2. Do not rely on mutable cart for post-checkout truth; persist immutable order snapshot before redirecting to Stripe.

### 2. Provider-backed checkout flow
1. Replace current `POST /api/v1/checkout` behavior:
   - Build order snapshot from current cart.
   - Create a provider checkout/payment session server-side.
   - Persist `PENDING_PAYMENT` order + transaction.
   - Return `checkoutUrl` (or sessionId) to frontend.
2. Do not clear cart on session creation.
3. Clear cart only after verified successful payment event.

### 3. Webhook-driven finalization
1. Add `POST /api/v1/payments/webhook/{gateway}` endpoint.
2. Verify the active provider signature with its configured webhook secret.
3. Handle events idempotently:
   - `checkout.session.completed` / `payment_intent.succeeded` -> mark order `PAID`, clear cart if not already cleared.
   - failure/expired events -> mark `FAILED` or `EXPIRED`.
4. Persist event processing log or dedupe key to avoid duplicate side effects.

### 4. API contract updates
1. `POST /api/v1/checkout` response becomes payment-session response DTO:
   - `orderId`, `status`, `checkoutUrl`, optional `expiresAt`.
2. Add `GET /api/v1/checkout/{orderId}` for payment/order status polling.
3. Keep response DTOs explicit; no map/entity response leakage.

### 5. Security and operational controls
1. Environment variables:
   - `APP_STRIPE_SECRET_KEY`
   - `APP_STRIPE_WEBHOOK_SECRET`
   - `APP_STRIPE_PUBLISHABLE_KEY` (frontend use)
2. Never expose secret key to frontend.
3. Apply request timeout/retry policy for Stripe SDK client.
4. Use idempotency keys on checkout-session creation.
5. Log failures with correlation IDs; keep exception messages in server logs.

### 6. Payment test plan
1. Unit tests for session creation request mapping and webhook signature validation.
2. Integration tests for:
   - successful payment -> order paid + cart cleared,
   - failed/expired payment -> order not paid + cart retained,
   - duplicate webhook delivery -> no duplicate state transition.
3. End-to-end test using provider test credentials and webhook events.

## Delivery Order
1. Phase 1 scalability fixes (N+1, pagination, caps, indexes, read-model DTOs, write-flow audit).
2. Payment integration (order snapshot + checkout session + webhook finalization).
3. Phase 2 DB evolution discipline updates and documentation hardening.

## Risks and Mitigations
- Risk: breaking frontend contracts when paginating reviews or changing checkout response.
  - Mitigation: version DTOs or coordinate frontend update in same release.
- Risk: webhook retries causing duplicate processing.
  - Mitigation: idempotent transaction state machine + dedupe storage.
- Risk: index additions requiring controlled rollout in existing DBs.
  - Mitigation: run index DDL during planned maintenance window and verify query plans.
