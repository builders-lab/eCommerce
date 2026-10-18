USE ecommerce_db;

-- Non-destructive upgrade script.
-- This file MUST NOT drop tables or delete existing product/catalog data.

SET @db := DATABASE();

-- Align legacy tables/columns with current runtime expectations
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'users' AND column_name = 'user_discount_percentage'),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN user_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'users' AND column_name = 'user_discount_start_date'),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN user_discount_start_date DATE NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'users' AND column_name = 'user_discount_end_date'),
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN user_discount_end_date DATE NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NOT NULL,
    MODIFY COLUMN password VARCHAR(255) NOT NULL,
    MODIFY COLUMN enabled BOOLEAN DEFAULT TRUE;

ALTER TABLE product
    MODIFY COLUMN name VARCHAR(100) NOT NULL,
    MODIFY COLUMN category VARCHAR(50) NOT NULL,
    MODIFY COLUMN price DECIMAL(12,2) NOT NULL;

ALTER TABLE review
    MODIFY COLUMN rating INT NOT NULL;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'carts' AND column_name = 'version'),
    'SELECT 1',
    'ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'cart_items' AND column_name = 'version'),
    'SELECT 1',
    'ALTER TABLE cart_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'cart_items' AND column_name = 'discount_id'),
    'SELECT 1',
    'ALTER TABLE cart_items ADD COLUMN discount_id BIGINT NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = @db AND table_name = 'review' AND column_name = 'created_at'),
    'SELECT 1',
    'ALTER TABLE review ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add check constraint for review rating if missing
SET @sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = @db
          AND table_name = 'review'
          AND constraint_name = 'chk_review_rating'
    ),
    'SELECT 1',
    'ALTER TABLE review ADD CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add FK cart_items.discount_id -> discount.id if missing
SET @sql := IF(
    EXISTS (
        SELECT 1
        FROM information_schema.key_column_usage
        WHERE table_schema = @db
          AND table_name = 'cart_items'
          AND column_name = 'discount_id'
          AND referenced_table_name = 'discount'
    ),
    'SELECT 1',
    'ALTER TABLE cart_items ADD CONSTRAINT fk_cart_items_discount FOREIGN KEY (discount_id) REFERENCES discount(id) ON DELETE SET NULL'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Ensure runtime indexes exist (idempotent)
SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'product' AND index_name = 'idx_product_category'),
    'SELECT 1',
    'CREATE INDEX idx_product_category ON product(category)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'review' AND index_name = 'idx_review_product_created_id'),
    'SELECT 1',
    'CREATE INDEX idx_review_product_created_id ON review(product_id, created_at, id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'user_roles' AND index_name = 'idx_user_roles_role_user'),
    'SELECT 1',
    'CREATE INDEX idx_user_roles_role_user ON user_roles(role_id, user_id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'discount' AND index_name = 'idx_discount_product_dates'),
    'SELECT 1',
    'CREATE INDEX idx_discount_product_dates ON discount(product_id, start_date, end_date)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Checkout/payment tables (create only if missing)
CREATE TABLE IF NOT EXISTS checkout_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS checkout_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    final_unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    product_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    user_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    employee_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    total_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    FOREIGN KEY (order_id) REFERENCES checkout_orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    provider VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_session_id VARCHAR(255),
    payment_intent_id VARCHAR(255),
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    failure_reason VARCHAR(512),
    expires_at TIMESTAMP NULL,
    last_webhook_event_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES checkout_orders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'checkout_orders' AND index_name = 'idx_checkout_orders_user_created'),
    'SELECT 1',
    'CREATE INDEX idx_checkout_orders_user_created ON checkout_orders(user_id, created_at)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'checkout_order_items' AND index_name = 'idx_checkout_order_items_order'),
    'SELECT 1',
    'CREATE INDEX idx_checkout_order_items_order ON checkout_order_items(order_id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_session'),
    'SELECT 1',
    'CREATE INDEX idx_payment_transactions_session ON payment_transactions(provider_session_id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = @db AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_intent'),
    'SELECT 1',
    'CREATE INDEX idx_payment_transactions_intent ON payment_transactions(payment_intent_id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Ensure ROLE_EMPLOYEE exists
INSERT INTO roles (name)
SELECT 'ROLE_EMPLOYEE'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_EMPLOYEE');

-- Non-destructive seed expansion: add missing catalog items only
INSERT INTO product (name, description, category, price)
SELECT 'Smartphone', 'Latest Android phone with fast processor', 'Electronics', 699.00
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Smartphone');

INSERT INTO product (name, description, category, price)
SELECT 'Wrist Watch', 'Analog wristwatch with leather strap', 'Accessories', 149.99
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Wrist Watch');

INSERT INTO product (name, description, category, price)
SELECT 'Running Shoes', 'Comfortable shoes for running', 'Footwear', 89.00
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Running Shoes');

INSERT INTO product (name, description, category, price)
SELECT 'Office Chair', 'Ergonomic chair with lumbar support', 'Furniture', 199.00
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Office Chair');

INSERT INTO product (name, description, category, price)
SELECT 'Backpack', 'Waterproof backpack for travel', 'Bags', 49.99
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Backpack');

INSERT INTO product (name, description, category, price)
SELECT 'Sunglasses', 'Polarized UV protection sunglasses', 'Accessories', 59.99
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Sunglasses');

INSERT INTO product (name, description, category, price)
SELECT 'Bluetooth Speaker', 'Portable speaker with deep bass', 'Electronics', 79.99
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'Bluetooth Speaker');

INSERT INTO product_images (product_id, images)
SELECT p.id, v.url
FROM product p
JOIN (
    SELECT 'Smartphone' AS product_name, 'http://localhost:8080/images/phone1.jpg' AS url
    UNION ALL SELECT 'Wrist Watch', 'http://localhost:8080/images/watch1.jpg'
    UNION ALL SELECT 'Running Shoes', 'http://localhost:8080/images/shoes1.jpg'
    UNION ALL SELECT 'Office Chair', 'http://localhost:8080/images/chair1.jpg'
    UNION ALL SELECT 'Backpack', 'http://localhost:8080/images/backpack1.jpg'
    UNION ALL SELECT 'Sunglasses', 'http://localhost:8080/images/sunglasses1.jpg'
    UNION ALL SELECT 'Bluetooth Speaker', 'http://localhost:8080/images/speaker1.jpg'
) v ON v.product_name = p.name
LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.images = v.url
WHERE pi.product_id IS NULL;
