#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${REPO_ROOT}/frontend"
BUILD_DIR="${FRONTEND_DIR}/dist"
DEPLOY_DIR="/var/www/commerce-platform/frontend/dist"
NGINX_CONF_SRC="${REPO_ROOT}/nginx/nginx.conf"
NGINX_SITE_SRC="${REPO_ROOT}/nginx/conf.d/commerce-platform.conf"
BACKEND_CFG="${REPO_ROOT}/nginx/backend-upstream.conf"
NGINX_CONF_DST="/etc/nginx/nginx.conf"
NGINX_SITE_DST="/etc/nginx/conf.d/commerce-platform.conf"
CERT_PATH="/etc/nginx/certs/commerce-platform.crt"
KEY_PATH="/etc/nginx/certs/commerce-platform.key"
BACKEND_PROBE_PATH="/api/v1/products/categories"
BACKEND_PROBE_TIMEOUT=5

echo "==> Installing Nginx if missing"
if ! command -v nginx >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y nginx
fi

if ! command -v rsync >/dev/null 2>&1; then
  sudo apt-get install -y rsync
fi

if ! command -v curl >/dev/null 2>&1; then
  sudo apt-get install -y curl
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found. Please install Node.js/npm in WSL before running this script."
  exit 1
fi

NODE_BIN=""
if command -v node >/dev/null 2>&1; then
  NODE_BIN="node"
elif command -v nodejs >/dev/null 2>&1; then
  NODE_BIN="nodejs"
else
  echo "Node.js runtime not found. Install Node.js 18+ in WSL and retry."
  exit 1
fi

NODE_MAJOR="$(${NODE_BIN} -p 'process.versions.node.split(".")[0]' 2>/dev/null || echo 0)"
if [[ "${NODE_MAJOR}" -lt 18 ]]; then
  echo "Node.js 18+ is required. Current version: $(${NODE_BIN} -v 2>/dev/null || echo unknown)"
  echo "Install a newer Node.js (recommended: Node 20 LTS) and retry."
  exit 1
fi

echo "==> Building frontend"
cd "${FRONTEND_DIR}"
ROLLUP_NATIVE_PACKAGE="$(${NODE_BIN} -p '(() => {
  const platform = process.platform;
  const arch = process.arch;
  const isMusl = platform === "linux" && (() => {
    try {
      const report = process.report?.getReport?.();
      return !report?.header?.glibcVersionRuntime;
    } catch {
      return false;
    }
  })();

  if (platform === "linux" && arch === "x64") return isMusl ? "@rollup/rollup-linux-x64-musl" : "@rollup/rollup-linux-x64-gnu";
  if (platform === "linux" && arch === "arm64") return isMusl ? "@rollup/rollup-linux-arm64-musl" : "@rollup/rollup-linux-arm64-gnu";
  if (platform === "win32" && arch === "x64") return "@rollup/rollup-win32-x64-msvc";
  if (platform === "win32" && arch === "arm64") return "@rollup/rollup-win32-arm64-msvc";
  if (platform === "darwin" && arch === "x64") return "@rollup/rollup-darwin-x64";
  if (platform === "darwin" && arch === "arm64") return "@rollup/rollup-darwin-arm64";
  return "";
})()' 2>/dev/null || echo "")"
ROLLUP_NATIVE_PATH=""
if [[ -n "${ROLLUP_NATIVE_PACKAGE}" ]]; then
  ROLLUP_NATIVE_PATH="node_modules/${ROLLUP_NATIVE_PACKAGE#@rollup/}"
fi

needs_frontend_install=false
if [[ ! -d "node_modules" || ! -x "node_modules/.bin/vite" ]]; then
  needs_frontend_install=true
fi

if [[ -n "${ROLLUP_NATIVE_PATH}" && ! -d "${ROLLUP_NATIVE_PATH}" ]]; then
  echo "==> Detected missing Rollup native package for this runtime: ${ROLLUP_NATIVE_PACKAGE}"
  echo "==> Existing node_modules likely came from a different OS/runtime. Reinstalling frontend dependencies in WSL."
  rm -rf node_modules
  needs_frontend_install=true
fi

if [[ "${needs_frontend_install}" == true ]]; then
  echo "==> Installing frontend dependencies"
  if [[ -f "package-lock.json" ]]; then
    npm ci --include=optional || npm install --include=optional
  else
    npm install --include=optional
  fi
fi
npm run build

echo "==> Deploying build artifacts to ${DEPLOY_DIR}"
sudo mkdir -p "${DEPLOY_DIR}"
sudo rsync -a --delete "${BUILD_DIR}/" "${DEPLOY_DIR}/"

echo "==> Linking Nginx configuration"
sudo mkdir -p /etc/nginx/conf.d /etc/nginx/certs
sudo ln -sf "${NGINX_CONF_SRC}" "${NGINX_CONF_DST}"
sudo rm -f /etc/nginx/conf.d/ecommerce.conf /etc/nginx/sites-enabled/ecommerce.conf /etc/nginx/sites-enabled/default

if [[ ! -f "${BACKEND_CFG}" ]]; then
  echo "Missing ${BACKEND_CFG}"
  echo "Create nginx/backend-upstream.conf from .example and set BACKEND_UPSTREAM."
  exit 1
fi

