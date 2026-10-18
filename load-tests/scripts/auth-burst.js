import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

import { BASE_URL, envNumber, jsonHeaders, parseCsv, randomItem, validateVuCapacity } from '../lib/config.js';
import { ensureUser } from '../lib/auth.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const loginLatency = new Trend('login_latency');

export const options = {
  scenarios: {
    login_burst: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.AUTH_RAMP_UP || '30s', target: envNumber('AUTH_VUS', 25) },
        { duration: __ENV.AUTH_DURATION || '2m', target: envNumber('AUTH_VUS', 25) },
        { duration: __ENV.AUTH_RAMP_DOWN || '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    login_latency: ['p(95)<1200', 'p(99)<2500'],
  },
};

export function setup() {
  validateVuCapacity('AUTH_VUS', envNumber('AUTH_VUS', 25), users);
  users.forEach((user) => ensureUser(user));
}

export default function () {
  const user = randomItem(users);
  const started = Date.now();
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    jsonHeaders()
  );
  loginLatency.add(Date.now() - started);
  check(response, {
    'login status is 200': (res) => res.status === 200,
    'login returned token': (res) => Boolean(res.json('token')),
  });
  sleep(envNumber('AUTH_SLEEP_SECONDS', 0.25));
}
