import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers';

import {
    extractDeviceModelFromUserAgent,
    isExpectedApiStatus,
    recordApiTelemetry,
    sanitizeTelemetryText,
    sanitizeTelemetryValue,
    shouldSampleOperationSuccess,
} from './telemetry.js';

test('网页诊断上下文会移除敏感字段和硬件标识', () => {
    const sanitized = sanitizeTelemetryValue({
        password: 'secret',
        phone: '13912345678',
        androidId: 'abcdef1234567890',
        location: { latitude: 24.123, provider: 'gps' },
    });

    assert.equal(sanitized.password, '[已脱敏]');
    assert.equal(sanitized.phone, '[已脱敏]');
    assert.equal(sanitized.androidId, '[已脱敏]');
    assert.equal(sanitized.location.latitude, '[已脱敏]');
    assert.equal(sanitized.location.provider, 'gps');
});

test('网页诊断消息会清理令牌、手机号、邮箱、MAC 和坐标', () => {
    const sanitized = sanitizeTelemetryText(
        'Bearer private-token 手机13912345678 邮箱user@example.com MAC=AA:BB:CC:DD:EE:FF latitude=24.12',
    );

    assert.doesNotMatch(sanitized, /private-token|13912345678|user@example\.com|AA:BB:CC:DD:EE:FF|24\.12/);
});

test('安卓浏览器仅提取非唯一设备型号', () => {
    const model = extractDeviceModelFromUserAgent(
        'Mozilla/5.0 (Linux; Android 13; zh-CN; M2102J2SC Build/TKQ1) AppleWebKit/537.36',
    );

    assert.equal(model, 'M2102J2SC');
    assert.equal(extractDeviceModelFromUserAgent('Mozilla/5.0 (Windows NT 10.0; Win64; x64)'), '');
});

test('仅显式声明的业务状态码不会生成接口告警', () => {
    assert.equal(isExpectedApiStatus(401, [401]), true);
    assert.equal(isExpectedApiStatus('401', [401]), true);
    assert.equal(isExpectedApiStatus(403, [401]), false);
    assert.equal(isExpectedApiStatus(500, [401]), false);
    assert.equal(isExpectedApiStatus(401), false);
});

test('网页成功操作按百分比采样并限制异常配置', () => {
    assert.equal(shouldSampleOperationSuccess(20, 0.199), true);
    assert.equal(shouldSampleOperationSuccess(20, 0.2), false);
    assert.equal(shouldSampleOperationSuccess(-1, 0), false);
    assert.equal(shouldSampleOperationSuccess(100, 0.999), true);
});

test('取消查询不产生失败记录，真实网络错误、服务错误和超时仍被记录', async context => {
    const storage = new Map();
    const previousStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
    Object.defineProperty(globalThis, 'localStorage', {
        configurable: true,
        value: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    });
    context.mock.method(globalThis, 'fetch', async () => ({ ok: false, status: 503 }));
    const queueKey = 'e-report.telemetry.queue.v1';
    try {
        recordApiTelemetry({ method: 'GET', path: '/work-orders/search', status: 0,
            error: { name: 'CanceledError', code: 'ERR_CANCELED', message: 'canceled' } });
        assert.equal(storage.has(queueKey), false);

        recordApiTelemetry({ method: 'GET', path: '/work-orders/search', status: 0,
            error: new TypeError('Failed to fetch') });
        recordApiTelemetry({ method: 'GET', path: '/work-orders/search', status: 503,
            error: { name: 'AxiosError', code: 'ERR_BAD_RESPONSE', message: 'Service unavailable' } });
        recordApiTelemetry({ method: 'GET', path: '/work-orders/search', status: 0,
            error: { name: 'AxiosError', code: 'ECONNABORTED', message: 'Request timeout' } });
        await new Promise(resolve => setImmediate(resolve));
        const events = JSON.parse(storage.get(queueKey));
        assert.equal(events.length, 3);
        assert.ok(events.every(event => event.outcome === 'FAILURE'));
        assert.deepEqual(events.map(event => event.attributes['http.response.status_code']), [0, 503, 0]);
        assert.equal(events[0].attributes['exception.type'], 'TypeError');
        assert.equal(events[2].attributes['exception.message'], 'Request timeout');
    } finally {
        if (previousStorage) Object.defineProperty(globalThis, 'localStorage', previousStorage);
        else delete globalThis.localStorage;
    }
});
