import assert from 'node:assert/strict';
import test from 'node:test';

import { parseBrowser, parseOperatingSystem, sha256Hex, windowsDisplayVersion } from './webEnvironment.js';

const chromeWindowsUa = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0.0.0 Safari/537.36';

test('WEB 环境将 Win32 解析为可读的 Windows 与浏览器信息', () => {
    assert.deepEqual(parseBrowser(chromeWindowsUa), { name: 'Google Chrome', version: '140.0.0.0' });
    assert.deepEqual(parseOperatingSystem(chromeWindowsUa), { name: 'Windows', version: '10/11' });
});

test('Client Hints 能区分 Windows 11 并优先使用完整浏览器版本', () => {
    const hints = { value: {
        platform: 'Windows',
        platformVersion: '15.0.0',
        fullVersionList: [{ brand: 'Microsoft Edge', version: '140.0.3485.54' }],
    } };
    assert.deepEqual(parseBrowser(chromeWindowsUa, hints), { name: 'Microsoft Edge', version: '140.0.3485.54' });
    assert.deepEqual(parseOperatingSystem(chromeWindowsUa, hints), { name: 'Windows', version: '11 (15.0.0)' });
    assert.equal(windowsDisplayVersion('10.0.0'), '10 (10.0.0)');
    assert.equal(windowsDisplayVersion('', 'Mozilla/5.0 (Windows NT 6.1; Win64; x64)'), '7');
});

test('环境摘要使用稳定 SHA-256 十六进制格式', async () => {
    assert.equal(await sha256Hex('e-report-web-env'), 'e897794099224b4e4975f1ea67abdd26342de3b6c3283d547ff75ede5c5fb248');
});
