import assert from 'node:assert/strict';
import test from 'node:test';

import { isAdminUser } from './adminAccess.js';

test('任一真实管理动作权限即可进入对应管理区域', () => {
    assert.equal(isAdminUser(null), false);
    assert.equal(isAdminUser({ permissions: [] }), false);
    assert.equal(isAdminUser({ permissions: ['user:manage:view'] }), true);
    assert.equal(isAdminUser({ permissions: ['work-order:network-fault:view'] }), true);
    assert.equal(isAdminUser({ permissions: ['account:self:view'] }), false);
});
