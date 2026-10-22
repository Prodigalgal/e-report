import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const REQUIRED_NPM_SETTINGS = new Map([
  ['registry', 'https://registry.npmjs.org/'],
  ['replace-registry-host', 'always'],
  ['ignore-scripts', 'true'],
  ['package-lock', 'true'],
]);
const NPM_REGISTRY_PREFIX = 'https://registry.npmjs.org/';
const SHA256_DIGEST = /@sha256:[a-f0-9]{64}$/;
const VERSIONED_LOCAL_IMAGE = /^localhost\/e-report-[a-z0-9-]+:\d{8}-v\d+\.\d+\.\d+(?:-[a-z0-9.-]+)?$/;

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const repoRoot = resolve(parseOptions(process.argv.slice(2)).repoRoot);
  const errors = validateRepositoryContract(repoRoot);
  if (errors.length > 0) {
    console.error(`SUPPLY_CHAIN_CONTRACT_REJECTED errors=${errors.length}`);
    for (const error of errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }
  console.log('SUPPLY_CHAIN_CONTRACT_ACCEPTED');
}

export function validateRepositoryContract(repoRoot) {
  const errors = [];
  for (const project of ['e-report', 'e-report-app']) {
    errors.push(...validateNpmProject(repoRoot, project));
  }
  errors.push(...validateGradleProject(repoRoot, {
    project: 'e-report-backen',
    wrapper: 'e-report-backen/gradle/wrapper/gradle-wrapper.properties',
    locks: ['e-report-backen/gradle.lockfile'],
    verificationMetadata: 'e-report-backen/gradle/verification-metadata.xml',
    buildFile: 'e-report-backen/build.gradle',
    requiredRepositories: ['mavenCentral()'],
  }));
  errors.push(...validateGradleProject(repoRoot, {
    project: 'e-report-app/android',
    wrapper: 'e-report-app/android/gradle/wrapper/gradle-wrapper.properties',
    locks: [
      'e-report-app/android/app/gradle.lockfile',
      'e-report-app/android/settings-gradle.lockfile',
    ],
    verificationMetadata: 'e-report-app/android/gradle/verification-metadata.xml',
    buildFile: 'e-report-app/android/build.gradle',
    requiredRepositories: ['google()', 'mavenCentral()'],
  }));
  errors.push(...validateWorkflowDirectory(repoRoot, '.github/workflows'));
  errors.push(...validateGradleProject(repoRoot, {
    project: 'e-report-lite',
    wrapper: 'e-report-lite/gradle/wrapper/gradle-wrapper.properties',
    locks: ['e-report-lite/app/gradle.lockfile'],
    verificationMetadata: 'e-report-lite/gradle/verification-metadata.xml',
    buildFile: 'e-report-lite/settings.gradle.kts',
    requiredRepositories: ['google()', 'mavenCentral()'],
  }));
  errors.push(...validateDockerfiles(repoRoot, [
    'e-report/Dockerfile',
    'e-report-backen/Dockerfile',
    'deploy/dedicated-cloud/Dockerfile.backend-runtime',
    'deploy/dedicated-cloud/Dockerfile.database-migration-runtime',
    'deploy/dedicated-cloud/Dockerfile.frontend-runtime',
    'deploy/dedicated-cloud/Dockerfile.mysql-runtime',
    'deploy/dedicated-cloud/Dockerfile.test-sync-kubectl',
  ]));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-production.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-test.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-test-postgresql-v5.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-test-mysql-v5-source-upgrade.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-test-postgresql-v5-baseline.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/dedicated-cloud/e-report-test-postgresql-v5-data-migration.yaml', false));
  errors.push(...validateDeploymentManifest(repoRoot, 'deploy/local-k8s/e-report.yaml', true));
  errors.push(...validateToolDownloads(repoRoot, 'scripts/security/run-supply-chain-audit.ps1'));
  errors.push(...validateReleaseEvidenceScript(repoRoot, 'deploy/dedicated-cloud/build-runtime-images.sh'));
  errors.push(...validateFrontendDockerContext(
    readRequired(resolve(repoRoot, 'e-report/Dockerfile'), errors, 'e-report/Dockerfile'),
    readRequired(resolve(repoRoot, '.dockerignore'), errors, '.dockerignore'),
    readRequired(
      resolve(repoRoot, 'deploy/local-k8s/deploy-local-import-containerd.sh'),
      errors,
      'deploy/local-k8s/deploy-local-import-containerd.sh',
    ),
  ));
  return errors;
}

export function validateFrontendDockerContext(dockerfile, dockerignore, deploymentScript) {
  const errors = [];
  const requiredDockerfileFragments = [
    'COPY e-report/package*.json ./',
    'COPY e-report/ .',
    'COPY shared/ /shared/',
    'COPY e-report/nginx.conf /etc/nginx/conf.d/default.conf',
  ];
  for (const fragment of requiredDockerfileFragments) {
    if (!dockerfile.includes(fragment)) {
      errors.push(`e-report/Dockerfile must retain root-context copy rule: ${fragment}`);
    }
  }
  for (const fragment of ['!e-report/**', '!shared/**', 'e-report/node_modules']) {
    if (!dockerignore.includes(fragment)) {
      errors.push(`.dockerignore must retain frontend root-context rule: ${fragment}`);
    }
  }
  for (const fragment of ['cd "$WORKDIR"', '-f e-report/Dockerfile']) {
    if (!deploymentScript.includes(fragment)) {
      errors.push(`deploy/local-k8s/deploy-local-import-containerd.sh must retain: ${fragment}`);
    }
  }
  return errors;
}

