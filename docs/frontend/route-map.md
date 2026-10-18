# Frontend Route Map

Source of truth: `frontend/src/App.tsx`

## Public Routes

- `/`: main catalog page with sidebar, product list, and category filtering
- `/product/:id`: product detail page with discounts, reviews, add-to-cart, and buy-now flow
- `/login`: login page with redirect support
- `/signup`: account creation page

## Authenticated Route

- `/checkout`: protected by `ProtectedRoute`
  - unauthenticated users are redirected to `/login?redirect=...`
  - authenticated users can review cart contents and start checkout

## Admin-Only Routes

Protected by `AdminRoute`:

- `/admin/dashboard`
- `/admin/add-product`
- `/admin/add-manager`
- `/admin/users`

## Manager/Admin Routes

Protected by `ManagerRoute`:

- `/manager/dashboard`
- `/manager/add-product`
- `/manager/users`

## Fallback Route

- `*` redirects to `/`

## Guard Behavior

- `ProtectedRoute` checks `isAuthenticated` from `AuthContext`
- `AdminRoute` allows `ROLE_ADMIN`
- `ManagerRoute` allows `ROLE_ADMIN` and `ROLE_MANAGER`
- Login redirect targets are built through `buildLoginRedirectPath()`
