import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readNativeApkContract, verifyApkVersionContract } from '../../e-report-app/scripts/verify-version-contract.mjs';

const CERTIFICATE = 'c63061845c0ca95444e9e9507338cc25284f2b5b022f6662bf85045371cb2d27';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const json = file => JSON.parse(readFileSync(file, 'utf8'));
const options = Object.fromEntries(Array.from({ length: (process.argv.length - 3) / 2 }, (_, index) => process.argv.slice(3 + index * 2, 5 + index * 2)));
const edition = options['--edition'];
if (!['NATIVE', 'LITE'].includes(edition)) throw new Error('需要指定 NATIVE 或 LITE。');
const appRoot = path.join(root, edition === 'NATIVE' ? 'e-report-app' : 'e-report-lite');
const native = edition === 'NATIVE' ? readNativeApkContract(appRoot) : json(path.join(appRoot, 'native-release.json'));
const fingerprint = json(path.join(appRoot, 'native-fingerprint.json')).hash;
const tag = `android-${edition.toLowerCase()}-v${native.version}`;
const destination = path.resolve(options['--output-dir'] || 'output/native-artifact');
const sourceSha = options['--source-sha'];
if (!/^[a-f0-9]{40}$/.test(sourceSha)) throw new Error('需要完整源码 SHA。');
const businessVersion = json(path.join(root, 'e-report/package.json')).version;
mkdirSync(destination, { recursive: true });
const apk = path.join(destination, 'app-release.apk');
const baseManifest = path.join(destination, 'native-manifest.json');
function gh(args) { return execFileSync('gh', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }); }
function assertManifest(manifest) {
  if (edition === 'NATIVE' && manifest.sourceSha !== sourceSha) {
    throw new Error('Native APK 必须内置当前源码的完整业务功能，禁止跨源码复用。');
  }
  for (const [name, expected] of Object.entries({ edition, version: native.version, versionCode: native.versionCode,
    runtimeVersion: native.runtimeVersion, applicationId: native.applicationId, nativeFingerprint: fingerprint, certificateSha256: CERTIFICATE })) {
    if (manifest[name] !== expected) throw new Error(`不可变安装包 ${name} 与当前发布契约不一致。`);
  }
  if (!/^[a-f0-9]{40}$/.test(manifest.sourceSha) || manifest.size !== statSync(apk).size
      || manifest.sha256 !== createHash('sha256').update(readFileSync(apk)).digest('hex')) throw new Error('基础包清单、源码或 APK 摘要不一致。');
  const metadata = verifyApkVersionContract({ apkPath: apk, androidSdkRoot: process.env.ANDROID_HOME,
    contract: { versionName: native.version, versionCode: native.versionCode, runtimeVersion: native.runtimeVersion },
    expectedPackageName: native.applicationId, expectedSourceSha: sourceSha });
  if (metadata.certificateSha256 !== CERTIFICATE) throw new Error('基础包签名与已发布应用不一致。');
}
function envelope(manifest) {
  writeFileSync(path.join(destination, 'release-manifest.json'), `${JSON.stringify({ ...manifest,
    artifactSourceSha: manifest.sourceSha, sourceSha, businessVersion }, null, 2)}\n`);
}
if (process.argv[2] === 'prepare') {
  let release;
  try { release = JSON.parse(gh(['release', 'view', tag, '--json', 'tagName,isDraft'])); }
  catch (error) {
    if (!/release not found|HTTP 404/i.test(error.stderr?.toString() || '')) throw error;
  }
  if (release) {
    if (release.isDraft) throw new Error('安装包 Release 尚未完成，请检查该构建的制品上传。');
    gh(['release', 'download', tag, '--pattern', 'app-release.apk', '--pattern', 'native-manifest.json', '--dir', destination]);
    const manifest = json(baseManifest); assertManifest(manifest); envelope(manifest);
    console.log(`NATIVE_ARTIFACT_REUSED edition=${edition} tag=${tag} sha256=${manifest.sha256}`);
  }
  if (process.env.GITHUB_OUTPUT) writeFileSync(process.env.GITHUB_OUTPUT, `reuse=${Boolean(release)}\n`, { flag: 'a' });
} else if (process.argv[2] === 'seal') {
  copyFileSync(path.resolve(options['--apk']), apk);
  const manifest = { edition, version: native.version, versionCode: native.versionCode, runtimeVersion: native.runtimeVersion,
    applicationId: native.applicationId, nativeFingerprint: fingerprint, sourceSha,
    size: statSync(apk).size, sha256: createHash('sha256').update(readFileSync(apk)).digest('hex'), certificateSha256: CERTIFICATE };
  assertManifest(manifest); writeFileSync(baseManifest, `${JSON.stringify(manifest, null, 2)}\n`);
  gh(['release', 'create', tag, apk, baseManifest, '--target', sourceSha, '--prerelease', '--latest=false', '--title',
    `${edition === 'NATIVE' ? 'Native 完整安装包' : 'Lite 安装包'} ${native.version}`, '--notes', `Immutable Android APK. Native fingerprint: ${fingerprint}. Source: ${sourceSha}.`]);
  envelope(manifest);
  console.log(`NATIVE_ARTIFACT_SEALED edition=${edition} tag=${tag} sha256=${manifest.sha256}`);
} else throw new Error('支持 prepare 或 seal。');
