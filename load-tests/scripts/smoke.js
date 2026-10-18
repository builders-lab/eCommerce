import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, jsonHeaders } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';

const smokeUser = {
  email: __ENV.SMOKE_EMAIL || 'loaduser001@example.com',
  password: __ENV.SMOKE_PASSWORD || 'secret123',
  username: __ENV.SMOKE_USERNAME || 'Load User 001',
};

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  ensureUser(smokeUser);
  return { token: login(smokeUser) };
}

export default function (data) {
  const categories = http.get(`${BASE_URL}/api/v1/products/categories`);
  check(categories, {
    'categories status is 200': (res) => res.status === 200,
  });

  const products = http.get(`${BASE_URL}/api/v1/products?page=0&size=20`);
  check(products, {
    'products status is 200': (res) => res.status === 200,
    'products returned items': (res) => Array.isArray(res.json('items')),
  });

  const cart = http.get(`${BASE_URL}/api/v1/cart`, jsonHeaders(data.token));
  check(cart, {
    'cart status is 200': (res) => res.status === 200,
    'cart returned items': (res) => Array.isArray(res.json('items')),
  });
}
