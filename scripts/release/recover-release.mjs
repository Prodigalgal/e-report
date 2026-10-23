import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const RETRYABLE_JOBS = new Set(['promote-test', 'verify-test', 'publish-test-android', 'publish-android']);
const RETRYABLE_CLIENT_JOB = /^(?:publish-test-android|publish-android) \/ (?:publish-apk \((?:NATIVE|LITE)\)|publish-ota)$/;

export function planReleaseRecovery(run, jobs, repository) {
  if (run.repository?.full_name !== repository || run.head_repository?.full_name !== repository
    || run.head_branch !== 'main' || run.path !== '.github/workflows/release-gitops.yml'
    || !['workflow_run', 'workflow_dispatch'].includes(run.event)) {
    return { retry: false, reason: 'untrusted release source' };
  }
  if (run.status !== 'completed' || run.conclusion !== 'failure') {
    return { retry: false, reason: 'only completed failures are retried; cancellation is respected' };
  }
  if (!Number.isInteger(run.run_attempt) || run.run_attempt < 1 || run.run_attempt >= 3) {
    return { retry: false, reason: 'release recovery attempt limit reached' };
  }
  if (!Array.isArray(jobs) || jobs.length === 0 || jobs.some((job) => job.status !== 'completed')) {
    return { retry: false, reason: 'release jobs are incomplete' };
  }
  const failed = jobs.filter((job) => !['success', 'skipped'].includes(job.conclusion));
  if (failed.length === 0 || failed.some((job) => job.conclusion !== 'failure'
      || (!RETRYABLE_JOBS.has(job.name) && !RETRYABLE_CLIENT_JOB.test(job.name)))) {
    return { retry: false, reason: 'failure requires diagnosis or production rollback review' };
  }
  return {
    retry: true,
    delaySeconds: failed.some((job) => job.name === 'promote-test') ? 900 : 60,
    reason: `retry failed jobs from attempt ${run.run_attempt} using the same source and artifacts`,
  };
}

function githubApi(path, method = 'GET') {
  return JSON.parse(execFileSync('gh', ['api', '--method', method, path], { encoding: 'utf8' }) || 'null');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const repository = process.env.GITHUB_REPOSITORY;
  const eventRun = JSON.parse(readFileSync(process.env.GITHUB_EVENT_PATH, 'utf8')).workflow_run;
  if (!repository || !Number.isSafeInteger(eventRun?.id)) {
    throw new Error('Missing trusted release event');
  }
  const runPath = `repos/${repository}/actions/runs/${eventRun.id}`;
  const run = githubApi(runPath);
  const { jobs, total_count: totalCount } = githubApi(`${runPath}/attempts/${run.run_attempt}/jobs?per_page=100`);
  if (totalCount > 100) {
    throw new Error('Unexpected release job count');
  }
  const plan = planReleaseRecovery(run, jobs, repository);
  console.log(`RELEASE_RECOVERY ${JSON.stringify(plan)}`);
  if (plan.retry) {
    await new Promise((resolveDelay) => setTimeout(resolveDelay, plan.delaySeconds * 1000));
    const current = githubApi(runPath);
    if (current.run_attempt === run.run_attempt && current.status === 'completed' && current.conclusion === 'failure') {
      githubApi(`${runPath}/rerun-failed-jobs`, 'POST');
      console.log(`RELEASE_RECOVERY_DISPATCHED run=${run.id} nextAttempt=${run.run_attempt + 1}`);
    } else {
      console.log('RELEASE_RECOVERY_SKIPPED: release state already changed');
    }
  }
}
