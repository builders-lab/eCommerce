# Environment Runbook

## Toolchain Requirements

- Java 25
- Maven 3.6.3 or newer
- Node.js 20.19+ or 22.12+
- Docker for `mvn verify` and its Testcontainers-backed integration tests
- MySQL 8 and Redis for normal local runtime

## Backend Secrets File

Local backend secrets are loaded from `backend-services/secrets.properties` when the backend starts from either `backend-services/` or the repository root.

Start from `backend-services/secrets.properties.example` and fill the values you actually need.

## Minimum Local Settings

```properties
APP_JWT_SECRET=replace-with-a-random-secret-at-least-32-chars
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ecommerce_db
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-mysql-password
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
APP_CACHE_DEFAULT_TTL_MINUTES=10
```

## Common Optional Settings

```properties
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://localhost
APP_PAYMENT_GATEWAY=stripe
APP_PAYMENT_DEFAULT_CURRENCY=usd
APP_MEDIA_UPLOAD_DIR=../imageResource/
APP_MEDIA_RESOURCE_LOCATION=file:../imageResource/
APP_MEDIA_PUBLIC_BASE_URL=http://localhost:8080/images/
```

## Redis Cache Notes

- Redis is used through Spring Cache as a cache-aside performance layer.
- MySQL remains the source of truth.
- Default cache TTL is controlled by `APP_CACHE_DEFAULT_TTL_MINUTES`.
- Cache-specific TTL overrides live in `RedisCacheConfig`.
- Redis must be reachable from the same environment where the backend process runs. If the backend runs in WSL, Redis on WSL `localhost:6379` is valid. If the backend runs in Windows, Windows must be able to reach Redis.
- If local Redis contains stale data from an older build, clear it with:

```bash
redis-cli flushall
```

Use a targeted `DEL` for shared Redis instances instead of `flushall`.

If logs show `missing type id property '@class'`, Redis is active but contains stale serialized entries. Clear local Redis and retry the request.

## Stripe Settings

Use these when `APP_PAYMENT_GATEWAY=stripe`:

```properties
APP_STRIPE_SECRET_KEY=sk_test_replace_me
APP_STRIPE_PUBLISHABLE_KEY=pk_test_replace_me
APP_STRIPE_WEBHOOK_SECRET=whsec_replace_me
APP_STRIPE_SUCCESS_URL=http://localhost:5173/checkout/success
APP_STRIPE_CANCEL_URL=http://localhost:5173/checkout/cancel
```

## Razorpay Settings

Use these when `APP_PAYMENT_GATEWAY=razorpay`:

```properties
APP_RAZORPAY_KEY_ID=rzp_test_replace_me
APP_RAZORPAY_KEY_SECRET=replace_me
APP_RAZORPAY_WEBHOOK_SECRET=replace_me
APP_RAZORPAY_CHECKOUT_BASE_URL=http://localhost:5173/checkout/razorpay
```

## Database Notes

- The default runtime uses `spring.jpa.hibernate.ddl-auto=validate`.
- Schema drift causes startup failure.
- `DatabaseInit.sql` is a full reset and reseed.
- `DatabaseUpgrade.sql` is the non-destructive path for evolving an existing schema.

## WSL/Linux Local Run

Run the whole stack inside WSL/Linux when possible:

```bash
redis-cli ping
sudo service mysql start
cd /mnt/c/Dev/e_commerce
mysql -h 127.0.0.1 -P 3306 -u root -p -e "CREATE DATABASE IF NOT EXISTS ecommerce_db;"
mysql -h 127.0.0.1 -P 3306 -u root -p ecommerce_db < DatabaseInit.sql

cd /mnt/c/Dev/e_commerce/backend-services
export APP_JWT_SECRET=replace-with-a-random-secret-at-least-32-chars
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ecommerce_db
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your-mysql-password
export SPRING_DATA_REDIS_HOST=localhost
mvn spring-boot:run
```

Use Linux paths in WSL. For example, use `/mnt/c/Dev/e_commerce/backend-services`, not `C:\Dev\e_commerce\backend-services`.

## Cross-Platform Notes

