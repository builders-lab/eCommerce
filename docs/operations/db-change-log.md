# DB Change Log

This file tracks schema/index changes while migration tooling is intentionally disabled.

## 2026-03-15

### Forward Changes
- Added user-level discount fields:
  - `users.user_discount_percentage`
  - `users.user_discount_start_date`
  - `users.user_discount_end_date`
- Added `ROLE_EMPLOYEE` seed role for employee discounts.
- Added cart concurrency and selected-discount fields:
  - `carts.version`
  - `cart_items.version`
  - `cart_items.discount_id`
- Added/verified cart discount referential integrity:
  - `fk_cart_items_discount` from `cart_items.discount_id` to `discount.id` with `ON DELETE SET NULL`.
- Added checkout/payment persistence baseline tables:
  - `checkout_orders`
  - `checkout_order_items`
  - `payment_transactions`
  - `payment_webhook_events`
- Added review creation timestamp:
  - `review.created_at` with default current timestamp.
- Added/verified hot-path indexes:
  - `idx_user_roles_role_user` on `user_roles(role_id, user_id)`
  - `idx_product_category` on `product(category)`
  - `idx_review_product_created_id` on `review(product_id, created_at, id)`
  - `idx_discount_product_dates` on `discount(product_id, start_date, end_date)`
  - `idx_checkout_orders_user_created` on `checkout_orders(user_id, created_at)`
  - `idx_checkout_order_items_order` on `checkout_order_items(order_id)`
  - `idx_payment_transactions_session` on `payment_transactions(provider_session_id)`
  - `idx_payment_transactions_intent` on `payment_transactions(payment_intent_id)`

### Rationale
- Support Stripe checkout and webhook-driven payment finalization with auditable order/payment state.
- Support user, employee, and selected product discounts in cart/checkout pricing.
- Improve query performance for growth-prone reads and role joins.
- Enable deterministic review pagination by creation time.

### Rollback Notes
- Drop newly introduced tables and indexes if rollback is required:
  - `payment_webhook_events`, `payment_transactions`, `checkout_order_items`, `checkout_orders`.
- Remove `review.created_at` and related index only if reverting review pagination sort strategy.
- Remove user/cart discount columns and `ROLE_EMPLOYEE` only if reverting discount functionality.
- Revert index additions if they are found to regress specific query plans in production.
