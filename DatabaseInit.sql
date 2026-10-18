USE ecommerce_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS payment_webhook_events;
DROP TABLE IF EXISTS payment_transactions;
DROP TABLE IF EXISTS checkout_order_items;
DROP TABLE IF EXISTS checkout_orders;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;
DROP TABLE IF EXISTS discount;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS product_images;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(100),
    is_flagged BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE,
    user_discount_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    user_discount_start_date DATE,
    user_discount_end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(12,2) NOT NULL
);

CREATE INDEX idx_product_category ON product(category);

CREATE TABLE product_images (
    product_id BIGINT NOT NULL,
    images VARCHAR(255),
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

CREATE TABLE review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user` VARCHAR(50),
    comment TEXT,
    rating INT NOT NULL,
    product_id BIGINT,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE discount (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(255),
    percentage DECIMAL(5,2) NOT NULL,
    start_date DATE,
    end_date DATE,
    product_id BIGINT NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quantity INT NOT NULL,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    discount_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    FOREIGN KEY (discount_id) REFERENCES discount(id) ON DELETE SET NULL,
    UNIQUE KEY unique_cart_product (cart_id, product_id)
);

-- Additive schema extensions required by current backend runtime
ALTER TABLE review
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER rating;

CREATE INDEX idx_review_product_created_id ON review(product_id, created_at, id);
CREATE INDEX idx_user_roles_role_user ON user_roles(role_id, user_id);
CREATE INDEX idx_discount_product_dates ON discount(product_id, start_date, end_date);

CREATE TABLE checkout_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_checkout_orders_user_created ON checkout_orders(user_id, created_at);

CREATE TABLE checkout_order_items (
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
CREATE INDEX idx_checkout_order_items_order ON checkout_order_items(order_id);

CREATE TABLE payment_transactions (
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
CREATE INDEX idx_payment_transactions_session ON payment_transactions(provider_session_id);
CREATE INDEX idx_payment_transactions_intent ON payment_transactions(payment_intent_id);

CREATE TABLE payment_webhook_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO roles (name) VALUES
('ROLE_USER'),
('ROLE_ADMIN'),
('ROLE_MANAGER'),
('ROLE_EMPLOYEE');

INSERT INTO users (email, password, username, is_flagged, enabled)
VALUES
('admin@ecommerce.com', '$2a$10$ehYWzot3e82Pdz0CHG/2JObImXBJypWBlto0dykq00dubbC97veWK', 'Super Admin', false, true),
('user@ecommerce.com', '$2a$10$ehYWzot3e82Pdz0CHG/2JObImXBJypWBlto0dykq00dubbC97veWK', 'John Doe', false, true),
('manager@ecommerce.com', '$2a$10$ehYWzot3e82Pdz0CHG/2JObImXBJypWBlto0dykq00dubbC97veWK', 'Manager Account', false, true);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ROLE_ADMIN' WHERE u.email = 'admin@ecommerce.com';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ROLE_USER' WHERE u.email = 'user@ecommerce.com';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ROLE_MANAGER' WHERE u.email = 'manager@ecommerce.com';

INSERT INTO product (name, description, category, price) VALUES
('Gaming Laptop', 'High-performance gaming laptop', 'Electronics', 1299.99),
('Wireless Earbuds', 'Noise-cancelling Bluetooth earbuds', 'Electronics', 99.99),
('Leather Wallet', 'Genuine leather wallet for men', 'Accessories', 25.50),
('Smartphone', 'Latest Android phone with fast processor', 'Electronics', 699.00),
('Wrist Watch', 'Analog wristwatch with leather strap', 'Accessories', 149.99),
('Running Shoes', 'Comfortable shoes for running', 'Footwear', 89.00),
('Office Chair', 'Ergonomic chair with lumbar support', 'Furniture', 199.00),
('Backpack', 'Waterproof backpack for travel', 'Bags', 49.99),
('Sunglasses', 'Polarized UV protection sunglasses', 'Accessories', 59.99),
('Bluetooth Speaker', 'Portable speaker with deep bass', 'Electronics', 79.99);

INSERT INTO product_images (product_id, images) VALUES
(1, 'http://localhost:8080/images/laptop1.jpg'), (1, 'http://localhost:8080/images/laptop2.jpg'),
(2, 'http://localhost:8080/images/earbuds1.jpg'), (2, 'http://localhost:8080/images/earbuds2.jpg'),
(3, 'http://localhost:8080/images/wallet1.jpg'), (3, 'http://localhost:8080/images/wallet2.jpg'),
(4, 'http://localhost:8080/images/phone1.jpg'), (4, 'http://localhost:8080/images/phone2.jpg'),
(5, 'http://localhost:8080/images/watch1.jpg'), (5, 'http://localhost:8080/images/watch2.jpg'),
(6, 'http://localhost:8080/images/shoes1.jpg'), (6, 'http://localhost:8080/images/shoes2.jpg'),
(7, 'http://localhost:8080/images/chair1.jpg'), (7, 'http://localhost:8080/images/chair2.jpg'),
(8, 'http://localhost:8080/images/backpack1.jpg'), (8, 'http://localhost:8080/images/backpack2.jpg'),
(9, 'http://localhost:8080/images/sunglasses1.jpg'), (9, 'http://localhost:8080/images/sunglasses2.jpg'),
(10, 'http://localhost:8080/images/speaker1.jpg'), (10, 'http://localhost:8080/images/speaker2.jpg');

INSERT INTO review (`user`, comment, rating, product_id) VALUES
('Alice', 'Fantastic performance!', 5, 1),
('Bob', 'Runs all games smoothly.', 4, 1),
('Carol', 'Battery life could be better.', 3, 2),
('Dan', 'Clear sound and nice fit.', 5, 2),
('Eve', 'Looks premium and lasts long.', 4, 3),
('Frank', 'Spacious and compact design.', 4, 4),
('Grace', 'Elegant and stylish.', 5, 5),
('Heidi', 'Strap material is soft.', 4, 5),
('Ivan', 'Very comfortable while running.', 5, 6),
('Judy', 'Feels solid and adjustable.', 4, 7),
('Mallory', 'Great for short trips.', 5, 8),
('Niaj', 'Lens quality is impressive.', 4, 9),
('Olivia', 'Loud and clear sound.', 5, 10);

INSERT INTO discount (description, percentage, start_date, end_date, product_id) VALUES
('Summer Sale', 10.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 1),
('Clearance Deal', 15.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 2),
('HDFC Bank Offer', 5.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 90 DAY), 4),
('Festive Sale', 10.00, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 2 DAY), 4),
('Flat Discount', 20.00, CURDATE(), NULL, 6);

INSERT INTO carts (user_id)
SELECT id FROM users WHERE email = 'user@ecommerce.com';
