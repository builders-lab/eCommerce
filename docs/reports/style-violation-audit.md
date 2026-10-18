# Style and Convention Violation Audit

> Historical snapshot: this report records repository state on 2026-06-24 and is not the current status. See `remediation-2026-07-05.md` for the verified disposition of these findings.

Date: 2026-06-24

## Scope

This audit reviewed the repository for style, convention, documentation, and project-structure violations against the repo's own guidance. The review covered:

- Backend Spring modules under `backend-services`
- Frontend React/TypeScript under `frontend`
- Documentation, SQL, nginx, and load-test assets

Subagents independently reviewed backend, frontend, and docs/load-test areas. This document consolidates the concrete findings and recommended fixes.

## Verification

- `mvn -q -DskipTests compile` in `backend-services`: passed after allowing Maven dependency resolution.
- `npm.cmd run lint` in `frontend`: failed because local npm shims did not resolve `eslint`.
- `node .\node_modules\eslint\bin\eslint.js .` in `frontend`: passed, but `--print-config src\App.tsx` returned `undefined`, confirming TS/TSX source is not covered by ESLint.
- `node .\node_modules\vite\bin\vite.js build` in `frontend`: failed because Rollup's Windows optional native package `@rollup/rollup-win32-x64-msvc` is missing from `node_modules`.

## Findings

### High: Frontend ESLint Does Not Cover TypeScript Source

Evidence:
- `frontend/eslint.config.js:9` targets only `**/*.{js,jsx}`.
- `frontend/src` is TS/TSX-based.
- `node .\node_modules\eslint\bin\eslint.js --print-config src\App.tsx` returned `undefined`.

Why this violates the project style:
- `frontend/package.json:10` exposes `npm run lint` as the frontend style check.
- The current config leaves React hooks rules, unused-code checks, and refresh checks unenforced for most application code.

Recommended fix:
- Add `typescript-eslint` support.
- Include `**/*.{ts,tsx}` in ESLint config.
- Keep React hooks and React refresh rules active for TSX files.

### High: Backend Module Boundaries Are Leaking Across Repositories and Entities

