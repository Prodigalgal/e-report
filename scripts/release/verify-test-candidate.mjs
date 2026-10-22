import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { verifyOtaEndpoint } from './verify-ota-endpoint.mjs';
import { verifyPublishedApk } from './verify-client-distribution.mjs';

export function validateCandidateApk(published, contract, checksum, edition) {
  if (!/^[a-f0-9]{64}$/.test(checksum || '') || published.edition !== edition || published.versionCode !== contract.versionCode
      || published.versionName !== contract.version || published.runtimeVersion !== contract.runtimeVersion || published.sha256 !== checksum) {
    throw new Error(`测试环境 ${edition} APK 尚未发布或与候选不一致。`);
  }
}

async function main() {
  const origin = 'https://e-report-test.fantong.eu.org:60443';
  const api = `${origin}/api`;
  const sourceSha = process.env.SOURCE_SHA;
  if (!/^[a-f0-9]{40}$/.test(sourceSha || '')) throw new Error('缺少完整候选源码 SHA。');
  const json = async url => {
    const response = await fetch(url, { headers: { 'Cache-Control': 'no-cache' }, signal: AbortSignal.timeout(30000) });
    if (!response.ok) throw new Error(`测试环境验收检查失败 ${response.status}`);
    return response.json();
  };
  const release = await json(`${origin}/release.json?source=${sourceSha}`);
  if (release.sourceSha !== sourceSha) throw new Error('测试环境并非待提升的候选源码。');
  await json(`${api}/auth/csrf`);
  for (const [edition, directory, checksum] of [
    ['NATIVE', 'e-report-app', process.env.ANDROID_SHA256], ['LITE', 'e-report-lite', process.env.LITE_SHA256],
  ]) {
    const contract = JSON.parse(readFileSync(`${directory}/native-release.json`, 'utf8'));
    const published = await json(`${api}/app-updates/latest?edition=${edition}&platform=ANDROID&channel=stable`);
    validateCandidateApk(published, contract, checksum, edition);
    await verifyPublishedApk(published);
  }
  await verifyOtaEndpoint({ apiBaseUrl: api, expected: { kind: 'UPDATE', environment: 'test', sourceSha,
    runtimeVersion: JSON.parse(readFileSync('e-report-app/native-release.json', 'utf8')).runtimeVersion,
    version: JSON.parse(readFileSync('e-report/package.json', 'utf8')).version } });
  console.log(`TEST_CANDIDATE_VERIFIED source=${sourceSha}`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
