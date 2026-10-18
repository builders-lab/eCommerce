import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { BASE_URL, envNumber, jsonHeaders, parseCsv, validateVuCapacity } from '../lib/config.js';
import { ensureUser, login } from '../lib/auth.js';
import { chooseProduct, fetchProducts } from '../lib/products.js';

const users = new SharedArray('users', () => parseCsv(open('../data/users.csv')));
const fallbackProducts = new SharedArray('products', () => JSON.parse(open('../data/products.json')));

export const options = {
  scenarios: {
    cart: {
      executor: 'ramping-vus',
      stages: [
        { duration: __ENV.CART_RAMP_UP || '30s', target: Number(__ENV.CART_VUS || 10) },
        { duration: __ENV.CART_DURATION || '2m', target: Number(__ENV.CART_VUS || 10) },
        { duration: __ENV.CART_RAMP_DOWN || '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:cart}': ['p(95)<800'],
  },
};

export function setup() {
  validateVuCapacity('CART_VUS', envNumber('CART_VUS', 10), users);

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

  const cart = http.get(`${BASE_URL}/api/v1/cart`, params);
  check(cart, {
    'cart read status is 200': (res) => res.status === 200,
  });

  const add = http.post(
    `${BASE_URL}/api/v1/cart/items`,
    JSON.stringify({ productId: product.id, quantity: 1, discountId: null }),
    params
  );
  check(add, {
    'cart add status is 200': (res) => res.status === 200,
  });

  const update = http.patch(
    `${BASE_URL}/api/v1/cart/items/${product.id}`,
    JSON.stringify({ quantity: 2 }),
    params
  );
  check(update, {
    'cart quantity update status is 200': (res) => res.status === 200,
  });

  const remove = http.del(`${BASE_URL}/api/v1/cart/items/${product.id}`, null, params);
  check(remove, {
    'cart remove status is 200': (res) => res.status === 200,
  });

  sleep(Number(__ENV.CART_SLEEP_SECONDS || 1));
}
