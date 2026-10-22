import assert from 'node:assert/strict';
import test from 'node:test';

import { normalizeAppReleasePolicy } from './appReleasePolicy.js';

test('发布策略归一化布尔值、最低构建号和说明', () => {
    assert.deepEqual(normalizeAppReleasePolicy({
        forceUpdate: true,
        minSupportedVersionCode: '37',
        releaseNotes: '  更新说明  ',
    }, 40), {
        forceUpdate: true,
        minSupportedVersionCode: 37,
        releaseNotes: '更新说明',
    });
});

test('发布策略拒绝负数、小数、越界构建号和超长说明', () => {
    assert.throws(() => normalizeAppReleasePolicy({ minSupportedVersionCode: '-1' }, 40), /大于或等于0/);
    assert.throws(() => normalizeAppReleasePolicy({ minSupportedVersionCode: '1.5' }, 40), /整数/);
    assert.throws(() => normalizeAppReleasePolicy({ minSupportedVersionCode: '41' }, 40), /不能高于/);
    assert.throws(() => normalizeAppReleasePolicy({ minSupportedVersionCode: '40', releaseNotes: 'a'.repeat(2001) }, 40), /2000/);
});
