import http from 'k6/http';
import { check, fail } from 'k6';
import { SharedArray } from 'k6/data';

import { BASE_URL, parseCsv, randomItem } from '../lib/config.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const products = new SharedArray('products', () => JSON.parse(open('../data/products.json')));

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  if (users.length < 100) {
    fail(`users.csv has ${users.length} users. Use a larger fixture for high-VU runs.`);
  }
  check({ status: 200 }, {
    'users fixture loaded': () => users.length >= 100,
    'products fixture loaded': () => products.length > 0,
  });

  for (const product of products.slice(0, Math.min(products.length, 20))) {
    const response = http.get(`${BASE_URL}/api/v1/products/${product.id}`, {
      responseCallback: http.expectedStatuses(200, 404),
    });
    check(response, {
      [`product ${product.id} exists`]: (res) => res.status === 200,
    });
  }

  const list = http.get(`${BASE_URL}/api/v1/products?page=0&size=100`);
  check(list, {
    'product list is available': (res) => res.status === 200,
    'product list has items': (res) => (res.json('items') || []).length > 0,
  });
}
