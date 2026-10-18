import { envNumber } from '../lib/config.js';
import { setupMixed, default as mixedDefault } from './mixed.js';

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-vus',
      vus: Number(__ENV.SOAK_VUS || __ENV.MIXED_VUS || 25),
      duration: __ENV.SOAK_DURATION || '30m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{scenario:soak}': ['p(95)<1200', 'p(99)<3000'],
  },
};

export function setup() {
  return setupMixed(envNumber('SOAK_VUS', envNumber('MIXED_VUS', 25)), 'SOAK_VUS');
}

export default function (data) {
  mixedDefault(data);
}
