You are a coding assistant for the e-commerce system in this repository. Prioritize correctness, module boundaries, readability, and contract-safe changes.

Project layout
- Backend: `backend-services` (Spring Boot, Java 25, module-first packages under `com.ecommerce.platform.modules.*`).
- Frontend: `frontend` (React + TypeScript + MUI).
- Reverse proxy/static serving may be handled by Nginx in deployment under `nginx`.

Backend module structure
- `modules.auth`: signup/login, JWT creation/validation, auth DTOs.
- `modules.users`: user management, roles, discounts, admin/manager actions.
- `modules.catalog`: products, categories, discounts.
- `modules.cart`: cart and cart-item operations.
- `modules.checkout`: checkout orders, payment transactions, webhook finalization.
  - `payment.core`: provider-neutral payment contracts and record-based DTOs.
  - `payment.stripe`: Stripe checkout session creation and webhook verification.
  - `payment.razorpay`: Razorpay order creation and webhook verification.
- `modules.reviews`: product reviews.
- `modules.media`: image upload and static URL generation.
- Cross-cutting:
  - `config`: security, CORS, static resources.
  - `common.error`: `ApiError` + global exception handling.
  - `common.dto`: shared response wrappers like `PageResponse`.

Current backend API contracts (source of truth)
- Auth:
  - `POST /auth/signup`
  - `POST /auth/login`
- Catalog:
  - `GET /api/v1/products` -> paginated `PageResponse<ProductListItemResponse>`
  - `GET /api/v1/products/{id}` -> `ProductResponse`
  - `GET /api/v1/products/categories` -> `string[]`
  - `POST|PUT|DELETE /api/v1/products...` (admin/manager restricted)
- Reviews:
  - `GET /api/v1/products/{productId}/reviews` -> paginated `PageResponse<ReviewResponse>`
  - `POST /api/v1/products/{productId}/reviews`
- Cart:
  - `GET /api/v1/cart`
  - `POST /api/v1/cart/items`
  - `PATCH /api/v1/cart/items/{productId}`
  - `PATCH /api/v1/cart/items/{productId}/discount`
  - `DELETE /api/v1/cart/items/{productId}`
  - `DELETE /api/v1/cart`
- Checkout/Payments:
  - `POST /api/v1/checkout` -> `CheckoutResponse(orderId, status, checkoutUrl, expiresAt)`
  - `GET /api/v1/checkout/{orderId}` -> `CheckoutStatusResponse(orderId, status, paymentStatus, message)`
  - `POST /api/v1/payments/webhook/{gateway}` (public webhook endpoint, currently Stripe and Razorpay)
- Users:
  - `GET /api/v1/users` (paginated)
  - `PATCH /api/v1/users/{id}/flag`
  - `PATCH /api/v1/users/{id}/unflag`
  - `PATCH /api/v1/users/{id}/discount`
  - `PATCH /api/v1/users/{id}/employee`
  - `DELETE /api/v1/users/{id}`
  - `POST /api/v1/users/managers`
- Media:
  - `POST /api/v1/images/upload`

Pagination and validation rules
- Growth-prone endpoints are paginated.
- Query validation contract: `page >= 0`, `1 <= size <= 100`.
- Responses should use stable DTO envelopes (`PageResponse`, specific response DTOs), not entities.

Database schema baseline
- Canonical baseline file: `DatabaseInit.sql`.
- Non-destructive upgrade file: `DatabaseUpgrade.sql` (use this for schema/data evolution without resets).
- Migration tooling is intentionally disabled for now (no Flyway/Liquibase runtime integration).
- DB changes must be reflected in:
  - `DatabaseInit.sql`
  - `DatabaseUpgrade.sql`
  - `backend-services/docs/db-change-log.md`

Key tables and relations
- Identity/access: `users`, `roles`, `user_roles`.
- Catalog: `product`, `product_images`, `discount`, `review`.
- Cart: `carts`, `cart_items`.
- Checkout/payments:
  - `checkout_orders`
  - `checkout_order_items`
  - `payment_transactions`
  - `payment_webhook_events`

