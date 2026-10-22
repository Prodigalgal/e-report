import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const CI_FILES = new Set([
  'scripts/security/verify-ci-maintenance-scope.mjs',
  'scripts/security/test/verify-ci-maintenance-scope.test.mjs',
  'scripts/release/recover-release.mjs',
  'scripts/release/recover-release.test.mjs',
  'scripts/release/publish-app-release.test.mjs',
  'e-report-app/scripts/publish-app-release.ps1',
  'docs/decisions/2026-09-24-github-actions-argocd-gitops.md',
  'tasks/current.md',
]);

export function isCiMaintenanceRequest(eventName, message) {
  return eventName === 'push' && message.startsWith('ci-only:');
}

export function verifyCiMaintenanceScope({ eventName, message, beforeSha, headSha, changedFiles }) {
  if (!isCiMaintenanceRequest(eventName, message)) {
    return false;
  }
  for (const sha of [beforeSha, headSha]) {
    if (!/^[a-f0-9]{40}$/.test(sha) || /^0+$/.test(sha)) {
      throw new Error('CI maintenance requires two existing immutable commit SHAs');
    }
  }
  if (changedFiles.length === 0) {
    throw new Error('CI maintenance must contain a change');
  }
  const rejected = changedFiles.filter((file) => !CI_FILES.has(file)
    && !/^\.github\/workflows\/[a-z0-9-]+\.ya?ml$/.test(file));
  if (rejected.length > 0) {
    throw new Error(`CI maintenance contains changes requiring a new business candidate: ${rejected.join(', ')}`);
  }
  return true;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const eventName = process.env.GITHUB_EVENT_NAME ?? '';
  const message = process.env.CI_COMMIT_MESSAGE ?? '';
  const beforeSha = process.env.CI_BEFORE_SHA ?? '';
  const headSha = process.env.GITHUB_SHA ?? '';
  let changedFiles = [];
  if (isCiMaintenanceRequest(eventName, message)) {
    if (![beforeSha, headSha].every((sha) => /^[a-f0-9]{40}$/.test(sha) && !/^0+$/.test(sha))) {
      throw new Error('Invalid CI maintenance commit range');
    }
    changedFiles = execFileSync('git', ['diff', '--name-only', '--no-renames', '-z', beforeSha, headSha], {
      encoding: 'utf8',
    }).split('\0').filter(Boolean);
  }
  const maintenance = verifyCiMaintenanceScope({ eventName, message, beforeSha, headSha, changedFiles });
  if (process.env.GITHUB_OUTPUT) {
    appendFileSync(process.env.GITHUB_OUTPUT, `maintenance=${maintenance}\n`);
  }
  console.log(maintenance ? 'CI_MAINTENANCE_ACCEPTED: business artifacts will not be rebuilt' : 'BUSINESS_CANDIDATE');
}
