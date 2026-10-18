  # Cart and Auth Performance Audit

  Date: 2026-03-31  
  Scope: cart mutation paths, cart response loading, JWT authentication lookup behavior, and the likely impact of virtual threads on these bottlenecks.

  ## Summary

  This audit found four meaningful performance and scalability risks in the current backend:

  1. Authenticated requests perform a database-backed user lookup inside `JwtAuthenticationFilter` on every request.
  2. Cart mutation paths perform extra database work beyond the minimum needed for the write itself.
  3. Cart response construction is vulnerable to lazy-loading amplification and likely N+1 query behavior.
  4. Concurrent cart mutations for the same user can race and currently rely mainly on optimistic locking plus error handling.

  Virtual threads are not enabled in the current backend configuration, and even if they were, they would not reduce SQL volume or database contention in these paths.

  ## Findings

  ### 1. Per-request DB lookup in `JwtAuthenticationFilter`

  Severity: High

  Evidence:
  - `backend-services/src/main/java/com/ecommerce/platform/modules/auth/security/JwtAuthenticationFilter.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/users/repository/UserRepository.java`

  Observed behavior:
  - After parsing the JWT, `JwtAuthenticationFilter` calls `userRepository.findByIdWithRoles(...)` before creating the Spring Security authentication object.
  - This means every authenticated request pays at least one database round-trip before business logic begins.

  Why it matters:
  - This adds fixed database load to all authenticated traffic.
  - It increases latency and makes the database part of the critical path for authentication even when the JWT is otherwise valid.

  ### 2. Extra DB calls in cart mutation paths

  Severity: High

  Evidence:
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/service/CartService.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/service/ProductService.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/repository/ProductRepository.java`

  Observed behavior:
  - `addOrIncrement`, `setQuantity`, `removeItem`, and `updateItemDiscount` all begin by loading or creating the cart.
  - The same flows also load the product entity.
  - Item update paths then query `cart_items` for the matching row.
  - After persisting the mutation, the service reloads the cart again before building the response.
  - Discount resolution may also trigger product-discount lazy loading.

  Typical mutation shape today:
  1. Load or create cart
  2. Load product
  3. Load matching cart item or delete by cart/product
  4. Persist change
  5. Reload cart for response

  Why it matters:
  - The mutation endpoint is doing more read work than necessary.
  - Under load, these extra queries amplify pressure on MySQL and make write paths more expensive than they need to be.

  ### 3. Lazy-loading and query amplification in cart responses

  Severity: High

  Evidence:
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/service/CartService.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/Cart.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/CartItem.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/catalog/model/Product.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/repository/CartRepository.java`

  Observed behavior:
  - `CartService.toResponse(...)` iterates `cart.getItems()` and builds DTOs by touching:
    - `item.getSelectedDiscount()`
    - `item.getCart().getUser()`
    - `item.getProduct().getPrice()`
    - `item.getProduct().getName()`
    - `item.getProduct().getImages()`
  - Cart items, product references, selected discount, product discounts, and product images are lazy-loaded associations.
  - The cart repository does not provide a fetch-join or entity-graph query shaped specifically for cart response assembly.

  Why it matters:
  - This creates a strong risk of N+1 query behavior.
  - The post-mutation cart reload likely triggers more SQL as item/product/image/discount/user data is lazily traversed inside the transaction.

  ### 4. Potential cart contention and same-user races

  Severity: Medium

  Evidence:
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/Cart.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/model/CartItem.java`
  - `backend-services/src/main/java/com/ecommerce/platform/modules/cart/service/CartService.java`
  - `backend-services/src/main/java/com/ecommerce/platform/common/error/GlobalExceptionHandler.java`

  Observed behavior:
  - `Cart` and `CartItem` both use optimistic locking via `@Version`.
  - `Cart` has a unique user mapping, and `CartItem` has a unique constraint on `(cart_id, product_id)`.
  - `getOrCreateCart(user)` follows a read-then-create pattern, which can race on the first cart creation for a user.
  - Item mutations also follow a read-then-write pattern that can collide under concurrent requests for the same user and product.
  - The global exception handler maps optimistic locking failures to `409 Conflict`, but there is no observed retry or serialization strategy in the cart path itself.

  Why it matters:
  - Concurrent same-user requests may surface as conflicts or unique-constraint failures instead of being smoothed out server-side.
  - This is more visible once traffic increases or clients retry aggressively.

  ## Virtual Threads Assessment

  I did not find virtual-thread enablement in the current backend code or configuration.

  Current runtime notes:
  - `spring.jpa.hibernate.ddl-auto=validate`
  - `spring.jpa.open-in-view=false`

  Implications:
  - Virtual threads would not reduce SQL query count.
  - Virtual threads would not eliminate lock contention on cart rows or cart items.
  - If MySQL is the bottleneck, virtual threads can allow the app to drive more concurrent database work and expose the bottleneck sooner.

  Conclusion:
  - The current bottlenecks are query-shape and contention problems, not platform-thread exhaustion problems.

  ## Possible Fixes

  ### A. Fix options for per-request JWT user lookup

  #### 1. Put role and status claims into the JWT

  How it works:
  - Include user id, roles, enabled/locked state, and possibly a token version in the token itself.

  Advantages:
  - Removes the per-request user query almost entirely
  - Lowest steady-state auth latency
  - Scales well for read-heavy authenticated traffic

  Disadvantages:
  - Role or status changes are not visible until token expiry unless revocation/versioning is added
  - Requires stronger token lifecycle design

  #### 2. Cache the user lookup by user id

  How it works:
  - Cache `findByIdWithRoles(...)` results using a short TTL in local memory or Redis.

  Advantages:
  - Smaller change than a full auth redesign
  - Reduces database load quickly
  - Preserves DB-backed validation of user state

  Disadvantages:
  - Can serve stale roles or account state during the cache TTL
  - Best results require invalidation when user roles or flags change

  #### 3. Hybrid approach: JWT claims plus version/revocation support

  How it works:
  - Keep roles in the JWT but include a version, revocation key, or forced-logout mechanism.

  Advantages:
  - Better balance between speed and revocation control
  - Supports administrative lockout or role refresh better than pure stateless JWT

  Disadvantages:
  - More moving pieces
  - Usually still needs some shared state

  #### 4. Session-backed authentication

  Advantages:
  - Easy invalidation and centralized user state
  - Strong runtime control over account changes

  Disadvantages:
  - Larger architectural shift
  - Reintroduces server-side session state

  ### B. Fix options for extra DB calls in cart mutation paths

  #### 1. Use a dedicated fetch plan for cart response loading

  How it works:
  - Replace plain cart reloads with a cart query that fetches the exact associations needed for the response.

  Advantages:
  - Reduces lazy-load amplification
  - Makes SQL count more predictable
  - Good balance between performance and maintainability

  Disadvantages:
  - More careful query design needed
  - Complex fetch joins can become heavy if overused

  #### 2. Stop doing a generic post-mutation cart reload

  How it works:
  - Reuse the managed entity state where safe, or reload through one optimized query instead of a plain `findById(...)`.

  Advantages:
  - Removes an avoidable round-trip
  - Keeps mutation endpoints leaner

  Disadvantages:
  - Requires confidence that the response shape is fully initialized
  - Incomplete fetch planning can still trigger lazy-load queries

  #### 3. Use targeted repository operations for hot mutations

  How it works:
  - Use more direct repository methods or SQL for increment, set quantity, and remove flows.

  Advantages:
  - Fewer read-before-write steps
  - Better for hot cart operations

  Disadvantages:
  - More repository complexity
  - Can spread business logic across service and repository layers

  #### 4. Simplify discount resolution

  How it works:
  - Reduce the amount of product discount state loaded during cart mutation when only one selected discount decision is needed.

  Advantages:
  - Lowers per-mutation query pressure

  Disadvantages:
  - May complicate discount logic or require more specialized queries

  #### 5. Return lighter mutation responses

  How it works:
  - Return a mutation summary instead of a fully enriched cart after every write.

  Advantages:
  - Potentially large reduction in SQL and serialization cost

  Disadvantages:
  - API contract change
  - Frontend may need an extra cart refresh call

  ### C. Fix options for lazy-loading/query amplification in cart responses

  #### 1. Dedicated DTO projection query

  How it works:
  - Query directly into response DTOs instead of walking entity graphs lazily.

  Advantages:
  - Most predictable SQL behavior
  - Often the best performance for read-heavy response assembly
  - Avoids accidental N+1 patterns

  Disadvantages:
  - More custom query code
  - Less reusable than entity-based mapping

  #### 2. `@EntityGraph` or explicit fetch joins

  Advantages:
  - Cleaner than many separate lazy fetches
  - Lower implementation cost than full projection-heavy redesign

  Disadvantages:
  - Multiple nested collections can make joins expensive or awkward
  - Easier to get partial improvement than a perfect fetch shape

  #### 3. Batch fetching

  How it works:
  - Configure Hibernate to batch-load lazy associations in fewer queries.

  Advantages:
  - Smaller code change
  - Can significantly reduce N+1 without major service rewrites

  Disadvantages:
  - Still multiple queries
  - Less explicit and less predictable than a dedicated fetch plan

  #### 4. Second-level cache for product and discount data

  Advantages:
  - Good for mostly-read catalog data
  - Can reduce repeated catalog-side lookups across requests

  Disadvantages:
  - Does not fix poor cart query shape by itself
  - Requires cache invalidation discipline

  ### D. Fix options for cart contention and same-user races

  #### 1. Keep optimistic locking and add retries

  How it works:
  - Retry a small number of cart mutations when optimistic locking conflicts occur.

  Advantages:
  - Small conceptual change
  - Works well if conflicts are infrequent

  Disadvantages:
  - Adds more work during contention
  - Does not remove the underlying race, only makes it more tolerable

  #### 2. Use pessimistic locking for cart mutation

  How it works:
  - Lock the cart or cart-item row during mutation.

  Advantages:
  - Stronger correctness guarantees for concurrent writes

  Disadvantages:
  - Lower throughput
  - Risk of lock waits and deadlocks
  - Can make performance worse if contention becomes common

  #### 3. Use atomic SQL updates for quantity changes

  How it works:
  - Push increments and updates into single database statements where possible.

  Advantages:
  - Reduces read-modify-write race windows
  - Strong fit for hot quantity-update paths

  Disadvantages:
  - Harder to combine with richer discount/business rules
  - More custom SQL and repository code

  #### 4. Serialize cart mutations per user

  How it works:
  - Ensure only one cart mutation runs at a time per user through locking, queuing, or distributed coordination.

  Advantages:
  - Strong correctness
  - Eliminates a full class of same-user conflicts

  Disadvantages:
  - More architectural overhead
  - Added latency and coordination complexity

  #### 5. Recover explicitly from first-cart creation races

  How it works:
  - Catch unique-constraint failures on cart creation and re-read the existing cart.

  Advantages:
  - Small targeted fix
  - Useful for the cart bootstrap race

  Disadvantages:
  - Solves only one race pattern, not all concurrent cart updates

  ## Can Cache Help?

  Yes, but selectively.

  Cache helps most with:
  - JWT user lookup
  - Product data
  - Discount data
  - Possibly cart read models if the product requirements tolerate staleness

  Cache helps much less with:
  - Cart mutation correctness
  - Same-user concurrent writes
  - Optimistic lock conflicts
  - Unique-constraint races

  Why:
  - Caching reduces repeated reads.
  - Caching does not remove database write contention.
  - Poorly designed cart caching can introduce stale cart behavior very quickly.

  ## Recommended Direction

  If improving this area incrementally without a large redesign, the most practical order is:

  1. Reduce or cache the per-request JWT user lookup
  2. Replace generic cart reloads with one optimized cart read query
  3. Shape cart response loading to avoid lazy-load amplification
  4. Add targeted conflict handling for cart races
  5. Consider catalog-side caching only after query shape is improved

  ## Assumptions and Limitations

  - This audit is based on code inspection rather than SQL trace capture.
  - The lazy-loading/query-amplification findings are therefore inference-based, but they are strongly supported by the current entity mappings and service access patterns.
  - No evidence of virtual-thread enablement was found in the backend code or configuration during this review.
