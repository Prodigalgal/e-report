import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const versionCode = 5006001;
const versionName = '6.6.1';
const runtimeVersion = `android-${versionName}-native${versionCode}`;

test('reuses an identical published APK without upload, registration or policy changes', { timeout: 30_000 }, async (t) => {
  const fixture = await createPublisherFixture(t, { existing: true });
  const result = await fixture.publish();
  assert.equal(result.code, 0, result.output);
  assert.match(result.output, /RELEASE_ARTIFACT_REUSED/);
  assert.equal(fixture.uploadCount, 0);
  assert.equal(fixture.registrationCount, 0);
  assert.equal(fixture.syncCount, 0);
  assert.equal(fixture.logoutCount, 1);
  assert.deepEqual(fixture.errors, []);
});

test('rejects a different APK using the same versionCode before any upload or sync', { timeout: 30_000 }, async (t) => {
  const fixture = await createPublisherFixture(t, { existing: true, conflictingHash: true });
  const result = await fixture.publish();
  assert.notEqual(result.code, 0);
  assert.match(result.output, /禁止覆盖或复用版本号/);
  assert.equal(fixture.uploadCount, 0);
  assert.equal(fixture.registrationCount, 0);
  assert.equal(fixture.syncCount, 0);
  assert.equal(fixture.logoutCount, 1);
  assert.deepEqual(fixture.errors, []);
});

test('uploads every chunk with the correct range, registers metadata and confirms synchronization', { timeout: 30_000 }, async (t) => {
  const fixture = await createPublisherFixture(t);
  const result = await fixture.publish();
  assert.equal(result.code, 0, result.output);
  assert.equal(fixture.uploadCount, 1);
  assert.equal(fixture.registrationCount, 1);
  assert.equal(fixture.chunkCount, 2);
  assert.equal(fixture.syncCount, 0);
  assert.deepEqual(fixture.errors, []);
});

test('recovers a failed R2 sync using the existing release without reupload or duplicate registration', { timeout: 45_000 }, async (t) => {
  const fixture = await createPublisherFixture(t, { failFirstSync: true });
  const first = await fixture.publish();
  assert.notEqual(first.code, 0);
  assert.match(first.output, /对象存储同步失败/);
  const second = await fixture.publish();
  assert.equal(second.code, 0, second.output);
  assert.match(second.output, /RELEASE_SYNC_RESUMED/);
  assert.equal(fixture.uploadCount, 1);
  assert.equal(fixture.registrationCount, 1);
  assert.equal(fixture.syncCount, 1);
  assert.equal(fixture.logoutCount, 2);
  assert.deepEqual(fixture.errors, []);
});

