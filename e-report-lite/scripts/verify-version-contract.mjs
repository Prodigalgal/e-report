import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { calculateAndroidVersionCode, verifyApkVersionContract } from '../../e-report-app/scripts/verify-version-contract.mjs';

const options = Object.fromEntries(Array.from({ length: (process.argv.length - 2) / 2 }, (_, index) => process.argv.slice(2 + index * 2, 4 + index * 2)));
const root = path.resolve(options['--app-root'] || path.join(path.dirname(fileURLToPath(import.meta.url)), '..'));
try {
  const native = JSON.parse(readFileSync(path.join(root, 'native-release.json'), 'utf8'));
  const contract = { versionName: native.version, versionCode: native.versionCode, runtimeVersion: native.runtimeVersion };
  if (calculateAndroidVersionCode(native.version) !== native.versionCode
      || native.runtimeVersion !== `android-lite-${native.version}-native${native.versionCode}` || native.applicationId !== 'com.mnnu.ereport.lite') {
    throw new Error('Lite 基础包版本契约不一致。');
  }
  for (const [option, actual] of [['--expected-version-code', String(native.versionCode)], ['--expected-version-name', native.version], ['--expected-runtime-version', native.runtimeVersion]]) {
    if (options[option] !== undefined && options[option] !== actual) throw new Error(`发布参数 ${option} 与 Lite 源码不一致。`);
  }
  const apk = options['--apk'] ? verifyApkVersionContract({ apkPath: path.resolve(options['--apk']), contract,
    androidSdkRoot: options['--android-sdk'], expectedPackageName: native.applicationId }) : undefined;
  console.log(`VERSION_CONTRACT=${JSON.stringify({ ...contract, apk })}`);
} catch (error) { console.error(error.message); process.exitCode = 1; }
