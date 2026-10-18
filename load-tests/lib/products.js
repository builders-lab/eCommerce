import http from 'k6/http';

import { BASE_URL, randomItem } from './config.js';

export function fetchProducts(maxPages = 5, pageSize = 100) {
  const products = [];
  for (let page = 0; page < maxPages; page++) {
    const response = http.get(`${BASE_URL}/api/v1/products?page=${page}&size=${pageSize}`);
    if (response.status !== 200) {
      break;
    }
    const items = response.json('items') || [];
    products.push(...items.map((item) => ({ id: item.id, category: item.category })));
    if (!response.json('hasNext')) {
      break;
    }
  }
  return products;
}

export function chooseProduct(products, fallbackProducts) {
  const source = products && products.length > 0 ? products : fallbackProducts;
  return randomItem(source);
}
