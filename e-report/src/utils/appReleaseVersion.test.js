import test from 'node:test';
import assert from 'node:assert/strict';

import { deriveAndroidVersionMetadata } from './appReleaseVersion.js';

test('安装包版本号统一计算 Android 构建号和运行时版本', () => {
    assert.deepEqual(deriveAndroidVersionMetadata('2.4.0'), {
        versionName: '2.4.0',
        versionCode: 1004000,
        runtimeVersion: 'android-2.4.0-native1004000',
    });
});

test('安装包版本号拒绝非三段式、前导零和超出范围的输入', () => {
    assert.throws(() => deriveAndroidVersionMetadata('2.4'), /x\.y\.z/);
    assert.throws(() => deriveAndroidVersionMetadata('2.04.0'), /x\.y\.z/);
    assert.throws(() => deriveAndroidVersionMetadata('3000.0.0'), /可用范围/);
});