async function createPublisherFixture(t, options = {}) {
  const fixtureRoot = await mkdtemp(join(tmpdir(), 'e-report-publisher-'));
  t.after(() => rm(fixtureRoot, { recursive: true, force: true }));
  const appRoot = join(fixtureRoot, 'candidate-app');
  await mkdir(join(appRoot, 'scripts'), { recursive: true });
  // The transport test isolates APK parsing; the real verifier remains mandatory in the publisher.
  await writeFile(join(appRoot, 'scripts/verify-version-contract.mjs'), `
    import assert from 'node:assert/strict';
    const args = process.argv.slice(2);
    assert.equal(args[args.indexOf('--app-root') + 1], process.env.PUBLISHER_APP_ROOT);
    assert.equal(args[args.indexOf('--expected-version-code') + 1], '${versionCode}');
    assert.equal(args[args.indexOf('--expected-version-name') + 1], '${versionName}');
    assert.equal(args[args.indexOf('--expected-runtime-version') + 1], '${runtimeVersion}');
  `);
  const apk = Buffer.alloc(2 * 1024 * 1024 + 17, 0x65);
  const sha256 = createHash('sha256').update(apk).digest('hex');
  const apkPath = join(fixtureRoot, 'fixture.apk');
  await writeFile(apkPath, apk);
  const expectedRelease = {
    id: '00000000-0000-7000-8000-000000000001', edition: 'NATIVE', platform: 'ANDROID', channel: 'stable',
    versionCode, versionName, runtimeVersion, fileSize: apk.length, sha256,
    minSupportedVersionCode: 4008003, forceUpdate: false,
  };
  let registered = options.existing ? { ...expectedRelease } : null;
  if (options.conflictingHash) {
    registered.sha256 = '0'.repeat(64);
  }
  let syncStatus = options.existing ? 'SYNCED' : null;
  const chunks = [];
  const fixture = { uploadCount: 0, registrationCount: 0, syncCount: 0, chunkCount: 0, logoutCount: 0, errors: [] };
  const server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url, 'http://localhost');
      const buffers = [];
      for await (const buffer of request) buffers.push(buffer);
      const body = Buffer.concat(buffers);
      const json = () => JSON.parse(body.toString('utf8'));
      const send = (payload) => {
        response.setHeader('Content-Type', 'application/json');
        response.end(JSON.stringify(payload));
      };
      if (url.pathname === '/api/auth/challenge') {
        return send({ challengeId: 'fixture', powPrefix: 'fixture', powDifficulty: 0, captchaRequired: false });
      }
      if (url.pathname === '/api/auth/login') {
        assert.equal(json().username, 'release-fixture');
        response.setHeader('Set-Cookie', 'fixture-session=active; Path=/api; HttpOnly');
        return send({ passwordChangeRequired: false });
      }
      assert.match(request.headers.cookie ?? '', /fixture-session=active/);
      if (url.pathname === '/api/auth/csrf') return send({ token: 'fixture-csrf', headerName: 'X-XSRF-TOKEN' });
      assert.equal(request.headers['x-xsrf-token'], 'fixture-csrf');
      if (url.pathname === '/api/auth/logout') {
        fixture.logoutCount += 1;
        return send({});
      }
      if (url.pathname === '/api/admin/app-updates' && request.method === 'GET') {
        assert.equal(url.searchParams.get('edition'), 'NATIVE');
        assert.equal(url.searchParams.get('platform'), 'ANDROID');
        assert.equal(url.searchParams.get('sort'), 'versionCode,desc');
        assert.equal(url.searchParams.get('channel'), 'stable');
        return send({ content: registered ? [registered] : [], last: true });
      }
      if (url.pathname === '/api/file-transfers/uploads' && request.method === 'POST') {
        assert.equal(json().sha256, sha256);
        assert.equal(json().totalSize, apk.length);
        fixture.uploadCount += 1;
        return send({ uploadId: 'fixture-upload', chunkSize: 2 * 1024 * 1024 });
      }
      const chunk = /\/file-transfers\/uploads\/fixture-upload\/chunks\/(\d+)$/.exec(url.pathname);
      if (chunk && request.method === 'PUT') {
        assert.equal(Number(chunk[1]), chunks.length);
        const start = chunks.reduce((sum, buffer) => sum + buffer.length, 0);
        assert.equal(request.headers['content-range'], `bytes ${start}-${start + body.length - 1}/${apk.length}`);
        chunks.push(body);
        fixture.chunkCount += 1;
        return send({ receivedBytes: start + body.length });
      }
      if (url.pathname === '/api/file-transfers/uploads/fixture-upload/complete') {
        assert.deepEqual(Buffer.concat(chunks), apk);
        return send({ uploadId: 'fixture-upload' });
      }
      if (url.pathname === '/api/admin/app-updates' && request.method === 'POST') {
        assert.equal(json().completedUploadId, 'fixture-upload');
        assert.equal(json().minSupportedVersionCode, 4008003);
        assert.equal(json().forceUpdate, true);
        assert.equal(json().runtimeVersion, runtimeVersion);
        fixture.registrationCount += 1;
        registered = { ...expectedRelease };
        syncStatus = options.failFirstSync ? 'SYNC_FAILED' : 'SYNCED';
        return send(registered);
      }
      if (url.pathname === `/api/admin/app-updates/r2/releases/${expectedRelease.id}` && request.method === 'POST') {
        fixture.syncCount += 1;
        syncStatus = 'SYNCED';
        return send({ status: syncStatus });
      }
      if (url.pathname === '/api/admin/app-updates/r2') {
        return send({ items: [{ releaseId: registered?.id, status: syncStatus, published: syncStatus === 'SYNCED' }] });
      }
      throw new Error(`Unexpected request: ${request.method} ${url.pathname}`);
    } catch (error) {
      fixture.errors.push(error.message);
      response.statusCode = 500;
      response.end(JSON.stringify({ error: error.message }));
    }
  });
  await new Promise((resolveListen) => server.listen(0, '127.0.0.1', resolveListen));
  t.after(() => new Promise((resolveClose) => server.close(resolveClose)));
  const apiBaseUrl = `http://127.0.0.1:${server.address().port}/api`;
  fixture.publish = () => new Promise((resolveExit, reject) => {
    const child = spawn(process.env.PWSH_PATH ?? 'pwsh', ['-NoProfile', '-NonInteractive', '-Command', `
      $ErrorActionPreference = 'Stop'
      $publisherArguments = @{
        Username = 'release-fixture'
        Password = ConvertTo-SecureString 'test-password' -AsPlainText -Force
        BaseUrl = $env:PUBLISHER_BASE_URL
        TransferBaseUrl = $env:PUBLISHER_BASE_URL
        AppRoot = $env:PUBLISHER_APP_ROOT
        ApkPath = $env:PUBLISHER_APK
        VersionCode = ${versionCode}
        VersionName = '${versionName}'
        RuntimeVersion = '${runtimeVersion}'
        MinSupportedVersionCode = 4008003
        ForceUpdate = $true
        ReleaseNotes = 'CI transport fixture'
      }
      & $env:PUBLISHER_SCRIPT @publisherArguments
    `], {
      cwd: repoRoot,
      timeout: 25_000,
      env: { ...process.env, PUBLISHER_SCRIPT: join(repoRoot, 'e-report-app/scripts/publish-app-release.ps1'),
        PUBLISHER_BASE_URL: apiBaseUrl, PUBLISHER_APP_ROOT: appRoot, PUBLISHER_APK: apkPath },
    });
    let output = '';
    child.stdout.on('data', (buffer) => { output += buffer; });
    child.stderr.on('data', (buffer) => { output += buffer; });
    child.on('error', reject);
    child.on('close', (code) => resolveExit({ code, output }));
  });
  return fixture;
}
