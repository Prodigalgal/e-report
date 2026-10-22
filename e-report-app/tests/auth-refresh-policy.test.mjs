import assert from 'node:assert/strict';
import test from 'node:test';

import { shouldRefreshAfterUnauthorized } from '../src/auth/auth-refresh-policy.ts';

test('Access Token 过期后认证用户接口均可通过 Refresh Token 恢复', () => {
  assert.equal(shouldRefreshAfterUnauthorized('/auth/me', true), true);
  assert.equal(shouldRefreshAfterUnauthorized('/auth/logout', true), true);
  assert.equal(shouldRefreshAfterUnauthorized('/work-orders/search?page=0', true), true);
});

test('匿名登录端点和无凭据请求不触发刷新', () => {
  assert.equal(shouldRefreshAfterUnauthorized('/auth/login', true), false);
  assert.equal(shouldRefreshAfterUnauthorized('/auth/challenge', true), false);
  assert.equal(shouldRefreshAfterUnauthorized('/auth/refresh', true), false);
  assert.equal(shouldRefreshAfterUnauthorized('/auth/me', false), false);
});
