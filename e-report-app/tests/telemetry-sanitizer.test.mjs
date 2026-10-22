import assert from 'node:assert/strict';
import test from 'node:test';

import { sanitizeTelemetryText, sanitizeTelemetryValue } from '../src/observability/telemetry-sanitizer.ts';

test('安卓诊断上下文会移除账号、坐标、网络和硬件标识', () => {
  const sanitized = sanitizeTelemetryValue({
    token: 'private-token',
    androidId: 'abcdef1234567890',
    bssid: 'AA:BB:CC:DD:EE:FF',
    ipAddress: '192.168.1.10',
    lastKnown: { latitude: 24.12, longitude: 117.65, accuracy: 18 },
  });

  assert.equal(sanitized.token, '[已脱敏]');
  assert.equal(sanitized.androidId, '[已脱敏]');
  assert.equal(sanitized.bssid, '[已脱敏]');
  assert.equal(sanitized.ipAddress, '[已脱敏]');
  assert.equal(sanitized.lastKnown.latitude, '[已脱敏]');
  assert.equal(sanitized.lastKnown.longitude, '[已脱敏]');
  assert.equal(sanitized.lastKnown.accuracy, 18);
});

test('安卓诊断文本会清理手机号、邮箱、MAC 和坐标', () => {
  const sanitized = sanitizeTelemetryText(
    '手机13912345678 邮箱user@example.com MAC=AA:BB:CC:DD:EE:FF longitude=117.65',
  );

  assert.doesNotMatch(sanitized, /13912345678|user@example\.com|AA:BB:CC:DD:EE:FF|117\.65/);
});
