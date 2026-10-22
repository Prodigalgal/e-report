import { readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';

const options = parseOptions(process.argv.slice(2));
const repoRoot = resolve(options.repoRoot);
const reportDirectory = resolve(options.reportDirectory);
const policy = readJson(options.policy);
const today = new Date().toISOString().slice(0, 10);
const rejected = [];
let acceptedCount = 0;
const imageNames = options.images ? options.images.split(',') : Object.keys(policy.images ?? {});
if (imageNames.length === 0 || new Set(imageNames).size !== imageNames.length ||
    imageNames.some((name) => !Object.hasOwn(policy.images ?? {}, name))) {
  throw new Error('Container scan image selection contains an unknown or duplicate image.');
}

for (const imageName of imageNames) {
  const imagePolicy = policy.images[imageName];
  const reportPath = resolve(reportDirectory, `${imageName}.json`);
  const report = readJson(reportPath);
  const expectedDigest = imagePolicy.reference.match(/@(?<digest>sha256:[a-f0-9]{64})$/)?.groups?.digest;
  if (!expectedDigest || !String(report.ArtifactName ?? '').includes(expectedDigest)) {
    rejected.push(`${imageName}: report does not match policy digest ${expectedDigest ?? '(invalid)'}`);
    continue;
  }

  for (const result of report.Results ?? []) {
    for (const vulnerability of result.Vulnerabilities ?? []) {
      const exception = imagePolicy.exceptions?.find(
        (candidate) =>
          String(result.Target ?? '').includes(candidate.targetContains) &&
          vulnerability.PkgName === candidate.package &&
          vulnerability.InstalledVersion === candidate.installedVersion &&
          candidate.ids?.includes(vulnerability.VulnerabilityID),
      );
      if (!exception) {
        rejected.push(
          `${imageName}: ${vulnerability.VulnerabilityID} ${vulnerability.PkgName}@${vulnerability.InstalledVersion} in ${result.Target} is not reviewed`,
        );
        continue;
      }
      const exceptionErrors = validateException(exception, repoRoot, today);
      if (exceptionErrors.length > 0) {
        rejected.push(...exceptionErrors.map((error) => `${imageName}/${vulnerability.VulnerabilityID}: ${error}`));
        continue;
      }
      acceptedCount += 1;
    }
  }
}

if (rejected.length > 0) {
  console.error(`CONTAINER_SCAN_REJECTED findings=${rejected.length}`);
  for (const error of rejected) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(`CONTAINER_SCAN_ACCEPTED images=${imageNames.length} reviewedFindings=${acceptedCount}`);

function validateException(exception, repoRootPath, currentDate) {
  const errors = [];
  if (!/^\d{4}-\d{2}-\d{2}$/.test(exception.ignoreUntil ?? '') || exception.ignoreUntil < currentDate) {
    errors.push(`exception expired or has invalid ignoreUntil (${exception.ignoreUntil ?? 'missing'})`);
  }
  if (!String(exception.reason ?? '').trim()) {
    errors.push('reason is required');
  }
  if (exception.controlFile) {
    const controlPath = resolve(repoRootPath, exception.controlFile);
    const control = readFileSync(controlPath, 'utf8');
    for (const marker of exception.controlContains ?? []) {
      if (!control.includes(marker)) {
        errors.push(`control marker missing from ${exception.controlFile}: ${marker}`);
      }
    }
  }
  return errors;
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
  for (const required of ['repoRoot', 'reportDirectory', 'policy']) {
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
    throw new Error(`Cannot read JSON ${basename(path)}: ${error.message}`);
  }
}
