import encoding from 'k6/encoding';
import http from 'k6/http';
import { check, fail, sleep } from 'k6';

import { BASE_URL, envNumber, jsonHeaders } from '../lib/config.js';
import { login } from '../lib/auth.js';

const tinyPng = encoding.b64decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=',
  's'
);

const adminUser = {
  email: __ENV.ADMIN_EMAIL || 'admin@ecommerce.com',
  password: __ENV.ADMIN_PASSWORD || 'password',
};

export const options = {
  scenarios: {
    upload: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.UPLOAD_RAMP_UP || '20s', target: envNumber('UPLOAD_VUS', 5) },
        { duration: __ENV.UPLOAD_DURATION || '1m', target: envNumber('UPLOAD_VUS', 5) },
        { duration: __ENV.UPLOAD_RAMP_DOWN || '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{scenario:upload}': ['p(95)<2000'],
  },
};

export function setup() {
  const token = login(adminUser);
  if (!token) {
    fail('Admin login failed. Set ADMIN_EMAIL and ADMIN_PASSWORD for upload tests.');
  }
  return { token };
}

export default function (data) {
  const valid = http.post(
    `${BASE_URL}/api/v1/images/upload`,
    { file: http.file(tinyPng, `k6-${__VU}-${__ITER}.png`, 'image/png') },
    { headers: { Authorization: `Bearer ${data.token}` } }
  );
  check(valid, { 'valid upload ok': (res) => res.status === 200 });

  if (__ITER % 5 === 0) {
    const invalid = http.post(
      `${BASE_URL}/api/v1/images/upload`,
      { file: http.file('not-a-real-image', `bad-${__VU}-${__ITER}.png`, 'image/png') },
      {
        headers: { Authorization: `Bearer ${data.token}` },
        responseCallback: http.expectedStatuses(400),
      }
    );
    check(invalid, { 'invalid upload rejected': (res) => res.status === 400 });
  }

  sleep(envNumber('UPLOAD_SLEEP_SECONDS', 1));
}
