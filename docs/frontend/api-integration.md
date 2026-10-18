# Frontend API Integration

Source of truth: `frontend/src/api/ApiService.tsx`

## API Prefixes

- `VITE_API_PREFIX` defaults to `/api/v1`
- `VITE_AUTH_PREFIX` defaults to `/auth`

## Main Endpoint Groups Used by the Frontend

### Auth
- `POST /auth/login`
- `POST /auth/signup`

### Catalog
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/categories`

### Reviews
- `GET /api/v1/products/{productId}/reviews`
- `POST /api/v1/products/{productId}/reviews`

### Cart and Checkout
- `GET /api/v1/cart`
- `POST /api/v1/cart/items`
- `PATCH /api/v1/cart/items/{productId}`
- `PATCH /api/v1/cart/items/{productId}/discount`
- `DELETE /api/v1/cart/items/{productId}`
- `DELETE /api/v1/cart`
- `POST /api/v1/checkout`

### Admin and Manager
- product create/update/delete endpoints
- user management endpoints under `/api/v1/users`
- image upload endpoint under `/api/v1/images/upload`

## Known Coupling Points

- `fetchProducts()` requests `size=100`, matching the current backend validation cap
- `getAllUsers()` currently requests `size=200`, which does not match the backend max page size of `100`
- checkout expects a `checkoutUrl` in the backend response and redirects the browser to it
- unauthorized Axios responses trigger the shared logout flow through the response interceptor
