import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { verify, X509Certificate } from 'node:crypto';
import { unzipSync } from 'fflate';
import { createOtaArchive } from '../scripts/pack-ota-update.mjs';
import { describeOtaExport } from '../scripts/ota-export-manifest.mjs';

function fixture(t) {
  const appRoot = mkdtempSync(path.join(tmpdir(), 'e-report-ota-test-'));
  t.after(() => rmSync(appRoot, { recursive: true, force: true }));
  for (const directory of ['android/app', 'ota', 'export/assets']) mkdirSync(path.join(appRoot, directory), { recursive: true });
  const certificate = path.join(appRoot, 'ota/certificate.crt');
  const privateKeyFile = path.join(appRoot, 'private-key.pem');
  const configuration = path.join(appRoot, 'openssl.cnf');
  writeFileSync(configuration, '[req]\ndistinguished_name=dn\nprompt=no\n[dn]\nCN=e-report-test\n');
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-keyout', privateKeyFile, '-out', certificate,
    '-nodes', '-config', configuration, '-days', '2'], { stdio: 'ignore' });
  const native = { version: '6.8.0', versionCode: 5008000, runtimeVersion: 'android-6.8.0-native5008000', applicationId: 'com.mnnu.ereport' };
  const config = { version: '6.8.1', runtimeVersion: native.runtimeVersion, android: { versionCode: 5008001, package: native.applicationId } };
  const json = (file, body) => writeFileSync(path.join(appRoot, file), JSON.stringify(body));
  json('native-runtime.json', native); json('app.json', { expo: config });
  json('package.json', { version: '6.8.1' }); json('package-lock.json', { version: '6.8.1', packages: { '': { version: '6.8.1' } } });
  writeFileSync(path.join(appRoot, 'android/app/build.gradle'), 'versionCode 5008001\nversionName "6.8.1"\n');
  const exportRoot = path.join(appRoot, 'export');
  json('export/expoConfig.json', config);
  json('export/metadata.json', { fileMetadata: { android: { bundle: 'app.hbc', assets: [{ path: 'assets/image', ext: 'png' }] } } });
  writeFileSync(path.join(exportRoot, 'app.hbc'), 'Hermes runtime-compatible bytecode fixture');
  writeFileSync(path.join(exportRoot, 'assets/image'), new Uint8Array([137, 80, 78, 71]));
  return { appRoot, exportRoot, privateKey: readFileSync(privateKeyFile, 'utf8'), environment: 'test', apiBaseUrl: 'https://test.example/api',
    sourceSha: 'a'.repeat(40), createdAt: '2026-10-21T00:00:00Z', certificate: new X509Certificate(readFileSync(certificate)) };
}

test('same immutable export produces a reproducible signed OTA for its native runtime', t => {
  const options = fixture(t);
  const first = createOtaArchive(options); const second = createOtaArchive(options);
  assert.deepEqual(first.archive, second.archive);
  const files = unzipSync(first.archive);
  assert.ok(verify('RSA-SHA256', files['manifest.json'], options.certificate.publicKey, Buffer.from(first.signature, 'base64')));
  const manifest = JSON.parse(Buffer.from(files['manifest.json']).toString('utf8'));
  assert.equal(manifest.runtimeVersion, 'android-6.8.0-native5008000'); assert.equal(manifest.metadata.version, '6.8.1');
  assert.match(manifest.launchAsset.url, /^https:\/\/test.example\/api\/app-updates\/ota\/assets\//);
  const production = createOtaArchive({ ...options, environment: 'production', apiBaseUrl: 'https://production.example/api' });
  assert.notEqual(production.descriptor.id, first.descriptor.id);
  const productionFiles = unzipSync(production.archive);
  const resourceNames = Object.keys(files).filter(name => name.startsWith('assets/'));
  for (const name of resourceNames) assert.deepEqual(files[name], productionFiles[name]);
});

test('rejects wrong signing key, source identity and unsafe resource path', t => {
  const options = fixture(t);
  assert.throws(() => createOtaArchive({ ...options, sourceSha: 'short' }), /完整源码 SHA/);
  assert.throws(() => createOtaArchive({ ...options, apiBaseUrl: 'http://test.example/api' }), /HTTPS/);
  writeFileSync(path.join(options.exportRoot, 'metadata.json'), JSON.stringify({ fileMetadata: { android: { bundle: '../package.json', assets: [] } } }));
  assert.throws(() => createOtaArchive(options), /路径越界/);
});

test('rollback directive remains signed and bound to environment and runtime', t => {
  const options = fixture(t);
  const result = createOtaArchive({ ...options, kind: 'ROLLBACK' });
  const files = unzipSync(result.archive);
  assert.equal(Object.keys(files).length, 3);
  const directive = JSON.parse(Buffer.from(files['manifest.json']).toString());
  assert.equal(directive.type, 'rollBackToEmbedded'); assert.equal(directive.parameters.metadata.environment, 'test');
  assert.equal(directive.parameters.metadata.runtimeVersion, 'android-6.8.0-native5008000');
  const described = describeOtaExport(options.exportRoot, options.appRoot, options.sourceSha);
  writeFileSync(path.join(options.exportRoot, 'app.hbc'), 'changed');
  assert.notEqual(describeOtaExport(options.exportRoot, options.appRoot, options.sourceSha).files.find(file => file.path === 'app.hbc').sha256,
    described.files.find(file => file.path === 'app.hbc').sha256);
});
