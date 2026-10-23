import assert from 'node:assert/strict';
import test from 'node:test';
import { planReleaseRecovery } from './recover-release.mjs';

const repository = 'Prodigalgal/e-report';
const run = {
  id: 123, repository: { full_name: repository }, head_repository: { full_name: repository },
  head_branch: 'main', path: '.github/workflows/release-gitops.yml', event: 'workflow_dispatch',
  status: 'completed', conclusion: 'failure', run_attempt: 1,
};
const job = (name, conclusion = 'failure') => ({ name, status: 'completed', conclusion });

test('retries APK publishing and lets test image pulls settle before test recovery', () => {
  assert.equal(planReleaseRecovery(run, [job('collect', 'success'), job('publish-android')], repository).delaySeconds, 60);
  assert.equal(planReleaseRecovery(run, [job('promote-test')], repository).delaySeconds, 900);
  assert.equal(planReleaseRecovery(run, [job('publish-test-android')], repository).retry, true);
  assert.equal(planReleaseRecovery(run, [job('collect', 'success'), job('verify-test')], repository).delaySeconds, 60);
  assert.equal(planReleaseRecovery(run, [job('publish-test-android / publish-apk (LITE)')], repository).retry, true);
  assert.equal(planReleaseRecovery(run, [job('publish-android / publish-ota')], repository).retry, true);
});

test('blocks production rollout, artifact contract, unknown and cancelled jobs', () => {
  for (const name of ['collect', 'promote-production', 'unknown-job']) {
    assert.equal(planReleaseRecovery(run, [job(name)], repository).retry, false);
  }
  assert.equal(planReleaseRecovery(run, [job('publish-android', 'cancelled')], repository).retry, false);
  assert.equal(planReleaseRecovery(run, [job('collect', 'success')], repository).retry, false);
});

test('respects cancellation, success, active runs and the three-attempt limit', () => {
  for (const overrides of [
    { conclusion: 'cancelled' }, { conclusion: 'success' }, { status: 'in_progress' },
    { run_attempt: 0 }, { run_attempt: 3 }, { run_attempt: undefined },
  ]) {
    assert.equal(planReleaseRecovery({ ...run, ...overrides }, [job('publish-android')], repository).retry, false);
  }
});

test('requires the same repository, main branch, exact workflow and allowed event', () => {
  for (const overrides of [
    { repository: { full_name: 'other/repo' } }, { head_repository: { full_name: 'fork/repo' } },
    { head_branch: 'topic' }, { path: '.github/workflows/other.yml' }, { event: 'pull_request' },
  ]) {
    assert.equal(planReleaseRecovery({ ...run, ...overrides }, [job('publish-android')], repository).retry, false);
  }
  assert.equal(planReleaseRecovery(run, [], repository).retry, false);
  assert.equal(planReleaseRecovery(run, [{ ...job('publish-android'), status: 'in_progress' }], repository).retry, false);
});
