USE ecommerce_db;

-- Optional non-production catalog expansion for laptop load tests.
-- Keeps ids in a separate range so the baseline seed remains easy to identify.

INSERT INTO product (id, name, description, category, price)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    1000 + n,
    CONCAT('Load Test Product ', LPAD(n, 3, '0')),
    'Generated product for backend load testing',
    ELT(1 + MOD(n, 8), 'Electronics', 'Accessories', 'Footwear', 'Furniture', 'Bags', 'Books', 'Stationery', 'Home'),
    ROUND(10 + (n * 3.17), 2)
FROM seq
WHERE NOT EXISTS (
    SELECT 1 FROM product p WHERE p.id = 1000 + seq.n
);

INSERT INTO product_images (product_id, images)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 200
)
SELECT
    1000 + n,
    CONCAT('http://localhost:8080/images/load-test-product-', LPAD(n, 3, '0'), '.jpg')
FROM seq
WHERE EXISTS (
    SELECT 1 FROM product p WHERE p.id = 1000 + seq.n
)
AND NOT EXISTS (
    SELECT 1
    FROM product_images pi
    WHERE pi.product_id = 1000 + seq.n
      AND pi.images = CONCAT('http://localhost:8080/images/load-test-product-', LPAD(seq.n, 3, '0'), '.jpg')
);
