import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

import { BASE_URL, envNumber, jsonHeaders, parseCsv, pickWeighted } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';
import { chooseProduct, fetchProducts } from '../lib/products.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const fallbackProducts = new SharedArray('products', () => JSON.parse(open('../data/products.json')));
const appErrorRate = new Rate('app_error_rate');

const stages = (__ENV.BREAKPOINT_STAGES || '10:1m,25:1m,50:2m,75:2m,100:2m,150:2m,200:2m')
  .split(',')
  .map((stage) => {
    const [target, duration] = stage.split(':');
    return { target: Number(target), duration };
  });

export const options = {
  setupTimeout: '5m',
  scenarios: {
    breakpoint: {
      executor: 'ramping-vus',
      stages,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.03'],
    app_error_rate: ['rate<0.03'],
    'http_req_duration{scenario:breakpoint}': ['p(95)<1500', 'p(99)<4000'],
  },
};

export function setup() {
  const maxVus = Math.max(...stages.map((stage) => stage.target));
  if (maxVus > users.length) {
    fail(`Max BREAKPOINT_STAGES target=${maxVus} exceeds users.csv count=${users.length}.`);
  }
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
    { value: 'browse', weight: 60 },
    { value: 'cart', weight: 35 },
    { value: 'login', weight: 5 },
  ]);
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const product = chooseProduct(data.products, fallbackProducts);

  if (action === 'browse') {
    const response = http.get(`${BASE_URL}/api/v1/products/${product.id}`);
    appErrorRate.add(response.status >= 500);
    check(response, { 'breakpoint browse ok': (res) => res.status === 200 });
  }

  if (action === 'cart') {
    const response = http.post(
      `${BASE_URL}/api/v1/cart/items`,
      JSON.stringify({ productId: product.id, quantity: 1, discountId: null }),
      jsonHeaders(token)
    );
    appErrorRate.add(response.status >= 500);
    check(response, { 'breakpoint cart add ok': (res) => res.status === 200 });
  }

  if (action === 'login') {
    const user = users[(__VU - 1) % users.length];
    const response = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email: user.email, password: user.password }), jsonHeaders());
    appErrorRate.add(response.status >= 500);
    check(response, { 'breakpoint login ok': (res) => res.status === 200 });
  }

  sleep(envNumber('BREAKPOINT_SLEEP_SECONDS', 1));
}
