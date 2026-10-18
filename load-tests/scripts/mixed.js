import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

import { BASE_URL, envNumber, extractQueryParam, jsonHeaders, parseCsv, pickWeighted, validateVuCapacity } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';
import { checkout, buildLoadtestWebhook, postLoadtestWebhook } from '../lib/payment.js';
import { chooseProduct, fetchProducts } from '../lib/products.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const fallbackProducts = new SharedArray('products', () => JSON.parse(open('../data/products.json')));

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.MIXED_RAMP_UP || '1m', target: envNumber('MIXED_VUS', 25) },
        { duration: __ENV.MIXED_DURATION || '5m', target: envNumber('MIXED_VUS', 25) },
        { duration: __ENV.MIXED_RAMP_DOWN || '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{scenario:mixed}': ['p(95)<1200', 'p(99)<3000'],
  },
};

export function setup() {
  return setupMixed(envNumber('MIXED_VUS', 25), 'MIXED_VUS');
}

export function setupMixed(requestedVus, envName = 'MIXED_VUS') {
  validateVuCapacity(envName, requestedVus, users);
  return {
    tokens: users.map((user) => {
      ensureUser(user);
      return login(user);
    }),
    products: fetchProducts(envNumber('PRODUCT_FETCH_PAGES', 5), envNumber('PRODUCT_FETCH_SIZE', 100)),
  };
}

export default function (data) {
  const action = pickWeighted([
    { value: 'browse', weight: envNumber('MIXED_BROWSE_WEIGHT', 55) },
    { value: 'cart', weight: envNumber('MIXED_CART_WEIGHT', 30) },
    { value: 'checkout', weight: envNumber('MIXED_CHECKOUT_WEIGHT', 10) },
    { value: 'login', weight: envNumber('MIXED_LOGIN_WEIGHT', 5) },
  ]);
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const product = chooseProduct(data.products, fallbackProducts);
  const params = jsonHeaders(token);

  if (action === 'browse') {
    check(http.get(`${BASE_URL}/api/v1/products?page=0&size=20`), { 'mixed browse list ok': (res) => res.status === 200 });
    check(http.get(`${BASE_URL}/api/v1/products/${product.id}`), { 'mixed browse detail ok': (res) => res.status === 200 });
    check(http.get(`${BASE_URL}/api/v1/products/${product.id}/reviews?page=0&size=20`), { 'mixed browse reviews ok': (res) => res.status === 200 });
  }

  if (action === 'cart') {
    check(http.get(`${BASE_URL}/api/v1/cart`, params), { 'mixed cart read ok': (res) => res.status === 200 });
    check(http.post(`${BASE_URL}/api/v1/cart/items`, JSON.stringify({ productId: product.id, quantity: 1 }), params), {
      'mixed cart add ok': (res) => res.status === 200,
    });
  }

  if (action === 'checkout') {
    http.post(`${BASE_URL}/api/v1/cart/items`, JSON.stringify({ productId: product.id, quantity: 1 }), params);
    const response = checkout(token);
    if (response.status === 200 && (__ENV.MIXED_COMPLETE_PAYMENT || 'true').toLowerCase() === 'true') {
      const payload = buildLoadtestWebhook(
        'checkout.session.completed',
        extractQueryParam(response.json('checkoutUrl'), 'sessionId'),
        extractQueryParam(response.json('checkoutUrl'), 'paymentId'),
        `${__VU}_${__ITER}`
      );
      postLoadtestWebhook(payload);
    }
    check(response, { 'mixed checkout ok': (res) => res.status === 200 });
  }

  if (action === 'login') {
    const user = users[(__VU - 1) % users.length];
    check(http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email: user.email, password: user.password }), jsonHeaders()), {
      'mixed login ok': (res) => res.status === 200,
    });
  }

  sleep(envNumber('MIXED_SLEEP_SECONDS', 1));
}
