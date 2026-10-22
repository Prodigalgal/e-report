import test from 'node:test';
import assert from 'node:assert/strict';

import { buildWorkOrderTimeline } from './workOrderTimeline.js';

test('处理历史按时间串行，并用回单明细替代重复生命周期事件', () => {
    const timeline = buildWorkOrderTimeline([
        { id: 'submitted', eventType: 'SUBMITTED', eventTypeLabel: '已提交', occurredAt: '2026-08-21T08:00:00Z' },
        { id: 'duplicate-response', eventType: 'RESPONSE_SUBMITTED', eventTypeLabel: '已回单', occurredAt: '2026-08-21T09:00:00Z' },
    ], [{
        id: 'response-1',
        attemptNo: 1,
        submittedAt: '2026-08-21T09:00:00Z',
        submittedBy: { name: '处理员' },
        resolutionDetails: '完成处理',
        verifiedAt: '2026-08-21T10:00:00Z',
        verifiedBy: { name: '验收员' },
        verificationDecision: 'APPROVED',
        verificationDecisionLabel: '验收通过',
    }]);

    assert.deepEqual(timeline.map(item => item.title), ['已提交', '第 1 次回单', '第 1 次验收 · 验收通过']);
    assert.equal(timeline[1].detail, '完成处理');
});
