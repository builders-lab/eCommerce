import crypto from 'k6/crypto';
import http from 'k6/http';
import { check } from 'k6';

import { BASE_URL, LOADTEST_WEBHOOK_SECRET, jsonHeaders } from './config.js';

export function signLoadtestPayload(payload) {
  return crypto.hmac('sha256', LOADTEST_WEBHOOK_SECRET, payload, 'hex');
}

export function buildLoadtestWebhook(eventType, providerReferenceId, paymentReferenceId, suffix = '') {
  return JSON.stringify({
    eventId: `lt_evt_${providerReferenceId}_${eventType}_${suffix || Date.now()}`,
    eventType,
    providerReferenceId,
    paymentReferenceId,
    message: `k6 ${eventType}`,
  });
}

export function postLoadtestWebhook(payload, expectedStatus = 200) {
  const signature = signLoadtestPayload(payload);
  const response = http.post(`${BASE_URL}/api/v1/payments/webhook/loadtest`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Signature': signature,
    },
  });
  check(response, {
    [`loadtest webhook status is ${expectedStatus}`]: (res) => res.status === expectedStatus,
  });
  return response;
}

export function checkout(token) {
  return http.post(`${BASE_URL}/api/v1/checkout`, null, jsonHeaders(token));
}
