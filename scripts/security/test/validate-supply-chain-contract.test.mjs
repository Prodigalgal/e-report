import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import {
  validateDeploymentManifestText,
  validateFrontendDockerContext,
  validateNpmProject,
  validateWorkflowText,
} from '../validate-supply-chain-contract.mjs';

test('accepts npm packages locked to the official registry with integrity', () => {
  const root = createNpmFixture({
    resolved: 'https://registry.npmjs.org/example/-/example-1.0.0.tgz',
    integrity: `sha512-${Buffer.from('verified').toString('base64')}`,
  });

  assert.deepEqual(validateNpmProject(root, 'app'), []);
});

test('requires the frontend Docker build to use the repository root context', () => {
  const accepted = validateFrontendDockerContext(
    'COPY e-report/package*.json ./\nCOPY e-report/ .\nCOPY shared/ /shared/\nCOPY e-report/nginx.conf /etc/nginx/conf.d/default.conf\n',
    '!e-report/**\n!shared/**\ne-report/node_modules\n',
    'cd "$WORKDIR"\ndocker build -f e-report/Dockerfile .\n',
  );
  assert.deepEqual(accepted, []);

  const rejected = validateFrontendDockerContext(
    'COPY package*.json ./\nCOPY . .\n',
    'node_modules\n',
    'cd "$WORKDIR/e-report"\ndocker build .\n',
  );
  assert.equal(rejected.length, 9);
});

test('rejects npm registry substitution and a missing integrity value', () => {
  const root = createNpmFixture({ resolved: 'https://packages.invalid/example.tgz' });
  const errors = validateNpmProject(root, 'app');

  assert.ok(errors.some((error) => error.includes('outside the official npm registry')));
  assert.ok(errors.some((error) => error.includes('missing a SHA-512')));
});

test('rejects GitHub Actions pinned only by a mutable tag', () => {
  const errors = validateWorkflowText('workflow.yml', 'steps:\n  - uses: actions/checkout@v4\n');

  assert.equal(errors.length, 1);
  assert.match(errors[0], /40-character commit SHA/);
});

test('rejects mutable external images and accepts digest or versioned local images', () => {
  const digest = 'a'.repeat(64);
  const source = [
    'image: nginx:latest',
    `image: nginx:1.30@sha256:${digest}`,
    'image: localhost/e-report-backend:20260812-v1.0.44-security-1',
  ].join('\n');

  const errors = validateDeploymentManifestText('deployment.yaml', source, false);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /nginx:latest/);
});

function createNpmFixture(descriptor) {
  const root = mkdtempSync(join(tmpdir(), 'e-report-supply-chain-contract-'));
  const project = join(root, 'app');
  mkdirSync(project);
  writeFileSync(join(project, '.npmrc'), [
    'registry=https://registry.npmjs.org/',
    'replace-registry-host=always',
    'ignore-scripts=true',
    'package-lock=true',
  ].join('\n'), 'utf8');
  writeFileSync(join(project, 'package-lock.json'), JSON.stringify({
    lockfileVersion: 3,
    packages: {
      '': { name: 'app' },
      'node_modules/example': { version: '1.0.0', ...descriptor },
    },
  }), 'utf8');
  return root;
}
