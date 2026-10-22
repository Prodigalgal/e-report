import test from 'node:test';
import assert from 'node:assert/strict';
import {
    normalizeWorkOrderIds,
    selectedIdsForExport,
    togglePageWorkOrderSelection,
    toggleWorkOrderSelection,
} from './workOrderSelection.js';

test('工单选择会去重并忽略空标识', () => {
    assert.deepEqual(normalizeWorkOrderIds(['a', ' a ', '', null, 'a']), ['a']);
    assert.deepEqual(selectedIdsForExport(new Set(['a', 'b'])), ['a', 'b']);
});

test('单条工单选择支持新增和取消', () => {
    const selected = toggleWorkOrderSelection(new Set(['a']), 'b');
    assert.deepEqual([...selected], ['a', 'b']);
    assert.deepEqual([...toggleWorkOrderSelection(selected, 'a')], ['b']);
});

test('当前页全选只影响当前页并支持取消', () => {
    const selected = togglePageWorkOrderSelection(new Set(['outside']), ['a', 'b', 'a']);
    assert.deepEqual([...selected], ['outside', 'a', 'b']);
    assert.deepEqual([...togglePageWorkOrderSelection(selected, ['a', 'b'])], ['outside']);
});
