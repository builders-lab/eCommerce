# Commerce Platform

Full-stack e-commerce application with a React frontend, a Spring Boot backend, MySQL persistence, Redis cache-aside caching, and Nginx-based deployment support.

## Current Capabilities

- JWT-based signup and login
- Role-aware administration for users, managers, products, and discounts
- Product catalog with categories, images, discounts, and reviews
- Cart and checkout flows with payment gateway abstraction
- Stripe and Razorpay payment integrations
- Local load-test payment gateway for capacity tests without external payment calls
- Redis-backed cache-aside reads for catalog, reviews, images, categories, and cart responses
- Secure image upload and public image serving
- Nginx reverse proxy support for `/api` and `/auth`

## How The App Fits Together

- The React frontend talks to the backend through `/auth`, `/api/v1/**`, and `/images/**`.
- The Spring Boot backend owns authentication, authorization, catalog, cart, checkout, payments, media, and administration workflows.
- MySQL is the source of truth for users, products, carts, orders, payments, reviews, and media metadata.
- Redis is a cache-aside performance layer only. If Redis has stale or incompatible cache entries, the app should keep serving from MySQL while logging cache warnings.
- Checkout creates an immutable order snapshot before redirecting to the configured payment provider. Cart clearing happens only after a verified successful payment event.
- Nginx can serve the built frontend and proxy backend routes for local HTTPS or deployment-style testing.

## Stack

- Frontend: React, TypeScript, Vite, MUI
- Backend: Spring Boot 4.1, Java 25
- Database: MySQL 8
- Cache: Redis
- Reverse proxy: Nginx

## Repository Layout

```text
.
|-- backend-services/   Spring Boot backend
|-- frontend/           React frontend
|-- nginx/              Nginx config and deploy script (deployment, not local Docker)
|-- docs/               Architecture, frontend, operations, reference, and reports
|-- docker-compose.yml  One-command local stack (MySQL, Redis, backend, frontend)
|-- Makefile            Convenience wrapper around docker compose
|-- DatabaseInit.sql    Full schema reset + seed data
|-- DatabaseUpgrade.sql Non-destructive schema evolution script
```

## Quick Start (Docker)

The whole stack runs with one command on Linux, macOS, and Windows. No local Java, Maven, Node, MySQL, or Redis needed.

```bash
docker compose up --build -d
```

Then open **http://localhost:8080**.

| Service | URL | Notes |
|---|---|---|
| Store (nginx + built SPA) | http://localhost:8080 | Proxies `/api`, `/auth`, `/images` to the backend |
| Backend | http://localhost:8081 | `/actuator/health` |
| MySQL | localhost:3306 | `ecommerce_db`, root / `admin` |
| Redis | localhost:6379 | Cache only |

`DatabaseInit.sql` is mounted into the MySQL init directory, so the schema and seed data are created automatically the first time the database volume is initialised.

Prefer make?

```bash
make up      # build and start
make logs    # tail all services
make down    # stop, keep data
make db-reset # wipe the database and reload the seed
make help    # list every target
```

On Windows without `make`, run the compose command directly — it is the same thing.

Every setting has a working default, so no `.env` file is required. To override, create a `.env` file beside `docker-compose.yml`:

```properties
MYSQL_ROOT_PASSWORD=admin
APP_JWT_SECRET=replace-with-a-random-secret-at-least-32-chars
APP_PAYMENT_GATEWAY=loadtest
FRONTEND_PORT=8080
BACKEND_PORT=8081
```

Tear down completely, including the database volume:

```bash
docker compose down -v
```

For running services directly on your machine instead, see below.

## Local Development

These steps are only needed if you want to run the services directly on your machine instead of using the Docker stack above.

### Prerequisites

- Java 25 for the backend target
- Maven
- Node.js 20.19+ or 22.12+
- MySQL 8
- Redis 7-compatible server
- Docker for the complete Testcontainers-backed backend verification suite

### 1. Initialize the database

Create the `ecommerce_db` database in MySQL, then run:

```sql
SOURCE DatabaseInit.sql;
```

`DatabaseInit.sql` recreates the schema and seeds sample data.

Use `DatabaseUpgrade.sql` only when you need to evolve an existing database without resetting it.

### 2. Configure backend secrets

Copy `backend-services/secrets.properties.example` to `backend-services/secrets.properties` and fill in the values you need.

The backend loads that file automatically when started from either `backend-services/` or the repository root.