Notable data-model details
- Monetary values use `DECIMAL` in DB and `BigDecimal` in Java where applicable.
- Reviews include `created_at` for deterministic ordering.
- Cart and cart-item use optimistic locking (`@Version`).
- Payment entities are mutable JPA entities and should remain regular classes, not records.

Checkout/payment behavior (current)
- Checkout does NOT clear cart immediately.
- `POST /api/v1/checkout`:
  - snapshots cart into order + order items,
  - creates a provider-specific checkout/order through the configured payment gateway,
  - persists a pending payment transaction.
- Cart is cleared only after a successful verified webhook event.
- Webhook processing is signature-verified and idempotent.
- Stripe local testing requires explicit webhook forwarding; success-page redirect alone does not finalize payment state.

Security/runtime guarantees
- JWT secret is mandatory and must be strong (>= 32 chars) via `app.jwt.secret` / `APP_JWT_SECRET`.
- Disabled/locked users are blocked at login and during JWT-authenticated request processing.
- Global exception handling returns `ApiError` envelope and keeps exception details in server logs.
- Media upload is hardened: allowed MIME/signature checks, size limit, normalized path handling.
- Public webhook endpoints are explicitly whitelisted in security config.

Configuration expectations
- `application.properties` default runtime uses `spring.jpa.hibernate.ddl-auto=validate`.
- `application-dev.properties` uses `update` for local-only convenience.
- CORS allowed origins are driven by `APP_CORS_ALLOWED_ORIGINS`.
- Stripe env vars used by backend:
  - `APP_STRIPE_SECRET_KEY`
  - `APP_STRIPE_WEBHOOK_SECRET`
  - `APP_STRIPE_PUBLISHABLE_KEY`
  - `APP_STRIPE_SUCCESS_URL`
  - `APP_STRIPE_CANCEL_URL`
  - optional timeout/retry env vars for Stripe gateway
- Razorpay env vars used by backend:
  - `APP_RAZORPAY_KEY_ID`
  - `APP_RAZORPAY_KEY_SECRET`
  - `APP_RAZORPAY_WEBHOOK_SECRET`
  - `APP_RAZORPAY_CHECKOUT_BASE_URL`

Frontend context (important)
- Frontend API layer: `frontend/src/api/ApiService.tsx`.
- Shared frontend types: `frontend/src/types/models.ts`.
- Global state: `AuthContext`, `CartContext`.
- Main flows/pages:
  - browse/list/detail (`MainPage`, `ProductDetails`)
  - cart/checkout (`Buy`)
  - auth (`Login`, `SignUp`)
  - admin/manager pages
- The deployed frontend is expected to run behind `https://localhost` through Nginx; Vite dev runs at `http://localhost:5173`.

Known frontend-backend alignment gaps (current)
- Users endpoint caps `size` at 100; frontend currently requests 200 in one call.
- Frontend does not define dedicated `/checkout/success` and `/checkout/cancel` routes yet.
- Stripe cart-clearing in local dev depends on webhook forwarding being active; redirecting back to the SPA is not sufficient.

Engineering expectations for future changes
- Preserve module boundaries; avoid cross-module repository leakage.
- Keep controllers thin; business logic and authorization belong in services.
- Keep DTO-based contracts explicit and validated (`@Valid`).
- Prefer records for immutable DTO/config/value carriers where they improve clarity.
- Do not force records onto JPA entities or other framework-managed mutable types.
- If schema changes, update Java entities, SQL baseline, and DB changelog together.
- Prefer backward-compatible changes unless explicitly approved as breaking.
- When breaking contracts are intentional, update backend + frontend together in the same release.

When making changes
- Keep behavior stable unless the task explicitly changes behavior.
- Preserve `ApiError` response shape.
- Maintain strong logging for failures, especially payment and webhook failures.
- Provide precise file references and concise diffs.
- Never delete existing catalog/product data as part of routine fixes.
- Never run destructive DB operations (`DROP`, truncate/reset seed replacement, hard data wipes) without explicit user confirmation.
- If destructive DB action is absolutely required, first show a clear warning about data loss and ask for explicit user confirmation before proceeding.
