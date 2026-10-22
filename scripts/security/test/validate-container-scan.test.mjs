import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const script = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'validate-container-scan.mjs');
const digest = `sha256:${'a'.repeat(64)}`;

test('接受 digest、CVE 和补偿控制均匹配的容器例外', () => {
  const fixture = createFixture();
  const result = runValidator(fixture);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /CONTAINER_SCAN_ACCEPTED/);
});

test('拒绝未审查 CVE 和错误镜像 digest', () => {
  const fixture = createFixture({ vulnerabilityId: 'CVE-2099-0002', reportDigest: `sha256:${'b'.repeat(64)}` });
  const result = runValidator(fixture);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /does not match policy digest/);
});

test('拒绝过期例外与缺失补偿控制', () => {
  const fixture = createFixture({ ignoreUntil: '2020-01-01', controlText: 'control removed' });
  const result = runValidator(fixture);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /expired/);
  assert.match(result.stderr, /control marker missing/);
});

test('发布扫描只验证明确选中的镜像并拒绝未知名称', () => {
  const fixture = createFixture();
  const policy = JSON.parse(readFileSync(fixture.policyPath, 'utf8'));
  policy.images.legacy = { reference: `example.invalid/legacy:1@${digest}`, exceptions: [] };
  writeJson(fixture.policyPath, policy);

  const release = runValidator(fixture, 'runtime');
  assert.equal(release.status, 0, release.stderr);
  assert.match(release.stdout, /images=1/);

  const invalid = runValidator(fixture, 'runtime,unknown');
  assert.equal(invalid.status, 1);
  assert.match(invalid.stderr, /unknown or duplicate image/);
});

function createFixture({
  vulnerabilityId = 'CVE-2099-0001',
  reportDigest = digest,
  ignoreUntil = '2099-12-31',
  controlText = 'SECURITY_CONTROL_ENABLED=true',
} = {}) {
  const repoRoot = mkdtempSync(join(tmpdir(), 'e-report-container-scan-test-'));
  const reportDirectory = join(repoRoot, 'reports');
  mkdirSync(reportDirectory);
  writeFileSync(join(repoRoot, 'Dockerfile'), `${controlText}\n`, 'utf8');
  const policyPath = join(repoRoot, 'policy.json');
  writeJson(policyPath, {
    schemaVersion: 1,
    images: {
      runtime: {
        reference: `example.invalid/runtime:1@${digest}`,
        exceptions: [{
          targetContains: 'alpine',
          package: 'example-lib',
          installedVersion: '1.0.0-r0',
          ids: ['CVE-2099-0001', 'CVE-2099-0002'],
          ignoreUntil,
          reason: 'test-reviewed exception',
          controlFile: 'Dockerfile',
          controlContains: ['SECURITY_CONTROL_ENABLED=true'],
        }],
      },
    },
  });
  writeJson(join(reportDirectory, 'runtime.json'), {
    SchemaVersion: 2,
    ArtifactName: `example.invalid/runtime:1@${reportDigest}`,
    Results: [{
      Target: 'runtime (alpine 3.24)',
      Vulnerabilities: [{
        VulnerabilityID: vulnerabilityId,
        PkgName: 'example-lib',
        InstalledVersion: '1.0.0-r0',
      }],
    }],
  });
  return { repoRoot, reportDirectory, policyPath };
}

function runValidator(fixture, images) {
  const args = [
    script,
    '--repoRoot', fixture.repoRoot,
    '--reportDirectory', fixture.reportDirectory,
    '--policy', fixture.policyPath,
  ];
  if (images) args.push('--images', images);
  return spawnSync(process.execPath, args, { encoding: 'utf8' });
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}