export function validateNpmProject(repoRoot, project) {
  const errors = [];
  const npmrcPath = resolve(repoRoot, project, '.npmrc');
  const lockPath = resolve(repoRoot, project, 'package-lock.json');
  const settings = parseNpmrc(readRequired(npmrcPath, errors, `${project}/.npmrc`));
  for (const [key, expected] of REQUIRED_NPM_SETTINGS) {
    if (settings.get(key) !== expected) {
      errors.push(`${project}/.npmrc must set ${key}=${expected}`);
    }
  }

  const lock = readJson(lockPath, errors, `${project}/package-lock.json`);
  if (!lock) {
    return errors;
  }
  if (lock.lockfileVersion !== 3) {
    errors.push(`${project}/package-lock.json must use lockfileVersion 3`);
  }
  for (const [packagePath, descriptor] of Object.entries(lock.packages ?? {})) {
    if (!packagePath || !descriptor?.resolved) {
      continue;
    }
    if (!descriptor.resolved.startsWith(NPM_REGISTRY_PREFIX)) {
      errors.push(`${project}/${packagePath} resolves outside the official npm registry: ${descriptor.resolved}`);
    }
    if (!/^sha512-[A-Za-z0-9+/=]+$/.test(descriptor.integrity ?? '')) {
      errors.push(`${project}/${packagePath} is missing a SHA-512 lock integrity value`);
    }
  }
  return errors;
}

