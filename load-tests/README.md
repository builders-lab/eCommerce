# Backend Load Testing with k6

This directory contains k6 scripts for backend-focused load testing.

## Execution Order

Run the tests in this order:

1. `scripts/smoke.js`
2. `scripts/browse.js`
3. `scripts/auth-cart.js`

Run the expanded scripts later in the file when you need checkout, webhook, contention, soak, abuse, upload, or Nginx-backed coverage.

## Requirements

- Backend running on `BASE_URL`, default `http://localhost:8080`
- MySQL and Redis running
- k6 installed
- Test users available in `data/users.csv`, or `CREATE_USERS=true` for one-time setup signup

## Environment Variables

Common:

```bash
BASE_URL=http://localhost:8080
CREATE_USERS=false
LOADTEST_WEBHOOK_SECRET=loadtest-secret
```

Smoke user:

```bash
SMOKE_EMAIL=loaduser001@example.com
SMOKE_PASSWORD=secret123
```

Load shape:

```bash
BROWSE_VUS=10
BROWSE_DURATION=2m
CART_VUS=10
CART_DURATION=2m
```

## Commands

Run commands from the script directory so fixture paths resolve consistently:

```bash
cd load-tests/scripts
```

Smoke:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true smoke.js
```

Browse, cold cache:

```bash
# Clear Redis first, then run:
k6 run -e BASE_URL=http://localhost:8080 browse.js
```

Browse, warm cache:

```bash
k6 run -e BASE_URL=http://localhost:8080 browse.js
k6 run -e BASE_URL=http://localhost:8080 browse.js
```

Authenticated cart:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true auth-cart.js
```

## Notes

- Do not use one shared account for cart tests. `data/users.csv` intentionally contains many users.
- Do not include real Stripe calls in backend capacity tests.
- Run direct backend tests before Nginx tests. Use Nginx later by changing `BASE_URL`.
- Keep a repeatable database/Redis reset process for comparable write-heavy runs.
- If the backend listens only on IPv6 loopback, use `BASE_URL=http://[::1]:8080` for direct backend tests.
- When testing through local HTTPS Nginx with a self-signed certificate, pass `--insecure-skip-tls-verify` and use `BASE_URL=https://localhost`.

## Current Notes: Comprehensive Local Load Suite

The original quick-start above is still valid for smoke, browse, and normal cart checks. The expanded suite adds backend breaking-point, checkout, webhook, contention, soak, abuse, and upload tests for laptop-local runs.

Use the backend fake payment gateway for checkout tests:

```bash
APP_PAYMENT_GATEWAY=loadtest
APP_LOADTEST_PAYMENT_DELAY_MS=50
APP_LOADTEST_PAYMENT_FAILURE_RATE=0
APP_LOADTEST_PAYMENT_WEBHOOK_SECRET=loadtest-secret
```

Run commands from `load-tests/scripts` unless noted otherwise.

`data/users.csv` currently contains 1000 generated load users. Scripts refuse to run with more VUs than available users to avoid overstating capacity with one shared account.

### Expanded Scripts

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true validate-fixtures.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true smoke.js
k6 run -e BASE_URL=http://localhost:8080 browse.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true auth-burst.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true auth-cart.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true cart-contention.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true checkout.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true webhook.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true mixed.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true breakpoint.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true soak.js
k6 run -e BASE_URL=http://localhost:8080 abuse.js
k6 run -e BASE_URL=http://localhost:8080 -e ADMIN_EMAIL=admin@ecommerce.com -e ADMIN_PASSWORD=password upload.js
```

### Baseline Sequence

1. Reset write-heavy state when you need comparable runs:

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p ecommerce_db < ../sql/reset-load-test-state.sql
```

Optionally add a larger generated catalog before high-VU browse/cart/checkout runs. Browse, cart, checkout, mixed, webhook, and breakpoint scripts discover live products from the backend and fall back to `data/products.json` only when discovery returns no products:

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p ecommerce_db < ../sql/seed-load-test-products.sql
```

2. Cold-cache browse:

```bash
redis-cli flushall
k6 run -e BASE_URL=http://localhost:8080 browse.js
```

3. Warm-cache browse:

```bash
k6 run -e BASE_URL=http://localhost:8080 browse.js
k6 run -e BASE_URL=http://localhost:8080 browse.js
```

4. Normal authenticated flows:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true auth-burst.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true auth-cart.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true checkout.js
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true mixed.js
```

5. Repeat through Nginx by changing only `BASE_URL`.

### Breaking-Point Runs

Use `BREAKPOINT_STAGES` to ramp until latency or error thresholds fail:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true -e BREAKPOINT_STAGES=10:1m,25:1m,50:2m,75:2m,100:2m,150:2m,200:2m breakpoint.js
```

For a larger Nginx run with result capture:

```bash
mkdir -p ../results
k6 run --insecure-skip-tls-verify \
  -e BASE_URL=https://localhost \
  -e CREATE_USERS=false \
  -e LOADTEST_WEBHOOK_SECRET=loadtest-secret \
  -e BREAKPOINT_STAGES=100:2m,200:2m,300:2m,400:2m,500:2m \
  --summary-export ../results/breakpoint-nginx-500-summary.json \
  --out json=../results/breakpoint-nginx-500-metrics.json \
  breakpoint.js
```

`breakpoint.js` allows a longer setup phase because signup/login for hundreds of users can exceed k6's default setup timeout.

Record the highest stage that remains stable before any of these fail:

- `http_req_failed >= 3%`
- scenario p95 exceeds the threshold
- p99 spikes continuously
- backend CPU or memory is saturated
- Hikari pending connections remain above zero
- MySQL lock waits or connection pressure rise continuously
- Redis latency or memory becomes unstable

### Failure Behavior Runs

Cart contention:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true -e CONTENTION_VUS=50 cart-contention.js
```

Payment delay sweep:

```bash
APP_LOADTEST_PAYMENT_DELAY_MS=50
APP_LOADTEST_PAYMENT_DELAY_MS=250
APP_LOADTEST_PAYMENT_DELAY_MS=1000
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true checkout.js
```

Payment failure sweep:

```bash
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.01
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.05
APP_LOADTEST_PAYMENT_FAILURE_RATE=0.20
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true checkout.js
```

Webhook replay and invalid signatures:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true webhook.js
```

### Soak Runs

Run this at the last stable load from the breakpoint test:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e CREATE_USERS=true -e SOAK_VUS=50 -e SOAK_DURATION=30m soak.js
```

Watch for memory growth, DB connection growth, Redis memory growth, latency drift, or rising error rate.

### Component Measurement

Useful backend metrics:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

Useful Redis/MySQL checks:

```bash
redis-cli info stats
redis-cli info memory
mysql -h 127.0.0.1 -P 3306 -u root -p -e "SHOW FULL PROCESSLIST; SHOW GLOBAL STATUS LIKE 'Threads_connected'; SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';"
```

Use direct backend results as the baseline. Nginx adds value only after backend-only behavior is understood.
