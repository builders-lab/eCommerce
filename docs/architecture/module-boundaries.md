# Module Boundaries (Structured Monolith)

This project is organized as a module-first (package-by-feature) monolith to make
future microservice extraction predictable and low-risk.

## Goals
- Keep domain logic isolated per module.
- Make cross-module dependencies explicit and small.
- Allow extraction of a module with minimal code movement.

## Module list
- auth: authentication and JWT issuance
- users: user management and roles
- catalog: products, categories, discounts
- cart: cart and cart items
- checkout: purchase flow orchestration
- reviews: product reviews
- media: image upload and static resource serving

## Dependency rules
- Controllers call services within the same module.
- Modules should not access another module's repositories directly.
- Cross-module access should go through a service method, or a thin "API" class
  inside the owning module.
- Shared utilities live under `com.ecommerce.platform.common`.

## Data boundaries (for future extraction)
- Each module owns its tables/entities and should be the single writer.
- Other modules should reference by IDs only (avoid tight entity coupling).

## Security and config
- Cross-cutting concerns (security, CORS, error handling) live under `com.ecommerce.platform.config`.
- All module configuration should be driven by properties/env vars, not hard-coded.
