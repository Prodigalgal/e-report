import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const severityOrder = new Map([
  ['info', 0],
  ['low', 1],
  ['moderate', 2],
  ['high', 3],
  ['critical', 4],
]);

const options = parseOptions(process.argv.slice(2));
const projectRoot = resolve(options.projectRoot);
const audit = readJson(options.auditJson, 'npm audit result');
const policy = readJson(options.policy, 'npm audit exception policy');
const packageLock = readJson(resolve(projectRoot, 'package-lock.json'), 'package-lock.json');
const projectPolicy = policy.projects?.[options.projectName] ?? { exceptions: [] };
const vulnerabilities = audit.vulnerabilities ?? {};
const findings = [];

for (const [packageName, vulnerability] of Object.entries(vulnerabilities)) {
  if ((severityOrder.get(vulnerability.severity) ?? Number.MAX_SAFE_INTEGER) < severityOrder.get(options.minimumSeverity)) {
    continue;
  }
  const advisories = resolveAdvisories(packageName, vulnerabilities, new Set());
  if (advisories.length === 0) {
    findings.push({
      affectedPackage: packageName,
      id: `UNRESOLVED-${packageName}`,
      advisoryPackage: packageName,
    });
  }
  for (const advisory of advisories) {
    findings.push({ affectedPackage: packageName, ...advisory });
  }
}

const uniqueFindings = deduplicate(findings);
const rejected = [];
const accepted = [];

for (const finding of uniqueFindings) {
  const exception = projectPolicy.exceptions?.find(
    (candidate) => candidate.id === finding.id && candidate.package === finding.advisoryPackage,
  );
  if (!exception) {
    rejected.push(`${finding.id} (${finding.advisoryPackage}, via ${finding.affectedPackage}) has no reviewed exception`);
    continue;
  }

  const exceptionErrors = validateException(exception, packageLock);
  if (exceptionErrors.length > 0) {
    rejected.push(...exceptionErrors.map((error) => `${finding.id}: ${error}`));
    continue;
  }
  accepted.push(finding);
}

if (rejected.length > 0) {
  console.error(`NPM_AUDIT_REJECTED project=${options.projectName} findings=${rejected.length}`);
  for (const rejection of rejected) {
    console.error(`- ${rejection}`);
  }
  process.exit(1);
}

console.log(
  `NPM_AUDIT_ACCEPTED project=${options.projectName} vulnerablePackages=${Object.keys(vulnerabilities).length} reviewedAdvisories=${accepted.length}`,
);

function resolveAdvisories(packageName, graph, stack) {
  if (stack.has(packageName)) {
    return [];
  }
  const vulnerability = graph[packageName];
  if (!vulnerability) {
    return [{ id: `UNRESOLVED-${packageName}`, advisoryPackage: packageName }];
  }

  const nextStack = new Set(stack);
  nextStack.add(packageName);
  const advisories = [];
  for (const source of vulnerability.via ?? []) {
    if (typeof source === 'string') {
      advisories.push(...resolveAdvisories(source, graph, nextStack));
      continue;
    }
    advisories.push({
      id: advisoryId(source),
      advisoryPackage: source.name ?? packageName,
    });
  }
  return advisories;
}

function advisoryId(advisory) {
  const ghsa = String(advisory.url ?? '').match(/GHSA-[a-z0-9-]+/i)?.[0];
  return ghsa ?? `NPM-${advisory.source ?? 'UNKNOWN'}`;
}

function validateException(exception, packageLock) {
  const errors = [];
  const today = new Date().toISOString().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(exception.ignoreUntil ?? '') || exception.ignoreUntil < today) {
    errors.push(`exception expired or has an invalid ignoreUntil (${exception.ignoreUntil ?? 'missing'})`);
  }
  if (!String(exception.reason ?? '').trim()) {
    errors.push('exception reason is required');
  }

  const packageEntries = Object.entries(packageLock.packages ?? {}).filter(
    ([path, descriptor]) => descriptor?.name === exception.package || path.endsWith(`/node_modules/${exception.package}`) || path === `node_modules/${exception.package}`,
  );
  if (packageEntries.length === 0) {
    errors.push(`package ${exception.package} is absent from package-lock.json`);
  }
  const actualVersions = [...new Set(packageEntries.map(([, descriptor]) => descriptor.version).filter(Boolean))];
  const unexpectedVersions = actualVersions.filter((version) => !exception.versions?.includes(version));
  if (unexpectedVersions.length > 0) {
    errors.push(`unexpected package version(s): ${unexpectedVersions.join(', ')}`);
  }

  const rootDescriptor = packageLock.packages?.[''] ?? {};
  if (rootDescriptor.dependencies?.[exception.package] || rootDescriptor.devDependencies?.[exception.package]) {
    errors.push(`${exception.package} became a direct project dependency`);
  }

  const actualParents = [...new Set(
    Object.entries(packageLock.packages ?? {})
      .filter(([, descriptor]) => descriptor?.dependencies?.[exception.package])
      .map(([path, descriptor]) => lockPackageName(path, descriptor)),
  )];
  if (actualParents.length === 0) {
    errors.push(`cannot prove a dependency parent for ${exception.package}`);
  }
  const unexpectedParents = actualParents.filter((parent) => !exception.allowedParents?.includes(parent));
  if (unexpectedParents.length > 0) {
    errors.push(`unexpected dependency parent(s): ${unexpectedParents.join(', ')}`);
  }
  return errors;
}

function lockPackageName(path, descriptor) {
  if (descriptor.name) {
    return descriptor.name;
  }
  const marker = 'node_modules/';
  const markerIndex = path.lastIndexOf(marker);
  return markerIndex >= 0 ? path.slice(markerIndex + marker.length) : '(project-root)';
}

function deduplicate(findings) {
  const seen = new Set();
  return findings.filter((finding) => {
    const key = `${finding.id}|${finding.advisoryPackage}|${finding.affectedPackage}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
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
  for (const required of ['projectName', 'projectRoot', 'auditJson', 'policy']) {
    if (!parsed[required]) {
      throw new Error(`Missing --${required}`);
    }
  }
  parsed.minimumSeverity ??= 'info';
  if (!severityOrder.has(parsed.minimumSeverity)) {
    throw new Error(`Unsupported --minimumSeverity ${parsed.minimumSeverity}`);
  }
  return parsed;
}

function readJson(path, description) {
  try {
    return JSON.parse(readFileSync(resolve(path), 'utf8').replace(/^\uFEFF/, ''));
  } catch (error) {
    throw new Error(`Cannot read ${description} at ${path}: ${error.message}`);
  }
}
