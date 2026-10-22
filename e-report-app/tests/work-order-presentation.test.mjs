import assert from 'node:assert/strict';
import test from 'node:test';

import {
  durationBucketPresentation,
  priorityPresentation,
  reportTypePresentation,
  slaRiskPresentation,
  statusDisplayLabel,
  statusPresentation,
} from '../src/constants/work-order-presentation.ts';
import { formatSlaSummary } from '../src/utils/work-order-sla.ts';

test('Android 使用共享工单颜色契约', () => {
  assert.equal(reportTypePresentation('NETWORK_FAULT').foreground, '#2563EB');
  assert.equal(reportTypePresentation('BOX_DOOR_OPEN').foreground, '#0F766E');
  assert.equal(reportTypePresentation('DAMAGED_BOX_REPLACEMENT').foreground, '#4338CA');
  assert.equal(priorityPresentation('URGENT').rail, '#DC2626');
  assert.equal(priorityPresentation('ROUTINE').label, '常规');
  assert.equal(slaRiskPresentation('BREACHED').foreground, '#6D28D9');
  assert.equal(durationBucketPresentation('OVER_72_HOURS').foreground, '#6D28D9');
});

test('Android 待接收和调单中状态统一显示已派单', () => {
  assert.equal(statusPresentation({ statusCode: 'SUBMITTED', phaseCode: 'PENDING_ACCEPTANCE' }).label, '已派单');
  assert.equal(statusPresentation({ statusCode: 'IN_PROGRESS', phaseCode: 'REASSIGNING' }).label, '已派单');
  assert.equal(statusDisplayLabel({ statusCode: 'SUBMITTED', statusLabel: '已提交', phaseCode: 'PENDING_ACCEPTANCE' }), '已派单');
  assert.equal(statusDisplayLabel({ statusCode: 'CUSTOM', statusLabel: '待外部确认' }), '待外部确认');
});

test('Android SLA 摘要只表达一次风险并隐藏已结束时限', () => {
  assert.equal(
    formatSlaSummary({ slaBreached: true, slaBreachSeconds: 3 * 3600 + 7 * 60 }),
    '已超时 3小时7分钟',
  );
  assert.equal(
    formatSlaSummary({ slaRiskLevel: 'DUE_SOON', slaRemainingSeconds: 65 * 60 }),
    '临期 · 剩余 1小时5分钟',
  );
  assert.equal(formatSlaSummary({ slaStage: 'COMPLETED', statusCode: 'CLOSED' }), null);
});