export function validateWorkflowText(path, source) {
  const errors = [];
  for (const [index, line] of source.split(/\r?\n/).entries()) {
    const action = line.match(/^\s*-?\s*uses:\s*([^\s#]+)/)?.[1];
    if (!action || action.startsWith('./')) {
      continue;
    }
    if (!/^[^@\s]+@[a-f0-9]{40}$/.test(action)) {
      errors.push(`${path}:${index + 1} must pin GitHub Action ${action} to a 40-character commit SHA`);
    }
  }
  return errors;
}

export function validateDeploymentManifestText(path, source, allowLocalDevelopmentTags) {
  const errors = [];
  for (const [index, line] of source.split(/\r?\n/).entries()) {
    const image = line.match(/^\s*image:\s*([^\s#]+)/)?.[1];
    if (!image) {
      continue;
    }
    if (image.startsWith('localhost/')) {
      if (!allowLocalDevelopmentTags && !VERSIONED_LOCAL_IMAGE.test(image)) {
        errors.push(`${path}:${index + 1} uses a mutable or unversioned local image: ${image}`);
      }
      continue;
    }
    if (!SHA256_DIGEST.test(image)) {
      errors.push(`${path}:${index + 1} must pin external image ${image} by sha256 digest`);
    }
  }
  return errors;
}

function validateGradleProject(repoRoot, contract) {
  const errors = [];
  const wrapper = readRequired(resolve(repoRoot, contract.wrapper), errors, contract.wrapper);
  if (!/^distributionUrl=https\\?:\/\/services\.gradle\.org\/distributions\/gradle-[^\s]+-bin\.zip$/m.test(wrapper)) {
    errors.push(`${contract.wrapper} must use the official Gradle distribution service`);
  }
  if (!/^distributionSha256Sum=[a-f0-9]{64}$/m.test(wrapper)) {
    errors.push(`${contract.wrapper} must pin distributionSha256Sum`);
  }
  if (!/^validateDistributionUrl=true$/m.test(wrapper)) {
    errors.push(`${contract.wrapper} must enable validateDistributionUrl`);
  }

  for (const lockFile of contract.locks) {
    const lock = readRequired(resolve(repoRoot, lockFile), errors, lockFile);
    if (!lock.includes('empty=')) {
      errors.push(`${lockFile} is not a complete Gradle dependency lock`);
    }
    for (const [index, line] of lock.split(/\r?\n/).entries()) {
      const coordinate = line.match(/^([^#][^=]+)=/)?.[1];
      if (!coordinate || coordinate === 'empty') {
        continue;
      }
      const version = coordinate.split(':').at(-1) ?? '';
      if (version.includes('+') || /latest|snapshot|^[[(]/i.test(version)) {
        errors.push(`${lockFile}:${index + 1} contains a dynamic dependency version: ${coordinate}`);
      }
    }
  }

  const metadata = readRequired(resolve(repoRoot, contract.verificationMetadata), errors, contract.verificationMetadata);
  const checksums = metadata.match(/<(?:sha256|sha512)\s+value="[a-f0-9]{64,128}"/g) ?? [];
  if (!/<verification-metadata(?:\s|>)/.test(metadata) || checksums.length === 0) {
    errors.push(`${contract.verificationMetadata} must contain dependency checksums`);
  }
  if (metadata.includes('<trusted-artifacts>') || metadata.includes('<trust ')) {
    errors.push(`${contract.verificationMetadata} must not use blanket trusted-artifact bypasses`);
  }

  const buildFile = readRequired(resolve(repoRoot, contract.buildFile), errors, contract.buildFile);
  for (const repository of contract.requiredRepositories) {
    if (!buildFile.includes(repository)) {
      errors.push(`${contract.buildFile} must retain repository ${repository}`);
    }
  }
  if (/jitpack|maven\s*\{\s*url/si.test(buildFile)) {
    errors.push(`${contract.buildFile} must not add an unapproved custom Maven repository`);
  }
  return errors;
}

function validateWorkflowDirectory(repoRoot, directory) {
  const errors = [];
  const root = resolve(repoRoot, directory);
  const workflowFiles = listFiles(root).filter((candidate) => /\.ya?ml$/i.test(candidate));
  for (const path of workflowFiles) {
    const relativePath = path.slice(repoRoot.length + 1).replaceAll('\\', '/');
    errors.push(...validateWorkflowText(relativePath, readFileSync(path, 'utf8')));
  }
  if (workflowFiles.length === 0) {
    errors.push(`${directory} must contain at least one workflow`);
  }
  return errors;
}

function validateDockerfiles(repoRoot, dockerfiles) {
  const errors = [];
  for (const dockerfile of dockerfiles) {
    const source = readRequired(resolve(repoRoot, dockerfile), errors, dockerfile);
    const pinnedArgs = new Set();
    for (const [index, line] of source.split(/\r?\n/).entries()) {
      const baseArg = line.match(/^ARG\s+([A-Z0-9_]+_BASE_IMAGE)=([^\s]+)$/);
      if (baseArg) {
        if (!SHA256_DIGEST.test(baseArg[2])) {
          errors.push(`${dockerfile}:${index + 1} must pin ${baseArg[1]} by sha256 digest`);
        }
        pinnedArgs.add(baseArg[1]);
      }
      const from = line.match(/^FROM\s+([^\s]+)(?:\s+AS\s+\S+)?$/i)?.[1];
      if (!from || from.toLowerCase() === 'scratch') {
        continue;
      }
      const variable = from.match(/^\$\{([A-Z0-9_]+)\}$/)?.[1];
      if (variable ? !pinnedArgs.has(variable) : !SHA256_DIGEST.test(from)) {
        errors.push(`${dockerfile}:${index + 1} has an unpinned base image: ${from}`);
      }
    }
  }
  return errors;
}

function validateDeploymentManifest(repoRoot, path, allowLocalDevelopmentTags) {
  const source = readRequired(resolve(repoRoot, path), [], path);
  if (!source) {
    return [`missing required file ${path}`];
  }
  return validateDeploymentManifestText(path, source, allowLocalDevelopmentTags);
}

function validateToolDownloads(repoRoot, path) {
  const errors = [];
  const source = readRequired(resolve(repoRoot, path), errors, path);
  const downloads = [...source.matchAll(/(?:Windows|Linux)\s*=\s*@\('https:\/\/[^']+',\s*'([^']+)'/g)];
  if (downloads.length === 0) {
    errors.push(`${path} must declare checksum-pinned tool downloads`);
  }
  for (const match of downloads) {
    if (!/^[a-f0-9]{64}$/.test(match[1])) {
      errors.push(`${path} contains a tool download without a valid SHA-256 checksum`);
    }
  }
  return errors;
}

function validateReleaseEvidenceScript(repoRoot, path) {
  const errors = [];
  const source = readRequired(resolve(repoRoot, path), errors, path);
  for (const marker of ['SBOM_PATH', 'SHA256SUMS', 'sha256sum -c SHA256SUMS']) {
    if (!source.includes(marker)) {
      errors.push(`${path} must retain release evidence marker ${marker}`);
    }
  }
  return errors;
}

function parseNpmrc(source) {
  return new Map(source.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line) => {
    const separator = line.indexOf('=');
    return separator < 0 ? [line, ''] : [line.slice(0, separator), line.slice(separator + 1)];
  }));
}

function listFiles(root) {
  if (!existsSync(root)) {
    return [];
  }
  const files = [];
  for (const entry of readdirSync(root)) {
    const path = resolve(root, entry);
    if (statSync(path).isDirectory()) {
      files.push(...listFiles(path));
    } else {
      files.push(path);
    }
  }
  return files;
}

function readRequired(path, errors, label) {
  if (!existsSync(path)) {
    errors.push(`missing required file ${label}`);
    return '';
  }
  return readFileSync(path, 'utf8');
}

function readJson(path, errors, label) {
  const source = readRequired(path, errors, label);
  if (!source) {
    return null;
  }
  try {
    return JSON.parse(source.replace(/^\uFEFF/, ''));
  } catch (error) {
    errors.push(`${label} is invalid JSON: ${error.message}`);
    return null;
  }
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
  if (!parsed.repoRoot) {
    throw new Error('Missing --repoRoot');
  }
  return parsed;
}
