# Frontend State and Auth

## AuthContext

Source: `frontend/src/global_component/AuthContext.tsx`

Responsibilities:

- restores token and stored user from `localStorage`
- keeps `isAuthenticated` and `user` in memory
- exposes `login()` and `logout()` helpers
- wires `ApiService.setUnauthorizedHandler()` to force logout on backend `401` responses

Stored keys:

- `token`
- `user`

Behavior notes:

- invalid or corrupted stored auth data is cleared automatically
- `ApiService.setAuthToken()` keeps Axios auth headers aligned with the stored JWT

## CartContext

Source: `frontend/src/global_component/CartContext.tsx`

Responsibilities:

- loads the authenticated user's cart from the backend
- exposes `addToCart`, `updateQuantity`, `removeFromCart`, `clear`, and `reload`
- performs optimistic updates before syncing with backend responses
- logs the user out when cart requests return `401` or `403`

## Role-Based Access

Role guards are implemented through:

- `RoleRoute`
- `AdminRoute`
- `ManagerRoute`

The effective roles are taken from the authenticated user stored in `AuthContext`.
