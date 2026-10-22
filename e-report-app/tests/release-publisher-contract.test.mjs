import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const script = readFileSync(new URL('../scripts/publish-app-release.ps1', import.meta.url), 'utf8')
  + readFileSync(new URL('../scripts/release-session.psm1', import.meta.url), 'utf8');

test('发布工具在挑战与登录间绑定同一 WEB 环境摘要', () => {
  assert.match(script, /function New-ReleasePublisherWebEnvironment/);
  assert.match(script, /webFingerprintHash\s*=\s*\$webEnvironment\.fingerprintHash/);
  assert.match(script, /webSecuritySignals\s*=\s*\$webEnvironment\.securitySignals/);
  assert.match(script, /webEnvironment\s*=\s*\$webEnvironment/);
  assert.match(script, /fingerprintSource\s*=\s*'e-report-release-publisher:web-env-v1'/);
});

test('发布工具兼容服务端带等号和问号的算术验证码', () => {
  assert.match(script, /captchaQuestion -notmatch '[^']*\(\?:=\\s\*\\\?\)\?[^']*'/);
});

test('发布工具只通过系统发布中心同步对象存储', () => {
  assert.doesNotMatch(script, /CloudflareEnvFile|R2ApiTokenKey|New-R2Client|Send-R2ApkWithRetry/);
  assert.match(script, /admin\/app-updates\/r2/);
  assert.match(script, /releaseItem\.published/);
  assert.match(script, /releaseItem\.status\s+-eq\s+'SYNCED'/);
});
