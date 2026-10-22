import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const script = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'validate-npm-audit.mjs');

test('接受版本、父依赖和期限均受约束的 npm 漏洞例外', () => {
  const fixture = createFixture();
  const result = runValidator(fixture);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /NPM_AUDIT_ACCEPTED/);
});

test('拒绝未审查 advisory', () => {
  const fixture = createFixture({ advisoryId: 'GHSA-unknown-new-risk' });
  const result = runValidator(fixture);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /has no reviewed exception/);
});

test('拒绝过期例外和依赖父级变化', () => {
  const fixture = createFixture({ ignoreUntil: '2020-01-01', parentName: 'runtime-parser' });
  const result = runValidator(fixture);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /expired/);
  assert.match(result.stderr, /unexpected dependency parent/);
});

function createFixture({
  advisoryId = 'GHSA-reviewed-build-only',
  ignoreUntil = '2099-12-31',
  parentName = 'metro',
} = {}) {
  const root = mkdtempSync(join(tmpdir(), 'e-report-npm-audit-test-'));
  const projectRoot = join(root, 'app');
  mkdirSync(projectRoot);
  const auditPath = join(root, 'audit.json');
  const policyPath = join(root, 'policy.json');
  writeJson(join(projectRoot, 'package-lock.json'), {
    lockfileVersion: 3,
    packages: {
      '': { name: 'app', dependencies: { [parentName]: '1.0.0' } },
      [`node_modules/${parentName}`]: {
        version: '1.0.0',
        dependencies: { 'image-size': '1.2.1' },
      },
      'node_modules/image-size': { version: '1.2.1' },
    },
  });
  writeJson(auditPath, {
    vulnerabilities: {
      'image-size': {
        severity: 'high',
        via: [{
          source: 1,
          name: 'image-size',
          severity: 'high',
          url: `https://github.com/advisories/${advisoryId}`,
        }],
      },
      [parentName]: { severity: 'high', via: ['image-size'] },
    },
  });
  writeJson(policyPath, {
    schemaVersion: 1,
    projects: {
      app: {
        exceptions: [{
          id: 'GHSA-reviewed-build-only',
          package: 'image-size',
          versions: ['1.2.1'],
          allowedParents: ['metro'],
          ignoreUntil,
          reason: 'test-reviewed build-only exception',
        }],
      },
    },
  });
  return { projectRoot, auditPath, policyPath };
}

function runValidator(fixture) {
  return spawnSync(process.execPath, [
    script,
    '--projectName', 'app',
    '--projectRoot', fixture.projectRoot,
    '--auditJson', fixture.auditPath,
    '--policy', fixture.policyPath,
  ], { encoding: 'utf8' });
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}