Evidence:
- `backend-services/src/main/java/com/ecommerce/platform/modules/auth/service/AuthService.java:7-10` imports users entities and repositories.
- `AuthService.java:22-23` depends directly on `UserRepository` and `RoleRepository`.
- `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/Cart.java:4` imports `User`.
- `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/CartItem.java:4-5` imports catalog entities.
- `backend-services/src/main/java/com/ecommerce/platform/modules/checkout/model/CheckoutOrder.java:4` imports `User`.
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/model/Review.java:5` imports `Product`.

Why this violates the project style:
- `docs/architecture/module-boundaries.md` says modules should not access another module's repositories directly.
- The same document says other modules should reference owned data by IDs and avoid tight entity coupling.

Recommended fix:
- Add users-owned service/API methods for auth identity loading, role lookup, and user creation.
- Refactor cross-module JPA fields toward scalar IDs where feasible, then resolve display/read models through owning-module services.
- Treat checkout's existing snapshot pattern as the preferred direction for module extraction.

### High: Untyped Frontend Props Bypass TypeScript Intent

Evidence:
- `frontend/src/components/ProceedToBuyButton.tsx:7` destructures `fullWidth` from an untyped props object.
- `frontend/tsconfig.json:9` enables `noImplicitAny`.

Why this violates the project style:
- The app is TypeScript-first, but this component relies on implicit prop typing and would be caught if lint/type checks covered TSX consistently.

Recommended fix:
- Add a typed props interface, for example `interface ProceedToBuyButtonProps { fullWidth?: boolean }`.
- Ensure ESLint and `tsc --noEmit` are part of normal frontend verification.

### Medium: Users Controller Calls Auth Service Directly

Evidence:
- `backend-services/src/main/java/com/ecommerce/platform/modules/users/controller/UserController.java:7-10` imports auth DTOs/service/security.
- `UserController.java:27-31` stores `AuthService`.
- `UserController.java:80-83` creates managers through `authService.registerManager`.

Why this violates the project style:
- `docs/architecture/module-boundaries.md` says controllers call services within the same module.

Recommended fix:
- Move manager registration under the auth module, or make `UserController` call a users-owned manager creation service.
- If token issuance is still needed, expose that through a narrow auth-owned API instead of coupling the users controller to `AuthService`.

### Medium: ApiService Mixes Axios and Native Fetch

Evidence:
- `frontend/src/api/ApiService.tsx:20` defines the configured Axios API client.
- `ApiService.tsx:51` installs the unauthorized response interceptor.
- `ApiService.tsx:68`, `95`, `121`, and `139` use native `fetch` for catalog/review requests.

Why this violates the project style:
- `docs/frontend/api-integration.md` documents `ApiService.tsx` as the source of truth.
- The same doc notes unauthorized Axios responses trigger shared logout behavior.
- Native `fetch` bypasses that shared interceptor path.

Recommended fix:
- Convert catalog and review calls to the configured Axios client.
- Keep any content-type validation in small helpers if still needed.

### Medium: Frontend Requests a Page Size the Backend Rejects

Evidence:
- `frontend/src/api/ApiService.tsx:258` requests users with `size: 200`.
- `frontend/src/pages/admin/ManageUsers.tsx` depends on that service for admin user management.
- `docs/frontend/api-integration.md:42` already notes the backend max page size is `100`.

Why this violates the project style:
- The API integration doc calls out this frontend/backend contract mismatch as known coupling.

Recommended fix:
- Request `size: 100`.
- Add pagination in the frontend if the page needs more than 100 users.

### Medium: Documentation Change Log Does Not Track Current Schema Changes

Evidence:
- `docs/operations/backend-remediation-status.md:33` says schema changes must be reflected in `DatabaseInit.sql`, `DatabaseUpgrade.sql`, and the DB change log.
- `docs/operations/db-change-log.md:7` records only a subset of current schema evolution.
- `DatabaseUpgrade.sql:10`, `43`, `96`, and `101` include changes not reflected in the change log.

Why this violates the project style:
- The operations docs define the DB change log as part of the schema-change process.

Recommended fix:
- Add dated entries for all current schema, index, and FK additions.
- Include rollback notes or mark baseline-only items explicitly.

### Medium: Load-Test Fixture-VU Conventions Are Inconsistent

Evidence:
- `load-tests/README.md:106` says scripts refuse to run with more VUs than available users.
- `load-tests/scripts/soak.js:7` uses `SOAK_VUS`, but `soak.js:17` reuses mixed setup.
- `load-tests/scripts/mixed.js:31` validates `MIXED_VUS`, not `SOAK_VUS`.
- `load-tests/scripts/auth-burst.js:17` uses `AUTH_VUS`, but its setup does not fail when `AUTH_VUS > users.length`.

Why this violates the project style:
- The load-test README sets a repeatability convention to avoid overstated capacity from shared account reuse.

Recommended fix:
- Give `soak.js` its own setup validation for `SOAK_VUS`, or parameterize the shared setup.
- Add an `AUTH_VUS > users.length` failure check to `auth-burst.js`, or document account reuse as intentional for that script.

### Medium: Generated-Catalog Guidance Does Not Match Browse/Cart Scripts

Evidence:
- `load-tests/README.md:134` recommends seeding a larger catalog before high-VU browse/cart/checkout runs.
- `load-tests/scripts/browse.js:6` and `load-tests/scripts/auth-cart.js:8` select from `data/products.json`.
- `load-tests/data/products.json:1` contains the small static fixture set.

Why this violates the project style:
- The docs imply seeded catalog data affects browse/cart load shape, but the scripts use static product IDs.

Recommended fix:
- Make browse/cart scripts fetch live products like checkout does.
- Or update the docs and fixtures so the product source is explicit and representative.

### Medium: Footer Contains Unused Template Code

Evidence:
- `frontend/src/components/Footer.tsx:1`, `3`, and `4` import unused items.
- `Footer.tsx:6` defines unused `Item`.
- `Footer.tsx:17` has formatting inconsistent with most of the app.

Why this violates the project style:
- It is dead starter-template code in a production app component.
- The issue is currently hidden because TS/TSX linting is not active.

Recommended fix:
- Remove unused imports and dead styled component code.
- Rebuild the footer as a typed MUI component if it is still needed.

### Low: Review Display-Name Naming Regresses to Generic User

Evidence:
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/model/Review.java:16` stores the reviewer label as `user`.
- `Review.java:38-43` exposes `getUser`/`setUser`.
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/service/ReviewService.java:47` writes `authUser.getDisplayName()`.
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/dto/response/ReviewResponse.java:7` exposes `user`.

