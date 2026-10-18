# Readability and Maintainability Recommendations

Date: 2026-03-17  
Project: `backend-services` and `frontend`

## Confirmed Improvements
- Backend user naming now consistently distinguishes login identity (`email`) from display label (`displayName`) in code, while keeping the existing database column and API field compatibility.
- Stale backend test references now use the same display-name terminology as production code.
- Frontend admin and manager route protection still flows through one shared `RoleRoute`.
- Frontend login redirect creation now uses one shared helper, so checkout and route guards produce the same redirect behavior.
- Frontend low-level role membership checks now use one shared helper while keeping route and page authorization decisions local.
- Admin manager creation still uses the same simple field-change and API-error handling pattern as the rest of the frontend.

## Open Recommendations

### 1. Keep Page-Level Async Flows Local Unless They Become Truly Identical (Low)
**Current state**:
- `Body`, `AdminDashboard`, `ManageUsers`, and `ProductDetails` now follow a more consistent loading/error flow.
- They still differ enough in UI behavior that a generic fetch abstraction would make the code harder to read.

**Recommendation**:
- Keep these flows local for now.
- Revisit extraction only if future pages repeat the exact same state transitions and rendering contract.

### 2. Document Authorization Ownership in One Small Note (Low)
**Current state**:
- Route-level authorization is handled in frontend guards.
- Object-level and privileged-action authorization stays in backend services/controllers.

**Recommendation**:
- Add one short architecture note defining where coarse-grained and object-level authorization should live.

### 3. Keep Public `username` API Fields Stable Until a Broader Contract Cleanup Is Approved (Low)
**Current state**:
- Internal backend code is clearer with `displayName`.
- Public request/response DTOs still expose `username` for compatibility with the current frontend.

**Recommendation**:
- Keep this as-is for now.
- If a future contract cleanup happens, rename the public field across backend and frontend together in one coordinated change.

## Intentionally Retained Duplication
- Route-level components still make their own access decisions after low-level helper calls so a reader can see authorization behavior without chasing abstractions.
- Page-specific async loading remains local where extracting it would hide page behavior.

## Java 25 Usage Notes
- Modern Java features were used where they improved readability, such as obvious `var` assignments and pattern matching for display-name resolution.
- Java 25 features were intentionally not forced into JPA entities or framework-heavy code paths where explicit classes and members remain clearer.

## Expected Outcome
The codebase is easier to scan because naming is more precise, low-level auth mechanics are centralized, and duplicated logic is only removed where it was creating inconsistency rather than clarity.
