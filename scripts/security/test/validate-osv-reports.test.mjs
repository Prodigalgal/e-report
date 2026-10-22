import assert from 'node:assert/strict';
import test from 'node:test';
import {
  summarizeBuildToolingReport,
  validateOsvReports,
} from '../validate-osv-reports.mjs';

test('accepts an empty release graph and an exact unexpired build-tooling baseline', () => {
  const buildReport = createBuildReport();
  const expected = summarizeBuildToolingReport(buildReport);

  assert.deepEqual(validateOsvReports({ results: [] }, buildReport, {
    reviewUntil: '2099-12-31',
    reason: 'reviewed fixture',
    expected,
  }, new Date('2099-01-01T00:00:00Z')), []);
});

test('rejects any release dependency finding', () => {
  const buildReport = createBuildReport();
  const errors = validateOsvReports(createReleaseReport(), buildReport, {
    reviewUntil: '2099-12-31',
    reason: 'reviewed fixture',
    expected: summarizeBuildToolingReport(buildReport),
  }, new Date('2099-01-01T00:00:00Z'));

  assert.ok(errors.some((error) => error.includes('release dependency graph')));
});

test('rejects build-tooling advisory drift and an expired baseline', () => {
  const buildReport = createBuildReport();
  const expected = summarizeBuildToolingReport(buildReport);
  buildReport.results[0].packages[0].groups[0].ids.push('GHSA-new-risk');
  const errors = validateOsvReports({ results: [] }, buildReport, {
    reviewUntil: '2020-01-01',
    reason: 'reviewed fixture',
    expected,
  }, new Date('2026-08-12T00:00:00Z'));

  assert.ok(errors.some((error) => error.includes('expired')));
  assert.ok(errors.some((error) => error.includes('fingerprintSha256 changed')));
});

function createBuildReport() {
  return {
    results: [{
      source: { path: '/workspace/e-report-app/android/app/gradle.lockfile' },
      packages: [{
        package: { ecosystem: 'Maven', name: 'example:tool', version: '1.0.0' },
        groups: [{ ids: ['GHSA-reviewed'] }],
      }],
    }],
  };
}

function createReleaseReport() {
  return {
    results: [{
      source: { path: '/workspace/package-lock.json' },
      packages: [{
        package: { ecosystem: 'npm', name: 'runtime-risk', version: '1.0.0' },
        groups: [{ ids: ['GHSA-runtime-risk'] }],
      }],
    }],
  };
}