Minimum local values:

```properties
APP_JWT_SECRET=replace-with-a-random-secret-at-least-32-chars
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/ecommerce_db
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-mysql-password
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
```

Optional but commonly needed:

```properties
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,https://localhost
APP_PAYMENT_GATEWAY=stripe
APP_STRIPE_SECRET_KEY=sk_test_replace_me
APP_STRIPE_PUBLISHABLE_KEY=pk_test_replace_me
APP_STRIPE_WEBHOOK_SECRET=whsec_replace_me
```

### 3. Start the backend

```bash
cd backend-services
mvn spring-boot:run
```

Backend default URL:

```text
http://localhost:8080
```

The backend uses Redis only as a performance cache. MySQL remains the source of truth. If cached list data was created by an older local build, clear Redis once with `redis-cli flushall` or delete the relevant cache keys.

### 4. Start the frontend

```bash
cd frontend
npm ci
npm start
```

Frontend default URL:

```text
http://localhost:5173
```

### 5. Validate the repository

Frontend validation intentionally excludes automated UI tests until the planned E2E suite is added:

```bash
cd frontend
npm run lint
npm run typecheck
npm run build
npm audit --omit=dev
```

Backend unit and architecture tests do not require Docker:

```bash
cd backend-services
mvn test
```

The complete backend suite requires a working Docker engine because the integration tests use Testcontainers:

```bash
mvn verify
```

Until E2E coverage is added, follow `docs/frontend/manual-smoke-checklist.md` for frontend workflow validation.

## Deployment Notes

The Nginx deployment assets live under `nginx/`.

- Site template: `nginx/conf.d/commerce-platform.conf`
- Main Nginx config: `nginx/nginx.conf`
- Deploy script: `nginx/deploy_wsl.sh`
- Backend upstream config: `nginx/backend-upstream.conf`

The Nginx site template proxies both `/api` and `/auth` using the `__BACKEND_UPSTREAM__` placeholder, which the deploy script renders from `nginx/backend-upstream.conf`.

The deploy script also:

- builds the frontend
- deploys static assets under `/var/www/commerce-platform/frontend/dist`
- renders the Nginx site file
- validates the upstream placeholder usage

## Seeded Accounts

`DatabaseInit.sql` seeds these accounts:

- `admin@ecommerce.com`
- `user@ecommerce.com`
- `manager@ecommerce.com`

Passwords are bcrypt-hashed in the SQL seed file. If you need the plain-text test password, check the project's seed/auth documentation before sharing it outside local development.

## Documentation Map

- `docs/README.md`
- `docs/reference/api-reference.md`
- `docs/architecture/module-boundaries.md`
- `docs/architecture/runtime-cache-portability.md`
- `docs/frontend/route-map.md`
- `docs/frontend/state-and-auth.md`
- `docs/frontend/api-integration.md`
- `docs/frontend/page-flow.md`
- `docs/operations/environment-runbook.md`
- `docs/operations/nginx-deployment-runbook.md`
- `docs/operations/db-change-log.md`
- `docs/reports/scalability-maintainability-report.md`
- `docs/reports/documentation-audit.md`

## Known Setup Pitfalls

- The backend default runtime uses `spring.jpa.hibernate.ddl-auto=validate`, so schema drift will fail startup.
- Spring Boot 4.1 uses Spring Framework 7, Jakarta EE 11, and the Servlet 6.1 baseline.
- For WSL/Linux runs, use Linux paths such as `/mnt/c/Dev/e_commerce`; do not use PowerShell syntax like `$env:...`.
- The default media path is relative to `backend-services`, so run the backend from that directory or set `APP_MEDIA_RESOURCE_LOCATION` explicitly.
- If Redis logs `missing type id property '@class'`, Redis is reachable but contains stale serialized cache entries. Clear local Redis and retry.
- If the backend fails on startup, first check:
  - JWT secret presence
  - MySQL credentials
  - Redis reachability for cache-backed reads
  - schema state versus `DatabaseInit.sql` or `DatabaseUpgrade.sql`

## API Surface

Primary public/authenticated route groups:

- `/auth`
- `/api/v1/products`
- `/api/v1/cart`
- `/api/v1/checkout`
- `/api/v1/payments/webhook/{gateway}`
- `/api/v1/users`
- `/api/v1/images/upload`

For the full endpoint inventory, use `docs/reference/api-reference.md`.
