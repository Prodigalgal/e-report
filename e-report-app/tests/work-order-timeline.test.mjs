import assert from 'node:assert/strict';
import test from 'node:test';

import { buildWorkOrderTimeline } from '../src/features/work-orders/work-order-timeline.ts';

test('APP 处理历史按时间串行并消除回单事件重复', () => {
  const timeline = buildWorkOrderTimeline([
    { id: 'submitted', eventType: 'SUBMITTED', eventTypeLabel: '已提交', occurredAt: '2026-08-21T08:00:00Z' },
    { id: 'duplicate', eventType: 'RESPONSE_SUBMITTED', eventTypeLabel: '已回单', occurredAt: '2026-08-21T09:00:00Z' },
  ], [{
    id: 'response-1',
    attemptNo: 1,
    resolutionDetails: '完成处理',
    resolutionMethod: '现场修复',
    submittedAt: '2026-08-21T09:00:00Z',
    submittedBy: { id: 'u1', username: 'handler', name: '处理员' },
    verifiedAt: '2026-08-21T10:00:00Z',
    verifiedBy: { id: 'u2', username: 'verifier', name: '验收员' },
    verificationDecision: 'APPROVED',
    verificationDecisionLabel: '验收通过',
  }]);

  assert.deepEqual(timeline.map((item) => item.title), ['已提交', '第 1 次回单', '第 1 次验收 · 验收通过']);
  assert.equal(timeline[1].meta, '处理方式：现场修复');
});
