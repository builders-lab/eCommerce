# Frontend Manual Smoke Checklist

This checklist is temporary. The frontend automated test setup was removed and will be replaced by a dedicated E2E suite.

## Prerequisites

1. Start MySQL, Redis, and the backend with a valid JWT secret.
2. Run `npm ci`, `npm run lint`, `npm run typecheck`, and `npm run build`.
3. Start the frontend with `npm start`.

## Routes and workflows

- Open `/` and confirm the catalog and category filter render after the accessible loading status.
- Open a product from the catalog and directly reload `/product/{id}`.
- Complete login and signup, then confirm login returns to the originally requested route.
- Open `/checkout` while signed out and confirm redirection to login.
- Sign in as a normal user and confirm admin and manager routes reject access.
- Sign in with admin and manager accounts and confirm their permitted dashboards and management routes load.
- Add a product to the cart, change quantity, remove an item, and complete the checkout handoff.
- Open `/checkout/success`, `/checkout/cancel`, and the configured local load-test checkout route.
- Open an unknown route and confirm it redirects to `/`.
- Navigate between several routes and confirm each lazy chunk loads without a blank screen or console error.
