import http from 'k6/http';
import { check, sleep } from 'k6';

import { BASE_URL, envNumber, jsonHeaders } from '../lib/config.js';

export const options = {
  scenarios: {
    abuse: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.ABUSE_RAMP_UP || '20s', target: envNumber('ABUSE_VUS', 10) },
        { duration: __ENV.ABUSE_DURATION || '1m', target: envNumber('ABUSE_VUS', 10) },
        { duration: __ENV.ABUSE_RAMP_DOWN || '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:abuse}': ['p(95)<1000'],
  },
};

export default function () {
  const badToken = http.get(`${BASE_URL}/api/v1/cart`, {
    headers: { Authorization: 'Bearer invalid-token' },
    responseCallback: http.expectedStatuses(401),
  });
  check(badToken, { 'bad token rejected': (res) => res.status === 401 });

  const badJson = http.post(`${BASE_URL}/api/v1/cart/items`, '{not-json', {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer invalid-token' },
    responseCallback: http.expectedStatuses(401, 400),
  });
  check(badJson, { 'bad json rejected': (res) => [400, 401].includes(res.status) });

  const badPage = http.get(`${BASE_URL}/api/v1/products?size=10000`, {
    responseCallback: http.expectedStatuses(400),
  });
  check(badPage, { 'bad page size rejected': (res) => res.status === 400 });

  const missingProduct = http.get(`${BASE_URL}/api/v1/products/999999999`, {
    responseCallback: http.expectedStatuses(404),
  });
  check(missingProduct, { 'missing product rejected': (res) => res.status === 404 });

  const failedLogin = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: 'missing@example.com', password: 'bad-password' }),
    {
      ...jsonHeaders(),
      responseCallback: http.expectedStatuses(401),
    }
  );
  check(failedLogin, { 'failed login rejected': (res) => res.status === 401 });

  sleep(envNumber('ABUSE_SLEEP_SECONDS', 0.5));
}
