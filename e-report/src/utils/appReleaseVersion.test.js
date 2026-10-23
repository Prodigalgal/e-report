import test from 'node:test';
import assert from 'node:assert/strict';

import { deriveAndroidVersionMetadata } from './appReleaseVersion.js';

test('完整 Native APK 可以沿用兼容运行时，Lite 保持独立身份', () => {
    assert.equal(deriveAndroidVersionMetadata('6.11.0', 'NATIVE', 'android-6.8.2-native5008002').runtimeVersion, 'android-6.8.2-native5008002');
    assert.equal(deriveAndroidVersionMetadata('6.11.0', 'LITE').runtimeVersion, 'android-lite-6.11.0-native5011000');
    assert.throws(() => deriveAndroidVersionMetadata('6.11.0', 'NATIVE', 'android-6.8.2-native5008003'), /构建号不一致/);
    assert.throws(() => deriveAndroidVersionMetadata('6.11.0', 'NATIVE', 'android-6.12.0-native5012000'), /高于安装包/);
});

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
