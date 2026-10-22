import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const SOURCE_ROLES = new Map([
  ['e-report-app/android/app/gradle.lockfile', 'android-all-configurations-lock'],
  ['e-report-app/android/gradle/verification-metadata.xml', 'android-verification-metadata'],
  ['e-report-backen/gradle/verification-metadata.xml', 'backend-verification-metadata'],
  ['e-report-lite/app/gradle.lockfile', 'lite-all-configurations-lock'],
  ['e-report-lite/gradle/verification-metadata.xml', 'lite-verification-metadata'],
]);

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const options = parseOptions(process.argv.slice(2));
  const releaseReport = readJson(options.releaseReport);
  const buildToolingReport = readJson(options.buildToolingReport);
  const policy = readJson(options.policy);
  const errors = validateOsvReports(releaseReport, buildToolingReport, policy);
  if (errors.length > 0) {
    console.error(`OSV_REPORTS_REJECTED errors=${errors.length}`);
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }
  const summary = summarizeBuildToolingReport(buildToolingReport);
  console.log(
    `OSV_REPORTS_ACCEPTED releaseFindings=0 buildToolingFindings=${summary.findingCount} `
      + `affectedPackageVersions=${summary.affectedPackageVersionCount} advisories=${summary.advisoryCount}`,
  );
}

export function validateOsvReports(releaseReport, buildToolingReport, policy, currentDate = new Date()) {
  const errors = [];
  const releaseFindings = collectFindings(releaseReport, false);
  if (releaseFindings.keys.length > 0) {
    errors.push(`release dependency graph contains ${releaseFindings.keys.length} unreviewed finding(s)`);
  }

  let summary;
  try {
    summary = summarizeBuildToolingReport(buildToolingReport);
  } catch (error) {
    errors.push(error.message);
    return errors;
  }
  const today = currentDate.toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(policy.reviewUntil ?? '') || policy.reviewUntil < today) {
    errors.push(`build-tooling baseline expired or has invalid reviewUntil (${policy.reviewUntil ?? 'missing'})`);
  }
  if (!String(policy.reason ?? '').trim()) {
    errors.push('build-tooling baseline reason is required');
  }
  const expected = policy.expected ?? {};
  for (const field of [
    'findingCount',
    'sourcePackageVersionCount',
    'affectedPackageVersionCount',
    'advisoryCount',
    'fingerprintSha256',
  ]) {
    if (expected[field] !== summary[field]) {
      errors.push(`build-tooling ${field} changed: expected ${expected[field]}, got ${summary[field]}`);
    }
  }
  return errors;
}

export function summarizeBuildToolingReport(report) {
  const findings = collectFindings(report, true);
  const sourcePackageVersions = new Set();
  const affectedPackageVersions = new Set();
  const advisories = new Set();
  for (const key of findings.keys) {
    const [sourceRole, ecosystem, packageName, version, advisory] = key.split('|');
    sourcePackageVersions.add(`${sourceRole}|${ecosystem}|${packageName}|${version}`);
    affectedPackageVersions.add(`${ecosystem}|${packageName}|${version}`);
    advisories.add(advisory);
  }
  return {
    findingCount: findings.keys.length,
    sourcePackageVersionCount: sourcePackageVersions.size,
    affectedPackageVersionCount: affectedPackageVersions.size,
    advisoryCount: advisories.size,
    fingerprintSha256: createHash('sha256').update(JSON.stringify(findings.keys)).digest('hex'),
  };
}

function collectFindings(report, requireKnownSource) {
  const keys = [];
  for (const result of report.results ?? []) {
    const sourceRole = requireKnownSource ? resolveSourceRole(result.source?.path) : 'release';
    for (const packageFinding of result.packages ?? []) {
      const descriptor = packageFinding.package ?? {};
      for (const group of packageFinding.groups ?? []) {
        for (const advisory of group.ids ?? []) {
          keys.push([
            sourceRole,
            descriptor.ecosystem ?? '(unknown)',
            descriptor.name ?? '(unknown)',
            descriptor.version ?? '(unknown)',
            advisory,
          ].join('|'));
        }
      }
    }
  }
  return { keys: [...new Set(keys)].sort() };
}

function resolveSourceRole(path) {
  const normalized = String(path ?? '').replaceAll('\\', '/');
  for (const [suffix, role] of SOURCE_ROLES) {
    if (normalized.endsWith(suffix)) {
      return role;
    }
  }
  throw new Error(`build-tooling report contains an unexpected source: ${path ?? '(missing)'}`);
}

function parseOptions(args) {
  const parsed = {};
  for (let index = 0; index < args.length; index += 2) {
    const key = args[index]?.replace(/^--/, '');
    const value = args[index + 1];
    if (!key || value === undefined) {
      throw new Error(`Invalid option near ${args[index] ?? '(end)'}`);
    }
    parsed[key] = value;
  }
  for (const required of ['releaseReport', 'buildToolingReport', 'policy']) {
    if (!parsed[required]) {
      throw new Error(`Missing --${required}`);
    }
  }
  return parsed;
}

function readJson(path) {
  try {
    return JSON.parse(readFileSync(resolve(path), 'utf8').replace(/^\uFEFF/, ''));
  } catch (error) {
    throw new Error(`Cannot read JSON ${path}: ${error.message}`);
  }
}
