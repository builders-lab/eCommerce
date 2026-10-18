import http from 'k6/http';
import { check, fail } from 'k6';
import { BASE_URL, CREATE_USERS, jsonHeaders } from './config.js';

export function ensureUser(user) {
  if (!CREATE_USERS) {
    return;
  }

  const response = http.post(
    `${BASE_URL}/auth/signup`,
    JSON.stringify({
      email: user.email,
      password: user.password,
      username: user.username || user.email.split('@')[0],
    }),
    {
      ...jsonHeaders(),
      responseCallback: http.expectedStatuses(200, 409),
    }
  );

  check(response, {
    'signup created or already exists': (res) => res.status === 200 || res.status === 409,
  });
}

export function login(user) {
  const response = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    jsonHeaders()
  );

  const ok = check(response, {
    'login status is 200': (res) => res.status === 200,
    'login returned token': (res) => Boolean(res.json('token')),
  });

  if (!ok) {
    fail(`Login failed for ${user.email}. Set CREATE_USERS=true or pre-seed load-test users.`);
  }

  return response.json('token');
}
