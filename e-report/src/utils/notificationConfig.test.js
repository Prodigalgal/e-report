import assert from 'node:assert/strict';
import test from 'node:test';

import {
    notificationSettingSourceLabel,
} from './notificationConfig.js';

test('notification setting sources are rendered consistently', () => {
    assert.equal(notificationSettingSourceLabel({ sourceScope: 'TENANT_OVERRIDE' }, '漳州市公司'), '漳州市公司默认');
    assert.equal(notificationSettingSourceLabel({
        sourceScope: 'ORGANIZATION_OVERRIDE',
        sourceOrgUnitName: '南靖县',
    }), '南靖县配置');
    assert.equal(notificationSettingSourceLabel({ sourceScope: 'UNCONFIGURED' }), '未配置');
    assert.equal(notificationSettingSourceLabel({ sourceScope: 'RUNTIME_SECRET' }), '系统默认发件池');
    assert.equal(notificationSettingSourceLabel({ sourceScope: 'MIXED' }), '本级与上级共同使用');
});
