import { check, fail, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';
import http from 'k6/http';

import { BASE_URL, envNumber, extractQueryParam, jsonHeaders, parseCsv } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';
import { checkout, buildLoadtestWebhook, postLoadtestWebhook, signLoadtestPayload } from '../lib/payment.js';
import { chooseProduct, fetchProducts } from '../lib/products.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const fallbackProducts = new SharedArray('products', () => JSON.parse(open('../data/products.json')));
const webhookErrorRate = new Rate('webhook_error_rate');

export const options = {
  scenarios: {
    webhook_replay: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.WEBHOOK_RAMP_UP || '20s', target: envNumber('WEBHOOK_VUS', 10) },
        { duration: __ENV.WEBHOOK_DURATION || '2m', target: envNumber('WEBHOOK_VUS', 10) },
        { duration: __ENV.WEBHOOK_RAMP_DOWN || '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    webhook_error_rate: ['rate<0.05'],
  },
};

export function setup() {
  const requestedVus = envNumber('WEBHOOK_VUS', 10);
  if (requestedVus > users.length) {
    fail(`WEBHOOK_VUS=${requestedVus} exceeds users.csv count=${users.length}.`);
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
  const checkoutResponse = checkout(token);
  if (checkoutResponse.status !== 200) {
    webhookErrorRate.add(true);
    return;
  }

  const checkoutUrl = checkoutResponse.json('checkoutUrl');
  const sessionId = extractQueryParam(checkoutUrl, 'sessionId');
  const paymentId = extractQueryParam(checkoutUrl, 'paymentId');
  const payload = buildLoadtestWebhook('checkout.session.completed', sessionId, paymentId, `${__VU}_${__ITER}`);

  const first = postLoadtestWebhook(payload);
  const duplicate = postLoadtestWebhook(payload);
  webhookErrorRate.add(first.status !== 200 || duplicate.status !== 200);

  const badPayload = JSON.stringify({ eventId: `bad_${__VU}_${__ITER}`, eventType: 'checkout.session.completed' });
  const badSignatureResponse = http.post(`${BASE_URL}/api/v1/payments/webhook/loadtest`, badPayload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Signature': `${signLoadtestPayload(badPayload)}bad`,
    },
    responseCallback: http.expectedStatuses(400),
  });
  check(badSignatureResponse, {
    'bad webhook signature rejected': (res) => res.status === 400,
  });

  sleep(envNumber('WEBHOOK_SLEEP_SECONDS', 0.5));
}
