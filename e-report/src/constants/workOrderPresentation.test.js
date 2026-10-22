import assert from 'node:assert/strict';
import test from 'node:test';

import workOrderCatalog from '../../../shared/work-order-catalog.json' with { type: 'json' };
import {
    WORK_ORDER_PRESENTATION,
    priorityPresentation,
    slaRiskPresentation,
    statusDisplayLabel,
    statusPresentation,
} from './workOrderPresentation.js';
import { slaSummaryLabel } from './workOrderSla.js';

test('共享展示契约完整覆盖工单类型和优先级', () => {
    assert.deepEqual(
        Object.keys(WORK_ORDER_PRESENTATION.reportTypes).sort(),
        workOrderCatalog.map(item => item.reportType).sort(),
    );
    assert.deepEqual(
        Object.keys(WORK_ORDER_PRESENTATION.priorities).sort(),
        ['URGENT', 'HIGH', 'ROUTINE', 'LOW'].sort(),
    );
});

test('紧急和超时使用相互独立的红色与紫色语义', () => {
    assert.equal(priorityPresentation('URGENT').rail, '#DC2626');
    assert.equal(slaRiskPresentation('BREACHED').foreground, '#6D28D9');
    assert.notEqual(priorityPresentation('URGENT').foreground, slaRiskPresentation('BREACHED').foreground);
    assert.equal(WORK_ORDER_PRESENTATION.durationBuckets.OVER_72_HOURS.foreground, '#6D28D9');
});

test('流程状态不占用紧急红色或超时紫色', () => {
    const reservedColors = new Set(['#B91C1C', '#DC2626', '#6D28D9']);
    for (const token of Object.values(WORK_ORDER_PRESENTATION.statuses)) {
        assert.equal(reservedColors.has(token.foreground), false);
    }
    assert.equal(statusPresentation({ statusCode: 'SUBMITTED', phaseCode: 'PENDING_ACCEPTANCE' }).label, '已派单');
    assert.equal(statusPresentation({ statusCode: 'IN_PROGRESS', phaseCode: 'REASSIGNING' }).label, '已派单');
    assert.equal(statusDisplayLabel({ statusCode: 'SUBMITTED', statusLabel: '已提交', phaseCode: 'PENDING_ACCEPTANCE' }), '已派单');
    assert.equal(statusDisplayLabel({ statusCode: 'CUSTOM', statusLabel: '待外部确认' }), '待外部确认');
});

test('SLA 摘要只表达一次风险并隐藏已结束时限', () => {
    assert.equal(
        slaSummaryLabel({ slaBreached: true, slaBreachSeconds: 3 * 3600 + 7 * 60 }),
        '已超时 3小时7分钟',
    );
    assert.equal(
        slaSummaryLabel({ slaRiskLevel: 'DUE_SOON', slaRemainingSeconds: 65 * 60 }),
        '临期 · 剩余 1小时5分钟',
    );
    assert.equal(slaSummaryLabel({ slaStage: 'COMPLETED', statusCode: 'CLOSED' }), null);
});
