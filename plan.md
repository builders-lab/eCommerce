# Performance, Readability, and Redundancy Review Plan

Date: 2026-04-02

This file records the next planned work only. No additional implementation beyond this planning file should be done until explicitly approved.

## Current Baseline and Version Stance

I checked the current repository versions and compared them against official framework/vendor documentation before planning further changes.

### Repository baseline
- Backend:
  - Spring Boot: `3.5.0`
  - Java target: `25`
  - JJWT: `0.11.5`
- Frontend:
  - React: `19.1.0`
  - React Router: `7.6.2`
  - Vite: `7.2.7`
- Database:
  - MySQL runtime/test usage in this repo

### Official-source baseline to use for decisions
- Java:
  - Oracle docs show Java `25` is the current LTS release and Java `26` is the current non-LTS release.
  - For production guidance, prefer Java `25` as the stability baseline unless there is a clear reason to target current non-LTS behavior.
- Spring Boot:
  - Official docs show the current stable lines include `4.0.x` and maintained `3.5.x`.
  - Since the project is already on the `3.5.x` line and no upgrade was requested, use Spring Boot `3.5.x` practices as the immediate source of truth for implementation patterns.
  - Do not plan a Boot `4.x` upgrade in this pass.
- MySQL:
  - Oracle/MySQL docs distinguish Innovation vs LTS.
  - Prefer MySQL `8.4` LTS guidance rather than Innovation-release assumptions when suggesting production-oriented practices.
- React:
  - Official React docs indicate `19.2` is current stable.
  - Since frontend changes are not the main target in this pass, do not plan a React upgrade now; only use current React docs as reference if frontend review touches architecture or redundancy findings.

### Practical version policy for this pass
- No version upgrades are planned in this pass unless explicitly requested later.
- Prefer framework-documented best practices that are compatible with the repo’s current major versions.

## Planned Work Order

### Phase 1. Re-validate cart performance changes from current repo state
- Review the current cart/auth code as it exists after the JWT principal refactor.
- Confirm that the cart changes really meet the intended goals:
  - no generic post-mutation reloads where avoidable
  - one optimized cart read path for response building
  - no lazy cart-response assembly dependence
  - targeted optimistic-lock / first-cart-creation recovery
- Specifically verify that no accidental behavior drift was introduced in:
  - pricing
  - employee discounts
  - user discount date windows
  - checkout/cart interaction
  - review author behavior

### Phase 2. Review catalog/product/review endpoints for similar N+1 or query-shape issues
- Inspect product list, product detail, discount access, image loading, review list, and review creation flows.
- Identify any places where the code:
  - walks lazy associations while building DTOs
  - issues repeated per-row lookups
  - loads broader entity graphs than necessary
- Do not change behavior yet unless explicitly approved later.
- Produce a focused report of:
  - confirmed query-shape risks
  - likely N+1 candidates
  - practical fixes that fit the current architecture

### Phase 3. Whole-codebase redundancy and readability audit
- Traverse the backend codebase module by module and identify:
  - duplicated logic
  - redundant helper methods
  - stale patterns left behind after refactors
  - over-complicated code that could be simplified without changing behavior
  - places where records are appropriate for DTO/projection-style types
- Keep this audit grounded in current framework guidance and the structured monolith rules.
- The output should separate:
  - safe deletions / simplifications
  - refactors that require behavior risk review
  - readability-only improvements

### Phase 4. Testing and runtime validation
- Run backend verification commands to make sure the backend still builds and tests pass as far as the environment allows.
- Minimum checks:
  - compile
  - unit tests
  - targeted integration tests if Docker/Testcontainers is available
- If integration tests are blocked by local environment constraints, record that clearly and do not guess at runtime health.

## Constraints to Respect During Future Implementation

### Architectural constraints
- Controllers only call services in the same module.
- No cross-module repository access.
- Cross-module interaction must go through service/internal API methods.
- Each module owns its own tables/entities.
- DTOs must be used across module boundaries.

### Security constraints
- Keep stateless JWT authentication.
- No migration to server-side sessions.
- No fallback JWT secrets.
- Keep login/token-issuance checks explicit and strict.
- Do not reintroduce per-request auth DB lookups unless explicitly approved.

### Performance constraints
- Do not use virtual threads as the primary fix for DB-heavy paths.
- Prefer reducing SQL volume, query amplification, and contention directly.
- Keep `spring.jpa.open-in-view=false` compatible.

### Code quality constraints
- Prefer practical, readable code over clever abstractions.
- Use Java records where they fit well:
  - DTOs
  - projections
  - immutable command/result carriers
- Do not force records onto JPA entities or other framework-unfriendly shapes.
- Follow framework-documented recommendations where they are compatible with the current project version.
- Do not modify logic for readability alone until explicitly asked.

## Deliverables for the Next Approved Execution Pass

If explicitly approved to proceed, the next pass should produce:

1. A cart-performance verification report from the current code state
- what is already fixed
- what remains risky
- any correctness concerns found

2. A catalog/product/review query-shape audit
- confirmed N+1 candidates
- evidence paths
- recommended fixes in priority order

3. A whole-codebase redundancy/readability report
- redundant logic that can likely be deleted
- simplifications that improve readability
- places where records should replace verbose DTO-style classes if approved

4. Backend validation results
- exact commands run
- what passed
- what could not be run due to environment limits

## Known Environment Notes
- The backend currently compiles and basic Maven test execution works with Java 21 override on this machine.
- Some integration tests depend on Docker/Testcontainers and may fail here if Docker is unavailable.
- If full runtime validation is requested later, Docker availability must be confirmed or the limitation must be recorded.

## Official Sources Consulted for This Plan
- Oracle Java SE Support Roadmap:
  - https://www.oracle.com/java/technologies/java-se-support-roadmap.html
- Spring Boot System Requirements:
  - https://docs.spring.io/spring-boot/system-requirements.html
- MySQL Innovation vs LTS overview:
  - https://dev.mysql.com/blog-archive/introducing-mysql-innovation-and-long-term-support-lts-versions/
- React current release reference:
  - https://react.dev/blog/2025/10/01/react-19-2

## Stop Condition

No additional code changes should be made under this plan until explicit approval is given.
