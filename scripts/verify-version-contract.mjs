import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { calculateAndroidVersionCode, verifySourceVersionContract } from '../e-report-app/scripts/verify-version-contract.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');
const json = (relativePath) => JSON.parse(read(relativePath));
const fail = (message) => {
  throw new Error(`版本契约校验失败：${message}`);
};

const backendBuild = read('e-report-backen/build.gradle');
const backendVersion = backendBuild.match(/^version\s*=\s*['"]([^'"]+)['"]/m)?.[1];
if (!backendVersion) fail('无法读取 Backend version。');
const backendRuntimeVersion = read('e-report-backen/src/main/resources/application.properties')
  .match(/^spring\.application\.version=(.+)$/m)?.[1]?.trim();
const backendDockerVersion = read('e-report-backen/Dockerfile')
  .match(/^ARG APP_VERSION=(.+)$/m)?.[1]?.trim();

const webPackage = json('e-report/package.json');
const webLock = json('e-report/package-lock.json');
const appPackage = json('e-report-app/package.json');
const appLock = json('e-report-app/package-lock.json');
const appConfig = json('e-report-app/app.json').expo;

const versions = new Map([
  ['Backend', backendVersion],
  ['Backend runtime', backendRuntimeVersion],
  ['Backend Docker default', backendDockerVersion],
  ['WEB package', webPackage.version],
  ['WEB lock', webLock.version],
  ['WEB lock root', webLock.packages?.['']?.version],
  ['APP package', appPackage.version],
  ['APP lock', appLock.version],
  ['APP lock root', appLock.packages?.['']?.version],
  ['Expo', appConfig.version],
]);
for (const [name, version] of versions) {
  if (version !== backendVersion) fail(`${name}=${version ?? '缺失'}，预期 ${backendVersion}。`);
}

const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(backendVersion);
if (!match) fail(`版本 ${backendVersion} 不是正式三段式 SemVer。`);
const [, majorText, minorText, patchText] = match;
const [major, minor, patch] = [majorText, minorText, patchText].map(Number);
if (major < 1 || minor >= 1000 || patch >= 1000) fail('版本号超出 Android versionCode 公式约束。');
const native = verifySourceVersionContract({ appRoot: path.join(root, 'e-report-app') });
const lite = json('e-report-lite/native-release.json');
const liteCode = calculateAndroidVersionCode(lite.version);
if (lite.versionCode !== liteCode || lite.runtimeVersion !== `android-lite-${lite.version}-native${liteCode}`
    || lite.applicationId !== 'com.mnnu.ereport.lite') fail('Lite 原生基础包契约不一致。');
if (liteCode > calculateAndroidVersionCode(backendVersion)) fail('Lite 基础包版本不能高于业务版本。');
const liteBuild = read('e-report-lite/app/build.gradle.kts');
if (!liteBuild.includes('rootProject.file("native-release.json")')
    || !liteBuild.includes('nativeRelease["versionCode"]') || !liteBuild.includes('nativeRelease["version"]')) {
  fail('Lite Gradle 必须读取原生基础包契约。');
}
const androidRuntime = read('e-report-app/android/app/src/main/res/values/strings.xml');
if (!androidRuntime.includes(`>${native.runtimeVersion}</string>`)) fail('Android Manifest runtime 与原生基础包不一致。');

process.stdout.write(`版本契约通过：业务 ${backendVersion} / 原生 ${native.versionName} (${native.versionCode}) / Lite ${lite.version} (${liteCode})\n`);