raw_upstream_line="$(grep -E '^[[:space:]]*BACKEND_UPSTREAM=' "${BACKEND_CFG}" | tail -n1 || true)"
if [[ -z "${raw_upstream_line}" ]]; then
  echo "BACKEND_UPSTREAM not found in ${BACKEND_CFG}"
  echo "Create nginx/backend-upstream.conf from .example and set BACKEND_UPSTREAM."
  exit 1
fi

BACKEND_UPSTREAM="${raw_upstream_line#*=}"
BACKEND_UPSTREAM="$(printf '%s' "${BACKEND_UPSTREAM}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"

if [[ -z "${BACKEND_UPSTREAM}" ]]; then
  echo "BACKEND_UPSTREAM is empty in ${BACKEND_CFG}"
  exit 1
fi

if [[ ! "${BACKEND_UPSTREAM}" =~ ^[A-Za-z0-9._-]+:[0-9]{1,5}$ ]]; then
  echo "Invalid BACKEND_UPSTREAM='${BACKEND_UPSTREAM}' in ${BACKEND_CFG}"
  echo "Expected format: host:port (no scheme/path)."
  exit 1
fi

probe_backend() {
  local upstream="$1"
  local url="http://${upstream}${BACKEND_PROBE_PATH}"
  curl --silent --show-error --fail --max-time "${BACKEND_PROBE_TIMEOUT}" "${url}" >/dev/null
}

detect_wsl_gateway_ip() {
  ip route 2>/dev/null | awk '/^default via / { print $3; exit }'
}

pick_backend_upstream() {
  local configured_upstream="$1"
  local configured_port="${configured_upstream##*:}"
  local gateway_ip
  local -a candidates=("${configured_upstream}")

  gateway_ip="$(detect_wsl_gateway_ip)"

  if [[ -n "${gateway_ip}" && "${gateway_ip}:${configured_port}" != "${configured_upstream}" ]]; then
    candidates+=("${gateway_ip}:${configured_port}")
  fi

  if [[ "localhost:${configured_port}" != "${configured_upstream}" && "localhost:${configured_port}" != "${gateway_ip}:${configured_port}" ]]; then
    candidates+=("localhost:${configured_port}")
  fi

  for candidate in "${candidates[@]}"; do
    echo "==> Probing backend upstream ${candidate}${BACKEND_PROBE_PATH}" >&2
    if probe_backend "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
    echo "==> Backend probe failed for ${candidate}" >&2
  done

  return 1
}

if ! EFFECTIVE_BACKEND_UPSTREAM="$(pick_backend_upstream "${BACKEND_UPSTREAM}")"; then
  echo "Unable to reach the backend from WSL."
  echo "Checked:"
  echo "  - ${BACKEND_UPSTREAM}${BACKEND_PROBE_PATH}"
  CURRENT_WSL_GATEWAY="$(detect_wsl_gateway_ip)"
  BACKEND_PORT="${BACKEND_UPSTREAM##*:}"
  if [[ -n "${CURRENT_WSL_GATEWAY}" && "${CURRENT_WSL_GATEWAY}:${BACKEND_PORT}" != "${BACKEND_UPSTREAM}" ]]; then
    echo "  - ${CURRENT_WSL_GATEWAY}:${BACKEND_PORT}${BACKEND_PROBE_PATH}"
  fi
  if [[ "localhost:${BACKEND_PORT}" != "${BACKEND_UPSTREAM}" ]]; then
    echo "  - localhost:${BACKEND_PORT}${BACKEND_PROBE_PATH}"
  fi
  echo "Start the backend on port 8080 or update ${BACKEND_CFG} with a reachable host:port."
  exit 1
fi

if [[ "${EFFECTIVE_BACKEND_UPSTREAM}" != "${BACKEND_UPSTREAM}" ]]; then
  echo "==> Configured BACKEND_UPSTREAM=${BACKEND_UPSTREAM} is unreachable from WSL."
  echo "==> Falling back to ${EFFECTIVE_BACKEND_UPSTREAM} for this deploy."
fi

placeholder_count="$( (grep -o '__BACKEND_UPSTREAM__' "${NGINX_SITE_SRC}" || true) | wc -l | tr -d ' ' )"
if [[ "${placeholder_count}" -lt 1 ]]; then
  echo "Template ${NGINX_SITE_SRC} must contain __BACKEND_UPSTREAM__ in the upstream block."
  exit 1
fi

echo "==> Using BACKEND_UPSTREAM=${EFFECTIVE_BACKEND_UPSTREAM}"
tmp_site="$(mktemp)"
sed "s|__BACKEND_UPSTREAM__|${EFFECTIVE_BACKEND_UPSTREAM}|g" "${NGINX_SITE_SRC}" > "${tmp_site}"
sudo cp "${tmp_site}" "${NGINX_SITE_DST}"
rm -f "${tmp_site}"
echo "==> Rendered ${NGINX_SITE_DST} from template"

if [[ -f "${CERT_PATH}" && -f "${KEY_PATH}" ]]; then
  echo "==> Testing and reloading Nginx"
  sudo nginx -t
  if command -v systemctl >/dev/null 2>&1; then
    sudo systemctl reload nginx || sudo systemctl restart nginx
  else
    sudo service nginx reload || sudo nginx -s reload
  fi
else
  echo "==> Skipping Nginx reload because TLS certs are missing:"
  echo "    ${CERT_PATH}"
  echo "    ${KEY_PATH}"
fi
