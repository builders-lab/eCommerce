# Runtime Cache and Portability

## Redis Cache Strategy

The backend uses Redis through Spring Boot Cache Abstraction. Redis is a cache-aside performance layer only; MySQL remains the source of truth.

Read flow:

1. Check Redis.
2. On cache miss, read from MySQL.
3. Store the response DTO in Redis.
4. Return the response DTO.

Write flow:

1. Write to MySQL inside the service transaction.
2. After a successful mutation, evict related Redis caches.
3. Do not rely on TTL for correctness.

Redis failures are not treated as application-fatal. The custom cache error handler logs cache get/put/evict/clear failures and lets the request continue against MySQL-backed service logic.

## Cache Names

- `products`
- `productLists`
- `categories`
- `productImages`
- `productReviews`
- `productDiscounts`
- `userCart`
- `userRoles`

## TTL Policy

One global default TTL is configured with:

```properties
app.cache.default-ttl-minutes=10
```

Specific cache names can override the default in `RedisCacheConfig`. The current short-lived overrides are `userCart` and `userRoles`.

TTL is a fallback cleanup mechanism. Correctness depends on explicit eviction after MySQL writes.

## Cached Data Shape

Cache DTOs and API response records, not JPA entities.

Current cached reads include:

- product detail responses
- product list `PageResponse`
- categories
- product image maps
- product review `PageResponse`
- user cart responses

Avoid caching passwords, JWTs, payment secrets, or mutable JPA entities.

## Stale Cache Entries

Redis may contain entries written by an older serializer or an older DTO shape. A common local symptom is:

```text
Redis cache get failed ... Could not read JSON ... missing type id property '@class'
```

This means Redis is reachable and returned data, but the backend cannot deserialize that cached value. It is not a Redis availability failure.

For local development, clear stale entries:

```bash
redis-cli flushall
```

For shared Redis instances, delete only this app's affected cache keys instead of flushing the whole instance.

## Data That Must Stay Durable in MySQL

Do not use Redis as the source of truth for:

- `checkout_orders`
- `checkout_order_items`
- `payment_transactions`
- `payment_webhook_events`

Checkout and payment state must remain transaction-safe and recoverable from MySQL.

## Cross-Platform Status

The application is mostly cross-platform:

- Backend: cross-platform on Java 25 with Maven.
- Frontend: cross-platform on Node.js/npm.
- Database: cross-platform with MySQL 8.
- Cache: cross-platform with Redis.
- Media serving: cross-platform when `APP_MEDIA_RESOURCE_LOCATION` points to a valid path for the host OS.

Known OS-specific items:

- `backend-services/stripe-listen.bat` is Windows-only.
- Linux/WSL should run Stripe CLI commands directly instead of using the batch file.
- `nginx/deploy_wsl.sh` and other shell scripts are Linux/WSL-oriented.
- Windows drive paths such as `C:\Dev\e_commerce` do not work inside WSL bash. Use `/mnt/c/Dev/e_commerce`.

## WSL Guidance

When running the whole stack in WSL, keep MySQL, Redis, backend, and frontend in the same WSL environment when possible.

Minimum checks:

```bash
redis-cli ping
mysqladmin -h 127.0.0.1 -P 3306 -u root -p ping
java -version
mvn -version
node -v
npm -v
```

If Redis runs in WSL but the backend runs in Windows, `localhost` may refer to different loopback interfaces. In that split setup, either run Redis and backend in the same environment or set `SPRING_DATA_REDIS_HOST` to an address the backend process can reach.

If the backend reports `Unknown database 'ecommerce_db'`, create and initialize the WSL MySQL database:

```bash
cd /mnt/c/Dev/e_commerce
mysql -h 127.0.0.1 -P 3306 -u root -p -e "CREATE DATABASE IF NOT EXISTS ecommerce_db;"
mysql -h 127.0.0.1 -P 3306 -u root -p ecommerce_db < DatabaseInit.sql
```

If static images return `NoResourceFoundException`, verify the backend was started from `backend-services` or set:

```bash
export APP_MEDIA_RESOURCE_LOCATION=file:/mnt/c/Dev/e_commerce/imageResource/
export APP_MEDIA_UPLOAD_DIR=/mnt/c/Dev/e_commerce/imageResource/
```

## Current Notes: Phase 2 Redis Safety

- `RedisCacheManager` is transaction-aware so cache writes and evictions are deferred until the surrounding DB transaction commits.
- This prevents Redis from being updated or evicted for a database-backed change that later rolls back.
- The custom `CacheErrorHandler` intentionally keeps the application available when Redis fails by logging warnings and falling back to MySQL-backed execution.
- Do not remove the error handler without a separate availability decision.
- Add monitoring or alerts for Redis cache errors, Redis availability, cache hit/miss behavior where available, and DB load when Redis is unavailable.
- During load tests, check Redis stats and DB pressure together so Redis outages do not stay hidden as warning-only log noise.
