import assert from 'node:assert/strict';
import test from 'node:test';

import { evaluatePassword, passwordsMatch } from '../src/auth/password-policy.ts';

const context = {
  username: 'dispatcher',
  name: '调度管理员',
  phone: '13959601234',
  email: 'dispatcher@139.com',
};

test('APP 密码策略接受符合默认组成要求的口令', () => {
  assert.equal(evaluatePassword('清风-月明-Aa1-工单', context).valid, true);
  assert.equal(evaluatePassword('correct-Horse-2026!', context).valid, true);
});

test('APP 密码策略支持关闭字符组成要求', () => {
  assert.equal(evaluatePassword('清风月明远山流水', {}, {
    minLength: 8,
    maxLength: 100,
    requireUppercase: false,
    requireLowercase: false,
    requireDigit: false,
    requireSpecial: false,
  }).valid, true);
});

test('APP 密码策略拒绝短口令、用户属性和可猜测序列', () => {
  assert.equal(evaluatePassword('Aa1!bc', context).valid, false);
  assert.equal(evaluatePassword('dispatcher-Safe-2026', context).valid, false);
  assert.equal(evaluatePassword('abcdefgh-2026-safe', context).valid, false);
  assert.equal(evaluatePassword('1111111111111111', context).valid, false);
});

test('APP 管理员重置密码要求两次输入完全一致', () => {
  assert.equal(passwordsMatch('Correct-Horse-Battery-2026', 'Correct-Horse-Battery-2026'), true);
  assert.equal(passwordsMatch('Correct-Horse-Battery-2026', 'Different-Horse-Battery-2026'), false);
  assert.equal(passwordsMatch('', ''), false);
});
