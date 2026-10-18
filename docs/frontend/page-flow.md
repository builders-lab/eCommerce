# Frontend Page Flow

## Catalog Flow

- `MainPage` loads categories and product pages through `ApiService`
- category filtering is sent through the product list query string
- `ProductCard` links into the product detail route

## Product Detail Flow

- `ProductDetails` loads one product and its reviews
- authenticated users can submit a review
- add-to-cart and buy-now flows go through `CartContext` and route the user toward checkout

## Checkout Flow

- `Buy` reads cart state from `CartContext`
- unauthenticated users are redirected to login with a return target
- starting checkout calls `POST /api/v1/checkout`
- when the backend returns `checkoutUrl`, the frontend redirects the browser to the payment session

## Admin and Manager Product Flow

- `AddProduct` is shared by admin and manager routes
- admin and manager dashboards link into add-product and user-management workflows
- admin-only access is enforced for manager creation

## User Management Flow

- `ManageUsers` lists users and performs role-based mutations
- admin and manager access differ at the route and backend authorization layers