Why this violates the project style:
- `docs/architecture/readability-recommendations.md` says backend naming should distinguish login identity `email` from display label `displayName`.

Recommended fix:
- Rename internal model/service accessors to `displayName`.
- Keep DB/API compatibility with explicit mapping annotations or DTO conversion where needed.

### Low: Payment Defaults Are Duplicated

Evidence:
- `backend-services/src/main/resources/application.properties:30-33`, `45-54`, and `57-60` define payment defaults.
- Payment property records also define defaults with `@DefaultValue`, for example `LoadTestPaymentProperties.java:8-11`.

Why this violates the project style:
- `docs/architecture/module-boundaries.md` says module configuration should be property/env driven.
- Duplicated defaults create two sources of truth for config behavior.

Recommended fix:
- Keep local defaults in `application.properties`.
- Remove duplicate `@DefaultValue`s unless there is a specific runtime-binding reason.
- Validate required secrets explicitly in payment services.

### Low: Test Package Layout Does Not Mirror Module Layout

Evidence:
- Production packages use `com.ecommerce.platform.modules.<module>...`.
- Tests use sibling packages such as `com.ecommerce.platform.catalog`, `users`, `cart`, `auth`, `media`, `reviews`, and `checkout`.

Why this violates the project style:
- `docs/architecture/module-boundaries.md` describes a module-first package layout.

Recommended fix:
- Move tests under `com.ecommerce.platform.modules.<module>`, or document tests as an intentional exception.

### Low: API Reference Has Inconsistent Webhook Gateway Wording

Evidence:
- `docs/reference/api-reference.md:51` and `157` include the local `loadtest` provider.
- `docs/reference/api-reference.md:124` says webhook gateway values are only `stripe` and `razorpay`.

Why this violates the project style:
- The docs are expected to be the endpoint source of truth.

Recommended fix:
- Include `loadtest` in the webhook endpoint row, or label it local-only in the same row.

### Low: README and Environment Runbook Disagree on Minimum Settings

Evidence:
- `docs/operations/environment-runbook.md:18` lists `APP_CACHE_DEFAULT_TTL_MINUTES=10` with minimum local settings.
- `README.md:74` omits that variable from the minimum local block.

Why this violates the project style:
- The root README and runbook should give consistent local setup guidance.

Recommended fix:
- Add the cache TTL variable to the root README minimum block, or move it to optional settings in the runbook.

### Low: Nginx Deploy Script References a Missing Example File

Evidence:
- `docs/operations/nginx-deployment-runbook.md:20` documents `nginx/backend-upstream.conf`.
- `nginx/deploy_wsl.sh:114` and `121` say to create it from `.example`.
- No `nginx/*.example` file exists.

Why this violates the project style:
- Operational scripts should point to existing docs or files.

Recommended fix:
- Add `nginx/backend-upstream.conf.example`, or change the script message to point to the runbook and expected `BACKEND_UPSTREAM=host:port` format.

### Low: Generated Load-Test Results Are Not Ignored

Evidence:
- `git status --short` reports untracked files under `load-tests/results/`.
- `load-tests/results/` contains large generated JSON output files.
- `.gitignore` does not ignore `load-tests/results/`.

Why this violates the project style:
- Generated test artifacts should not appear as source changes or slow repository scans.

Recommended fix:
- Add `load-tests/results/` to `.gitignore`.
- Keep curated summaries only if they are intentionally part of a report.

### Low: Stale Load-Test Imports

Evidence:
- `load-tests/scripts/validate-fixtures.js:5` imports unused `randomItem`.
- `load-tests/scripts/upload.js:5` imports unused `jsonHeaders`.

Why this violates the project style:
- The k6 scripts otherwise follow a tidy ES-module style.

Recommended fix:
- Remove the unused imports.

## Recommended Fix Order

1. Fix frontend lint coverage and dependency install health, then run lint/build again.
2. Address backend module-boundary issues before they become harder to unwind.
3. Fix frontend API-service consistency and the `size: 200` contract mismatch.
4. Update load-test validation and docs so performance results remain trustworthy.
5. Clean up lower-risk naming, docs, generated-artifact, and unused-import issues.
