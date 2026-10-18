# Nginx Deployment Runbook

## Files

- Site template: `nginx/conf.d/commerce-platform.conf`
- Main config: `nginx/nginx.conf`
- Deploy script: `nginx/deploy_wsl.sh`
- Backend upstream config: `nginx/backend-upstream.conf`

## Template Rendering

The site template contains one `__BACKEND_UPSTREAM__` placeholder in the named upstream block:

```nginx
upstream commerce_backend {
  server __BACKEND_UPSTREAM__;
}
```

The deploy script reads `BACKEND_UPSTREAM` from `nginx/backend-upstream.conf`, validates the value, and renders the final site file from the template.

Expected format:

```properties
BACKEND_UPSTREAM=host:port
```

## Important Paths

- Rendered site path: `/etc/nginx/conf.d/commerce-platform.conf`
- Frontend deploy path: `/var/www/commerce-platform/frontend/dist`
- TLS certificate path: `/etc/nginx/certs/commerce-platform.crt`
- TLS key path: `/etc/nginx/certs/commerce-platform.key`

## Deploy Script Behavior

`nginx/deploy_wsl.sh` currently:

- ensures Nginx, rsync, curl, and Node are available
- builds the frontend in WSL
- copies frontend assets to the deploy directory
- renders the Nginx template using `BACKEND_UPSTREAM`
- removes stale local site links that previously caused config-test failures
- optionally validates backend reachability through `/api/v1/products/categories`
- reloads Nginx when TLS cert files exist

## Troubleshooting

### Placeholder validation fails
- Cause: the template is missing `__BACKEND_UPSTREAM__` in the `commerce_backend` upstream block
- Fix: verify the upstream block contains the placeholder before deployment

### Backend probe fails
- Cause: backend host/port is wrong or backend is not reachable from WSL/Nginx
- Fix: update `nginx/backend-upstream.conf` with the correct `host:port`

### TLS reload is skipped
- Cause: certificate or key file is missing
- Fix: place the certificate files at the configured `commerce-platform` paths

## Current Notes: Load-Test Timing

For Nginx-vs-backend attribution during k6 runs, the default access log now includes upstream timing fields.

Useful fields:

```nginx
$request_time $upstream_response_time $upstream_status
```

Run direct backend tests first with `BASE_URL=http://localhost:8080`, then repeat through Nginx by changing only `BASE_URL`.
