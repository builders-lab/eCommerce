# Query Shape Follow-up Audit

Date: 2026-04-02

## Summary

- The cart path is now materially improved compared with the original audit baseline:
  - per-request auth DB lookup is removed
  - cart responses no longer rely on lazy entity traversal
  - generic post-mutation reloads are gone
  - first-cart creation races and optimistic-lock conflicts now have targeted handling
- The next query-shape concerns are mostly in catalog/product detail and review response assembly.
- Virtual threads remain the wrong primary lever for these remaining issues because the dominant risks are still SQL shape and DB contention.

## Cart Status

### What is now improved
- `JwtAuthenticationFilter` no longer loads the user from MySQL on every request.
- Cart response assembly now uses a dedicated cart read query rather than walking lazy associations from `Cart`.
- Cart mutations no longer do a generic `findById(...)` cart reload after every write.
- Cart creation now recovers from unique `user_id` races by re-reading the cart.
- Cart mutation flow now retries bounded optimistic-lock failures and surfaces a controlled conflict if retry is exhausted.

### Remaining cart notes
- The cart response path now does:
  1. one cart item projection query
  2. one batched image lookup query
- That is a good practical improvement and no longer shows the earlier lazy-loading amplification pattern.
- Discount selection during mutation still loads the product with discounts, but this is now intentionally scoped to the mutation path rather than leaking into response assembly.

## Catalog/Product Findings

### 1. Product detail still has a small query-shape inefficiency

Files:
- `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/service/ProductService.java`
- `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/repository/ProductRepository.java`

Observation:
- `getProductById(...)` loads product detail via `findDetailedById(...)`, which eagerly loads `images` only.
- `toDetailResponse(...)` then traverses `product.getDiscounts()`.

Impact:
- This is not an N+1 issue because the endpoint returns one product.
- It is still an extra lazy query on the product detail path.

Recommendation:
- If optimizing product detail later, fetch the discount data as part of the detail read path instead of loading it lazily during response mapping.

### 2. Product list path is in reasonably good shape

Files:
- `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/service/ProductService.java`
- `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/repository/ProductRepository.java`

Observation:
- Product list uses a page query for products and a second batched query for all images in the page.
- DTO assembly then uses the preloaded image map.

Impact:
- This avoids the worst image-related N+1 pattern.

Recommendation:
- Leave this path as-is unless future profiling shows the two-query shape needs further tuning.

## Review Findings

### 1. Review list mapping likely causes avoidable lazy product access

Files:
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/service/ReviewService.java`
- `backend-services/src/main/java/com/ecommerce/platform/modules/reviews/repository/ReviewRepository.java`

Observation:
- `getReviewsByProductId(...)` loads `Review` entities and maps them with `toResponse(...)`.
- `toResponse(...)` reads `review.getProduct().getId()`.

Impact:
- Since the query is already filtered by `productId`, reading the product relation again is redundant.
- Depending on persistence state, that can introduce avoidable lazy proxy initialization while building the page response.

Recommendation:
- Prefer a review projection or a lighter mapper path that does not touch `review.getProduct()` when the product id is already known from the query context.

### 2. Review create path is acceptable

Observation:
- Creating a review needs the product lookup for ownership/association and then returns the saved review.
- This does not currently show the same broad N+1 pattern as cart originally did.

Recommendation:
- No immediate performance change needed here.

## Other Query-shape Notes

### Checkout path

Files:
- `backend-services/src/main/java/com/ecommerce/platform/modules/checkout/service/CheckoutService.java`

Observation:
- Checkout now benefits from the improved cart read path because it consumes `CartResponse` instead of walking cart entities itself.

Recommendation:
- No immediate query-shape concern found here beyond normal order/payment persistence cost.

## Recommended Priority

1. Leave cart/auth changes in place and observe them under real traffic.
2. Optimize product detail discount loading if that endpoint becomes hot.
3. Simplify review list response assembly so it does not touch lazy product state unnecessarily.
4. Re-profile before making deeper repository/query changes.

## Validation Notes

> Historical validation note: the commands below describe the environment available when this audit was written. Current validation uses Java 25; see `remediation-2026-07-05.md`.

- Backend compile/test command succeeded locally with Java 21 overrides:
  - `mvn "-Djava.version=21" "-Dmaven.compiler.release=21" clean test`
- Targeted integration tests are Docker/Testcontainers-backed and could not be executed in this environment because Docker was unavailable.
