import { fail } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const CREATE_USERS = (__ENV.CREATE_USERS || 'false').toLowerCase() === 'true';
export const LOADTEST_WEBHOOK_SECRET = __ENV.LOADTEST_WEBHOOK_SECRET || 'loadtest-secret';

export function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers };
}

export function randomItem(items) {
  return items[Math.floor(Math.random() * items.length)];
}

export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function envNumber(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === null || raw === '') {
    return fallback;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function envBool(name, fallback = false) {
  const raw = __ENV[name];
  if (raw === undefined || raw === null || raw === '') {
    return fallback;
  }
  return String(raw).toLowerCase() === 'true';
}

export function validateVuCapacity(envName, requestedVus, users) {
  if (requestedVus > users.length) {
    fail(`${envName}=${requestedVus} exceeds users.csv count=${users.length}. Add users or lower ${envName}.`);
  }
}

export function pickWeighted(weightedItems) {
  const total = weightedItems.reduce((sum, item) => sum + item.weight, 0);
  let cursor = Math.random() * total;
  for (const item of weightedItems) {
    cursor -= item.weight;
    if (cursor <= 0) {
      return item.value;
    }
  }
  return weightedItems[weightedItems.length - 1].value;
}

export function extractQueryParam(url, name) {
  const marker = `${name}=`;
  const index = url.indexOf(marker);
  if (index < 0) {
    return null;
  }
  const start = index + marker.length;
  const end = url.indexOf('&', start);
  const encoded = end < 0 ? url.slice(start) : url.slice(start, end);
  return decodeURIComponent(encoded.replace(/\+/g, ' '));
}

export function parseCsv(text) {
  const lines = text.trim().split(/\r?\n/);
  const headers = lines.shift().split(',').map((value) => value.trim());

  return lines
    .filter((line) => line.trim().length > 0)
    .map((line) => {
      const values = line.split(',').map((value) => value.trim());
      return headers.reduce((row, header, index) => {
        row[header] = values[index] || '';
        return row;
      }, {});
    });
}
