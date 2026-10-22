import assert from 'node:assert/strict';
import test from 'node:test';

import {
  assertIsolatedE2eTarget,
  assertIsolationReady,
  assertProductionReadOnlyTarget,
  assertReadOnlyBusinessMethod,
} from '../lib/e2e-environment-guard.mjs';

test('写入型 E2E 必须显式提供地址且拒绝所有已登记生产主机', () => {
  assert.throws(() => assertIsolatedE2eTarget(''), /必须通过环境变量显式提供/);
  assert.throws(
    () => assertIsolatedE2eTarget('https://e-report.fantong.eu.org:60443/api'),
    /禁止连接生产主机/,
  );
  assert.throws(
    () => assertIsolatedE2eTarget('https://36.212.129.231/api'),
    /禁止连接生产主机/,
  );
  assert.equal(
    assertIsolatedE2eTarget('https://e-report-e2e.internal/api/'),
    'https://e-report-e2e.internal/api',
  );
});

test('隔离能力仅在全部副作用开关关闭时通过', () => {
  const ready = {
    ready: true,
    notificationDeliveryEnabled: false,
    notificationCenterEnabled: false,
    emailEnabled: false,
    weComEnabled: false,
    dingTalkEnabled: false,
    dailyOutstandingReportEnabled: false,
    outboxEnqueueEnabled: false,
    outboxDeliveryEnabled: false,
    dailyOutstandingReportSchedulerEnabled: false,
  };
  assert.doesNotThrow(() => assertIsolationReady(ready));
  assert.throws(
    () => assertIsolationReady({ ...ready, emailEnabled: true }),
    /emailEnabled/,
  );
  assert.throws(
    () => assertIsolationReady({ ...ready, ready: false }),
    /ready=false/,
  );
});

test('生产巡检只允许登记主机和只读业务方法', () => {
  assert.equal(
    assertProductionReadOnlyTarget('https://e-report.fantong.eu.org:60443/api/'),
    'https://e-report.fantong.eu.org:60443/api',
  );
  assert.throws(
    () => assertProductionReadOnlyTarget('https://example.com/api'),
    /只允许连接已登记生产主机/,
  );
  assert.equal(assertReadOnlyBusinessMethod('head'), 'HEAD');
  assert.throws(() => assertReadOnlyBusinessMethod('POST'), /禁止使用 POST/);
});
