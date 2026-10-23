import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readNativeApkContract } from './verify-version-contract.mjs';

const appRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourceSha = process.env.SOURCE_SHA || execFileSync('git', ['rev-parse', 'HEAD'], { cwd: appRoot, encoding: 'utf8' }).trim();
if (!/^[a-f0-9]{40}$/.test(sourceSha)) throw new Error('APK 内置发布信息需要完整源码 SHA。');
const { version, runtimeVersion } = readNativeApkContract(appRoot);
const destination = path.join(appRoot, 'android/app/src/main/assets/e-report-release.json');
mkdirSync(path.dirname(destination), { recursive: true });
writeFileSync(destination, `${JSON.stringify({ version, runtimeVersion, sourceSha }, null, 2)}\n`);
console.log(`EMBEDDED_RELEASE_READY version=${version} source=${sourceSha}`);
