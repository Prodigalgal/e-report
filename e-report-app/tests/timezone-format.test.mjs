import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DISPLAY_TIME_ZONE,
  dateForUtc8Picker,
  formatDateTime,
  formatDateTimeInput,
  parseApiDateTime,
  parseDateTimeInput,
  pickerDateToApiDateTime,
} from '../src/utils/format.ts';

test('APP 时间展示固定转换为 UTC+8，并将无时区 API 时间按 UTC 解析', () => {
  assert.equal(DISPLAY_TIME_ZONE, 'Asia/Shanghai');
  assert.equal(formatDateTime('2026-09-16T09:21:50.145113'), '2026-09-16 17:21');
  assert.equal(formatDateTime('2026-09-16T09:21:50.145113Z'), '2026-09-16 17:21');
  assert.equal(parseApiDateTime('not-a-date'), null);
  assert.equal(parseDateTimeInput('2026-02-30T10:00'), null);
});

test('APP 原生日期选择器使用 UTC+8 墙上时间并回写 UTC LocalDateTime', () => {
  const apiValue = '2026-09-16T09:21:50';
  assert.equal(formatDateTimeInput(apiValue), '2026-09-16T17:21');
  assert.equal(parseDateTimeInput('2026-09-16T17:21:50'), apiValue);

  const pickerValue = dateForUtc8Picker(apiValue);
  assert.equal(pickerValue.getHours(), 17);
  assert.equal(pickerValue.getMinutes(), 21);
  assert.equal(pickerDateToApiDateTime(pickerValue), apiValue);
});