- The backend is cross-platform when Java 25, Maven, MySQL, Redis, and media paths are configured for the host OS.
- The frontend is cross-platform with Node.js 20.19+ or 22.12+ and npm.
- `stripe-listen.bat` is Windows-specific. Use the Stripe CLI directly on Linux/WSL instead of the batch file.
- Nginx deployment scripts under `nginx/` are Linux/WSL-oriented shell scripts.
- `file:` media resource locations are OS-sensitive. Prefer the default relative path or set `APP_MEDIA_RESOURCE_LOCATION` per environment.

## Common Startup Failures

### Missing JWT secret
- Symptom: `Could not resolve placeholder 'APP_JWT_SECRET'`
- Fix: set `APP_JWT_SECRET` in `backend-services/secrets.properties`

### MySQL authentication failure
- Symptom: access denied for `root` or another configured user
- Fix: set `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` correctly

### Schema validation failure
- Symptom: Hibernate reports missing columns or tables during startup
- Fix: run `DatabaseInit.sql` for a reset or apply the needed changes from `DatabaseUpgrade.sql`

### Unknown database
- Symptom: `Unknown database 'ecommerce_db'` or Hibernate cannot determine dialect
- Fix: create `ecommerce_db` in the MySQL instance used by the backend, then run `DatabaseInit.sql`

### Redis stale serialization data
- Symptom: logs contain `Redis cache get failed`, `Could not read JSON`, or `missing type id property '@class'`
- Meaning: Redis is reachable, but a cached value was written by an older/incompatible serializer or DTO shape
- Fix: clear local Redis cache entries and retry the request

### Java target mismatch
- Symptom: Maven fails with `release version 25 not supported`
- Fix: install/configure Java 25 for native project builds

### Docker unavailable during `mvn verify`
- Symptom: Testcontainers reports that it could not find a valid Docker environment
- Meaning: Failsafe discovered the integration tests, but Docker is not running or is inaccessible
- Fix: start Docker Desktop or another compatible Docker engine, verify `docker info`, then rerun `mvn verify`

## Validation Commands

`mvn test` runs the unit and architecture tests. It does not run classes named `*IT`.

`mvn verify` runs the unit tests and all Testcontainers integration tests through Maven Failsafe. Use it as the complete backend gate.

The frontend currently has no automated test suite. Run `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build`, and the manual smoke checklist under `docs/frontend/` until E2E coverage is added.

## Current Notes: Load-Test Payment Gateway

Use this local-only gateway for k6 checkout capacity tests. It avoids Stripe/Razorpay network calls and keeps payment behavior controlled on one laptop.

```properties
APP_PAYMENT_GATEWAY=loadtest
APP_LOADTEST_PAYMENT_DELAY_MS=50
APP_LOADTEST_PAYMENT_FAILURE_RATE=0
APP_LOADTEST_PAYMENT_WEBHOOK_SECRET=loadtest-secret
APP_LOADTEST_PAYMENT_CHECKOUT_BASE_URL=http://localhost:5173/checkout/loadtest
```

Restart the backend after changing `APP_PAYMENT_GATEWAY` or any payment gateway secret.

Useful delay/failure sweeps:

```properties
APP_LOADTEST_PAYMENT_DELAY_MS=50
APP_LOADTEST_PAYMENT_DELAY_MS=250
APP_LOADTEST_PAYMENT_DELAY_MS=1000
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.01
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.05
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.20
```

## Current Notes: Local Metrics During Load Tests

Actuator endpoints are exposed for local test measurement:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

Useful laptop-local checks while k6 is running:

```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
redis-cli info stats
redis-cli info memory
mysql -h 127.0.0.1 -P 3306 -u root -p -e "SHOW FULL PROCESSLIST; SHOW GLOBAL STATUS LIKE 'Threads_connected'; SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';"
```

When testing through Nginx with a self-signed local certificate, pass `--insecure-skip-tls-verify` to k6 and set `BASE_URL=https://localhost`.

## Current Notes: Redis Failure Visibility

The backend keeps serving requests when Redis cache operations fail. This is intentional for availability, but it can hide cache outages if logs and metrics are not watched.

Watch for:

- repeated `Redis cache get failed`, `put failed`, `evict failed`, or `clear failed` warnings
- Redis availability and latency changes
- cache hit/miss behavior where available
- rising MySQL query load and Hikari pending connections while Redis is unhealthy

`RedisCacheManager` is transaction-aware, so cache changes are applied after DB commit instead of during a transaction that may roll back.
