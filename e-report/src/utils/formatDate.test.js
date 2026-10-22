import test from 'node:test';
import assert from 'node:assert/strict';

import {
    DISPLAY_TIME_ZONE,
    formatDate,
    formatDateTime,
    formatDateTimeInput,
    parseApiDateTime,
    parseDateTimeInput,
} from './formatDate.js';

test('WEB 时间展示固定转换为 UTC+8，并将无时区 API 时间按 UTC 解析', () => {
    assert.equal(DISPLAY_TIME_ZONE, 'Asia/Shanghai');
    assert.equal(formatDateTime('2026-09-16T09:21:50.145113'), '2026/09/16 17:21');
    assert.equal(formatDate('2026-09-16T09:21:50.145113', 'zh-CN', { second: '2-digit' }), '2026/09/16 17:21:50');
    assert.equal(formatDateTime('2026-09-16T09:21:50Z'), '2026/09/16 17:21');
});

test('WEB 日期输入按 UTC+8 墙上时间与 API UTC LocalDateTime 双向转换', () => {
    const apiValue = '2026-09-16T09:21:50';
    assert.equal(formatDateTimeInput(apiValue), '2026-09-16T17:21');
    assert.equal(parseDateTimeInput('2026-09-16T17:21:50'), apiValue);
});

test('WEB 无效时间不抛异常并保留可识别的空态', () => {
    assert.equal(parseApiDateTime('not-a-date'), null);
    assert.equal(formatDateTime('not-a-date'), '-');
    assert.equal(formatDateTimeInput('not-a-date'), '');
    assert.equal(parseDateTimeInput('2026-02-30T10:00'), null);
});
