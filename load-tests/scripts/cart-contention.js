import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate } from 'k6/metrics';

import { BASE_URL, envNumber, jsonHeaders } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';

const conflictRate = new Rate('cart_conflict_rate');

const sharedUser = {
  email: __ENV.CONTENTION_EMAIL || 'loaduser001@example.com',
  password: __ENV.CONTENTION_PASSWORD || 'secret123',
  username: __ENV.CONTENTION_USERNAME || 'Load Contention User',
};

export const options = {
  scenarios: {
    cart_contention: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.CONTENTION_RAMP_UP || '20s', target: envNumber('CONTENTION_VUS', 25) },
        { duration: __ENV.CONTENTION_DURATION || '2m', target: envNumber('CONTENTION_VUS', 25) },
        { duration: __ENV.CONTENTION_RAMP_DOWN || '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.10'],
    cart_conflict_rate: ['rate<0.25'],
    'http_req_duration{scenario:cart_contention}': ['p(95)<1500'],
  },
};

export function setup() {
  ensureUser(sharedUser);
  const token = login(sharedUser);
  const productId = Number(__ENV.CONTENTION_PRODUCT_ID || 1);
  if (!productId) {
    fail('CONTENTION_PRODUCT_ID must be a valid product id.');
  }
  return { token, productId };
}

export default function (data) {
  const params = {
    ...jsonHeaders(data.token),
    responseCallback: http.expectedStatuses(200, 404, 409),
  };
  const add = http.post(
    `${BASE_URL}/api/v1/cart/items`,
    JSON.stringify({ productId: data.productId, quantity: 1, discountId: null }),
    params
  );
  conflictRate.add(add.status === 409);
  check(add, {
    'cart add is ok or conflict': (res) => res.status === 200 || res.status === 409,
  });

  const quantity = (__ITER % 5) + 1;
  const update = http.patch(
    `${BASE_URL}/api/v1/cart/items/${data.productId}`,
    JSON.stringify({ quantity }),
    params
  );
  conflictRate.add(update.status === 409);
  check(update, {
    'cart update is ok, missing, or conflict': (res) => [200, 404, 409].includes(res.status),
  });

  sleep(envNumber('CONTENTION_SLEEP_SECONDS', 0.1));
}
