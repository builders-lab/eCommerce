import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend } from 'k6/metrics';

import { BASE_URL, envNumber, extractQueryParam, jsonHeaders, parseCsv } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';
import { checkout, buildLoadtestWebhook, postLoadtestWebhook } from '../lib/payment.js';
import { chooseProduct, fetchProducts } from '../lib/products.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const fallbackProducts = new SharedArray('products', () => JSON.parse(open('../data/products.json')));
const checkoutLatency = new Trend('checkout_latency');

export const options = {
  scenarios: {
    checkout: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.CHECKOUT_RAMP_UP || '30s', target: envNumber('CHECKOUT_VUS', 10) },
        { duration: __ENV.CHECKOUT_DURATION || '2m', target: envNumber('CHECKOUT_VUS', 10) },
        { duration: __ENV.CHECKOUT_RAMP_DOWN || '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.03'],
    checkout_latency: ['p(95)<2000', 'p(99)<4000'],
  },
};

export function setup() {
  const requestedVus = envNumber('CHECKOUT_VUS', 10);
  if (requestedVus > users.length) {
    fail(`CHECKOUT_VUS=${requestedVus} exceeds users.csv count=${users.length}.`);
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
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const product = chooseProduct(data.products, fallbackProducts);
  const params = jsonHeaders(token);

  http.post(
    `${BASE_URL}/api/v1/cart/items`,
    JSON.stringify({ productId: product.id, quantity: 1, discountId: null }),
    params
  );

  const started = Date.now();
  const response = checkout(token);
  checkoutLatency.add(Date.now() - started);
  const ok = check(response, {
    'checkout status is 200': (res) => res.status === 200,
    'checkout returned order id': (res) => Boolean(res.json('orderId')),
    'checkout returned url': (res) => Boolean(res.json('checkoutUrl')),
  });
  if (!ok) {
    sleep(envNumber('CHECKOUT_SLEEP_SECONDS', 1));
    return;
  }

  if ((__ENV.COMPLETE_PAYMENT || 'true').toLowerCase() === 'true') {
    const checkoutUrl = response.json('checkoutUrl');
    const sessionId = extractQueryParam(checkoutUrl, 'sessionId');
    const paymentId = extractQueryParam(checkoutUrl, 'paymentId');
    const payload = buildLoadtestWebhook('checkout.session.completed', sessionId, paymentId, `${__VU}_${__ITER}`);
    postLoadtestWebhook(payload);
  }

  const orderId = response.json('orderId');
  const status = http.get(`${BASE_URL}/api/v1/checkout/${orderId}`, params);
  check(status, {
    'checkout status poll is 200': (res) => res.status === 200,
  });
  sleep(envNumber('CHECKOUT_SLEEP_SECONDS', 1));
}
