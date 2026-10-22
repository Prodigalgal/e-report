import assert from 'node:assert/strict';
import test from 'node:test';

import { readWorkOrderSearchParams, writeWorkOrderSearchParams } from './workOrderSearchParams.js';

test('读取重复参数和旧单值参数为多选集合', () => {
    const params = new URLSearchParams('queue=CLAIMABLE&queue=REPORTED_BY_ME&type=NETWORK_FAULT&reportType=BOX_DOOR_OPEN&status=SUBMITTED&status=CLOSED&q=光交箱');

    assert.deepEqual(readWorkOrderSearchParams(params), {
        searchTerm: '光交箱',
        statuses: ['SUBMITTED', 'CLOSED'],
        reportTypes: ['NETWORK_FAULT', 'BOX_DOOR_OPEN'],
        queues: ['CLAIMABLE', 'REPORTED_BY_ME'],
    });
});

test('写入时为每个选项生成重复参数并忽略空集合', () => {
    const params = writeWorkOrderSearchParams({
        statuses: ['IN_PROGRESS', 'ON_HOLD'],
        reportTypes: ['NETWORK_FAULT'],
        queues: [],
        location: '芗城区',
    });

    assert.deepEqual(params.getAll('status'), ['IN_PROGRESS', 'ON_HOLD']);
    assert.deepEqual(params.getAll('type'), ['NETWORK_FAULT']);
    assert.deepEqual(params.getAll('queue'), []);
    assert.equal(params.get('location'), '芗城区');
});
