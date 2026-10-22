import assert from 'node:assert/strict';
import test from 'node:test';

import {
  resolveAuthReturnPath,
  resolveUnauthenticatedReturnPath,
} from '../src/navigation/auth-return-path.ts';

test('explicit logout return target always resolves to the home screen', () => {
  assert.equal(resolveAuthReturnPath('/'), '/');
  assert.equal(resolveAuthReturnPath(['/']), '/');
});

test('authentication return target accepts internal paths and rejects login or external targets', () => {
  assert.equal(resolveAuthReturnPath('/work-orders/123'), '/work-orders/123');
  assert.equal(resolveAuthReturnPath('/login?returnTo=/profile'), '/');
  assert.equal(resolveAuthReturnPath('https://example.com'), '/');
  assert.equal(resolveAuthReturnPath(undefined), '/');
});

test('explicit logout discards the previous account route while session expiry preserves it', () => {
  assert.equal(resolveUnauthenticatedReturnPath('/profile', true), '/');
  assert.equal(resolveUnauthenticatedReturnPath('/admin/users', true), '/');
  assert.equal(resolveUnauthenticatedReturnPath('/work-orders/123', false), '/work-orders/123');
});
