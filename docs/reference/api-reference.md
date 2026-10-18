# Full System Features and API Reference

Date: 2026-03-16  
Project: `e_commerce` (`backend-services` + `frontend`)

## 1. System Overview

This system is a role-based e-commerce app with:
- JWT auth (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_MANAGER`, optional `ROLE_EMPLOYEE`)
- Product catalog with category filtering and discounts
- Cart with additive discount calculation
- Checkout with pluggable payment gateway (`stripe`, `razorpay`, or local `loadtest`)
- Admin/manager user and product management
- Secure image upload for product media
- Redis cache-aside reads for high-traffic catalog, review, image, category, role, and cart responses
- k6 load-test scripts for smoke, browse, authenticated cart, checkout, webhook, mixed, breakpoint, soak, abuse, and upload scenarios

## 2. Backend Features by Module

### Auth
- Signup and login with JWT token issuance.
- Login enforces account status (`enabled=true` and not flagged/locked).
- Manager account registration by admin.

### Users
- Paginated user listing for admin/manager.
- Flag/unflag/delete users with role-based restrictions.
- User discount assignment with date windows.
- Employee role toggle by admin.

### Catalog
- Paginated product listing with optional category filter.
- Product details with images and discounts.
- Distinct category listing.
- Product create/update/delete by privileged roles.

### Reviews
- Paginated product reviews with deterministic sorting (`createdAt DESC, id DESC`).
- Add review for authenticated user.

### Cart
- User-scoped cart read/mutate endpoints.
- Add/increment item, quantity updates, discount selection, remove, clear.
- Cart pricing computes base price, final price, and stacked discounts:
  product discount + user discount + employee discount (capped at 100%).
- Optimistic locking on cart/cart-item entities.

### Checkout and Payments
- `POST /api/v1/checkout` creates order snapshot + payment session.
- Payment provider selected by config (`app.payment.gateway`).
- Current providers: Stripe, Razorpay, and local load-test.
- Webhook processing endpoint is gateway-agnostic:
  `POST /api/v1/payments/webhook/{gateway}`.
- Order status polling endpoint:
  `GET /api/v1/checkout/{orderId}`.
- Cart clearing is driven by a verified successful payment event, not by the browser redirect alone.

### Media
- Image upload restricted to admin/manager.
- MIME and file-signature verification (`png`, `jpeg`, `webp`).
- Upload size limit and safe path handling.
- Static image serving via `/images/**`.

## 3. Frontend Features

### Public User Flows
- Product catalog home with category sidebar and responsive grid.
- Product detail page with:
  - discount selection
  - effective price display
  - add-to-cart
  - buy-now
  - review display

### Authenticated User Flows
- Protected checkout page.
- Cart quantity update/remove flows.
- Checkout initiation via backend payment session URL redirection.
- Payment success flow returns through the configured provider success URL. Stripe requires webhook forwarding for local payment completion.

### Admin Flows
- Admin dashboard with product list and delete action.
- Add product form with image upload.
- Add manager form.
- Manage users page:
  - flag/unflag
  - delete
  - user discount dates/percentage
  - employee role toggle

### Manager Flows
- Manager dashboard.
- Add product.
- Manage users with manager-limited actions.

## 4. Backend API Reference

All non-2xx errors are returned in `ApiError` format:
- `timestamp`, `status`, `error`, `message`, `path`, `details`

Paginated responses use:
- `PageResponse<T>`: `items`, `page`, `size`, `totalItems`, `totalPages`, `hasNext`

| Method | Path | Auth | What it does |
|---|---|---|---|
| POST | `/auth/signup` | Public | Create user account and return JWT + user profile. |
| POST | `/auth/login` | Public | Login and return JWT + user profile. |
| GET | `/api/v1/products` | Public | Paginated product list, optional `category`, validated `page/size`. |
| GET | `/api/v1/products/{id}` | Public | Get product details including discounts. |
| GET | `/api/v1/products/categories` | Public | Get distinct categories. |
| POST | `/api/v1/products` | Admin/Manager | Create product. |
| PUT | `/api/v1/products/{id}` | Admin/Manager | Update product. |
| DELETE | `/api/v1/products/{id}` | Admin | Delete product. |
| GET | `/api/v1/products/{productId}/reviews` | Public | Paginated reviews for product. |
| POST | `/api/v1/products/{productId}/reviews` | Authenticated | Add review for product. |
| GET | `/api/v1/cart` | Authenticated | Get current user cart. |
| POST | `/api/v1/cart/items` | Authenticated | Add/increment cart item with optional discount selection. |
| PATCH | `/api/v1/cart/items/{productId}` | Authenticated | Set item quantity (`0` removes item). |
| PATCH | `/api/v1/cart/items/{productId}/discount` | Authenticated | Set/remove selected discount for cart item. |
| DELETE | `/api/v1/cart/items/{productId}` | Authenticated | Remove cart item. |
| DELETE | `/api/v1/cart` | Authenticated | Clear cart. |
| POST | `/api/v1/checkout` | Authenticated | Create checkout order + payment session URL. |
| GET | `/api/v1/checkout/{orderId}` | Authenticated | Get checkout/payment status for own order (admin can view any). |
| POST | `/api/v1/checkout/{orderId}/simulate-payment` | Authenticated | Settle a local `loadtest` order by replaying a signed webhook event. Returns 404 unless `APP_PAYMENT_GATEWAY=loadtest`. |
| POST | `/api/v1/payments/webhook/{gateway}` | Public | Verify/process provider webhook event (`stripe`, `razorpay`, local `loadtest`). |
| GET | `/api/v1/users` | Admin/Manager | Paginated user list. |
| PATCH | `/api/v1/users/{id}/flag` | Manager | Flag user (manager restrictions enforced). |
| PATCH | `/api/v1/users/{id}/unflag` | Admin | Unflag user. |
| DELETE | `/api/v1/users/{id}` | Admin | Delete user. |
| PATCH | `/api/v1/users/{id}/discount` | Admin/Manager | Set user discount percentage + date range. |
| PATCH | `/api/v1/users/{id}/employee` | Admin | Add/remove employee role. |
| POST | `/api/v1/users/managers` | Admin | Create manager account and return a user/admin DTO. |
| POST | `/api/v1/images/upload` | Admin/Manager | Upload product image and return public URL. |
| GET | `/images/{file}` | Public | Serve uploaded image resource. |

## 5. Role and Access Matrix (Practical)

- Public:
  - Browse products/categories/reviews.
  - Signup/login.
- Authenticated user:
  - Cart operations.
  - Add review.
  - Checkout and own order status.
- Manager:
  - Product create/update.
  - User list.
  - Flag and discount updates with object-level restrictions.
- Admin:
  - Full user administration.
  - Product delete.
  - Manager creation.
  - Employee role toggles.

## 6. Payment Gateway Configuration

Main switch:
- `APP_PAYMENT_GATEWAY=stripe`, `razorpay`, or `loadtest`

Common:
- `APP_PAYMENT_DEFAULT_CURRENCY=usd` (or `inr`)

Stripe:
- `APP_STRIPE_SECRET_KEY`
- `APP_STRIPE_PUBLISHABLE_KEY`
- `APP_STRIPE_WEBHOOK_SECRET`

Razorpay:
- `APP_RAZORPAY_KEY_ID`
- `APP_RAZORPAY_KEY_SECRET`
- `APP_RAZORPAY_WEBHOOK_SECRET`

Load-test gateway:
- `APP_LOADTEST_PAYMENT_DELAY_MS`
- `APP_LOADTEST_PAYMENT_FAILURE_RATE`
- `APP_LOADTEST_PAYMENT_WEBHOOK_SECRET`
- `APP_LOADTEST_PAYMENT_CHECKOUT_BASE_URL`

## 7. Frontend Route Map

| Route | Access | Purpose |
|---|---|---|
| `/` | Public | Main catalog page with filters and product grid. |
| `/product/:id` | Public | Product details, discount selection, reviews, add-to-cart. |
| `/login` | Public | Login with redirect support. |
| `/signup` | Public | Signup form. |
| `/checkout` | Authenticated | Cart review + place order (payment redirect). |
| `/admin/dashboard` | Admin | Admin home and product management entry points. |
| `/admin/add-product` | Admin | Create product. |
| `/admin/add-manager` | Admin | Create manager user. |
| `/admin/users` | Admin | User management actions. |
| `/manager/dashboard` | Manager/Admin | Manager home. |
| `/manager/add-product` | Manager/Admin | Create product. |
| `/manager/users` | Manager/Admin | Limited user management. |

