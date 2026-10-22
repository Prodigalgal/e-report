import assert from 'node:assert/strict';
import test from 'node:test';

import {
    buildUserExportRequest,
    bindUserExportPreview,
    buildTransferRowFailureParams,
    exportFilterLabels,
    groupTransferRows,
    transferRowHasChanges,
} from './userTransferWorkflow.js';

test('导入预览按连续脱敏用户标识分组', () => {
    const groups = groupTransferRows([
        { id: '1', subjectKey: 'a', subjectDisplay: '135****0001' },
        { id: '2', subjectKey: 'a', subjectDisplay: '135****0001' },
        { id: '3', subjectKey: 'b', subjectDisplay: '135****0002' },
    ]);
    assert.equal(groups.length, 2);
    assert.equal(groups[0].rows.length, 2);
    assert.equal(groups[1].subjectDisplay, '135****0002');
});

test('导入预览只把真实字段或权限变化视为 Diff', () => {
    assert.equal(transferRowHasChanges({ fieldChanges: [], authorizationDiff: { changes: [] } }), false);
    assert.equal(transferRowHasChanges({ fieldChanges: [{ field: '姓名' }] }), true);
    assert.equal(transferRowHasChanges({ authorizationDiff: { changes: [{ kind: 'ADD' }] } }), true);
});

test('导出预检与执行共享同一筛选快照', () => {
    assert.deepEqual(buildUserExportRequest({
        searchTerm: '庄', orgUnitId: 'org-1', status: 'DELETED',
    }), {
        searchTerm: '庄', orgUnitId: 'org-1', status: 'DELETED', includeDeleted: true,
    });
    assert.deepEqual(exportFilterLabels({ organizationName: '南靖县', statusLabel: '启用' }), [
        '组织：南靖县', '状态：启用',
    ]);
    assert.deepEqual(bindUserExportPreview({ orgUnitId: 'org-1' }, { previewToken: 'signed-preview' }), {
        orgUnitId: 'org-1', previewToken: 'signed-preview',
    });
    assert.throws(() => bindUserExportPreview({}, {}), /重新预览/);
});

test('用户导出筛选包含所属部门', () => {
    assert.deepEqual(buildUserExportRequest({ orgUnitId: 'org-1', departmentId: 'dept-1' }), {
        orgUnitId: 'org-1',
        departmentId: 'dept-1',
        includeDeleted: false,
    });
});

test('导入任务分别筛选校验失败和执行失败', () => {
    assert.deepEqual(buildTransferRowFailureParams('VALIDATION_FAILED'), {
        validationStatus: 'INVALID', executionStatus: undefined,
    });
    assert.deepEqual(buildTransferRowFailureParams('EXECUTION_FAILED'), {
        validationStatus: undefined, executionStatus: 'FAILED',
    });
    assert.deepEqual(buildTransferRowFailureParams('ALL'), {
        validationStatus: undefined, executionStatus: undefined,
    });
});
