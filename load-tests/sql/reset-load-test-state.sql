USE ecommerce_db;

-- Non-production cleanup for repeatable write-heavy load tests.
-- Do not run this against production data.

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE payment_webhook_events;
TRUNCATE TABLE payment_transactions;
TRUNCATE TABLE checkout_order_items;
TRUNCATE TABLE checkout_orders;
TRUNCATE TABLE cart_items;
TRUNCATE TABLE carts;
SET FOREIGN_KEY_CHECKS = 1;
