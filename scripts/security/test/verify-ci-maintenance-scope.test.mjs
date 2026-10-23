import assert from 'node:assert/strict';
import test from 'node:test';
import { verifyCiMaintenanceScope } from '../verify-ci-maintenance-scope.mjs';

const request = {
  eventName: 'push',
  message: 'ci-only: fix release recovery',
  beforeSha: 'a'.repeat(40),
  headSha: 'b'.repeat(40),
  changedFiles: ['.github/workflows/release-gitops.yml', 'e-report-app/scripts/publish-app-release.ps1'],
};

test('accepts explicitly scoped CI maintenance without allocating business artifacts', () => {
  assert.equal(verifyCiMaintenanceScope(request), true);
  assert.equal(verifyCiMaintenanceScope({ ...request, changedFiles: [
    'scripts/release/wait-for-test-deployment.mjs', 'scripts/release/wait-for-test-deployment.test.mjs',
  ] }), true);
});

test('keeps ordinary pushes and pull requests on the complete candidate validation path', () => {
  assert.equal(verifyCiMaintenanceScope({ ...request, message: 'fix: change behavior' }), false);
  assert.equal(verifyCiMaintenanceScope({ ...request, eventName: 'pull_request' }), false);
});

test('rejects runtime, dependency, version, database and arbitrary tooling changes', () => {
  for (const file of [
    'e-report/src/App.tsx', 'shared/ui-action-colors.json', 'e-report-app/app.json',
    'e-report-backen/build.gradle', 'e-report/package-lock.json',
    'e-report-backen/src/main/resources/db/changelog/075.xml',
    'scripts/verify-version-contract.mjs', '.github/workflows/../runtime.yml',
    'scripts/release/arbitrary-runtime-change.mjs',
  ]) {
    assert.throws(() => verifyCiMaintenanceScope({ ...request, changedFiles: [...request.changedFiles, file] }),
      /requiring a new business candidate/);
  }
});

test('rejects absent changes and untrusted or missing revision inputs', () => {
  assert.throws(() => verifyCiMaintenanceScope({ ...request, changedFiles: [] }), /contain a change/);
  for (const beforeSha of ['', '0'.repeat(40), 'main; echo unsafe', '-HEAD']) {
    assert.throws(() => verifyCiMaintenanceScope({ ...request, beforeSha }), /immutable commit SHAs/);
  }
});
