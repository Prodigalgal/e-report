import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { configureDatabaseMigration } from './configure-database-migration.mjs';

const environments = new Set(['test', 'production']);
const imagePattern = /^ghcr\.io\/prodigalgal\/e-report-(backend|frontend)@sha256:[a-f0-9]{64}$/;
const previousImagePattern = /^(?:localhost\/e-report-(?:backend|frontend):\d{8}-v\d+\.\d+\.\d+(?:-[a-z0-9.-]+)?|ghcr\.io\/prodigalgal\/e-report-(?:backend|frontend)@sha256:[a-f0-9]{64})$/;
const TEST_SUPER_ADMIN_USER_ID = '01a000cb-6bc4-72c4-ac47-05168d5fb9b9';

export function replaceDeploymentImage(source, service, image) {
  if (!imagePattern.test(image) || !image.includes(`/e-report-${service}@`)) {
    throw new Error(`Invalid immutable ${service} image: ${image}`);
  }
  const lines = source.split(/(\r?\n)/);
  let replacements = 0;
  for (let index = 0; index < lines.length; index += 2) {
    const match = lines[index].match(/^(\s*(?:-\s+)?image:\s*)(\S+)(\s*)$/);
    if (!match) continue;
    if (!previousImagePattern.test(match[2])) {
      throw new Error(`Unexpected current image: ${match[2]}`);
    }
    lines[index] = `${match[1]}${image}${match[3]}`;
    replacements += 1;
  }
  if (replacements !== 1) {
    throw new Error(`Expected one ${service} image, found ${replacements}`);
  }
  return lines.join('');
}

export function ensureMailSenderSecretMount(source) {
  const lines = source.split(/\r?\n/);
  const lineEnding = source.includes('\r\n') ? '\r\n' : '\n';
  const requiredBlocks = [
    {
      marker: '        - name: MAIL_SENDER_POOL_FILE',
      before: '        - name: APP_UPDATE_RELEASE_DIR',
      lines: [
        '        - name: MAIL_SENDER_POOL_FILE',
        '          value: /var/run/e-report/mail-sender-pool/senders.json',
      ],
      validate: block => block[1] === '          value: /var/run/e-report/mail-sender-pool/senders.json',
      missingMessage: 'APP_UPDATE_RELEASE_DIR env entry not found',
      invalidMessage: 'MAIL_SENDER_POOL_FILE must use the mounted Secret path',
    },
    {
      marker: '        - mountPath: /var/run/e-report/mail-sender-pool',
      before: '      dnsPolicy:',
      lines: [
        '        - mountPath: /var/run/e-report/mail-sender-pool',
        '          name: mail-sender-pool',
        '          readOnly: true',
      ],
      validate: block => block[1] === '          name: mail-sender-pool' && block[2] === '          readOnly: true',
      missingMessage: 'Pod dnsPolicy entry not found after container volumeMounts',
      invalidMessage: 'mail-sender-pool volume mount must be read-only',
      after: '        volumeMounts:',
    },
    {
      marker: '      - name: mail-sender-pool',
      before: '      imagePullSecrets:',
      lines: [
        '      - name: mail-sender-pool',
        '        secret:',
        '          secretName: e-report-notification',
        '          items:',
        '          - key: MAIL_SENDER_POOL_JSON',
        '            path: senders.json',
      ],
      validate: block => block.slice(1).every((line, index) => line === [
        '        secret:',
        '          secretName: e-report-notification',
        '          items:',
        '          - key: MAIL_SENDER_POOL_JSON',
        '            path: senders.json',
      ][index]),
      missingMessage: 'Pod imagePullSecrets entry not found after volumes',
      invalidMessage: 'mail-sender-pool volume must project MAIL_SENDER_POOL_JSON from e-report-notification',
      after: '      volumes:',
    },
  ];

  const hasNotificationSecret = lines.some(line => line === '        - name: NOTIFICATION_CENTER_ENABLED');
  if (!hasNotificationSecret) throw new Error('NOTIFICATION_CENTER_ENABLED Secret reference not found');

  for (const required of requiredBlocks) {
    const matchingMarkers = lines.reduce((matches, line, index) => {
      if (line === required.marker) matches.push(index);
      return matches;
    }, []);
    if (matchingMarkers.length > 1) throw new Error(`Duplicate deployment entry: ${required.marker.trim()}`);
    const existingIndex = matchingMarkers[0] ?? -1;
    if (existingIndex >= 0) {
      const itemPrefix = required.marker.match(/^\s*- /)?.[0];
      const nextBlockIndex = lines.findIndex((line, index) => index > existingIndex && line.startsWith(itemPrefix));
      const existingBlock = lines.slice(existingIndex, nextBlockIndex < 0 ? undefined : nextBlockIndex);
      if (!required.validate(existingBlock.slice(0, required.lines.length))) throw new Error(required.invalidMessage);
      continue;
    }

    const afterIndex = required.after ? lines.indexOf(required.after) : -1;
    if (required.after && afterIndex < 0) throw new Error(`${required.after.trim()} entry not found`);
    const beforeIndex = lines.findIndex((line, index) => index > afterIndex && line.startsWith(required.before));
    if (beforeIndex < 0) throw new Error(required.missingMessage);
    lines.splice(beforeIndex, 0, ...required.lines);
  }
  const updated = lines.join(lineEnding);
  return source.endsWith('\n') && !updated.endsWith('\n') ? `${updated}${lineEnding}` : updated;
}

export function configureTestMailIsolation(source) {
  const lineEnding = source.includes('\r\n') ? '\r\n' : '\n';
  const lines = source.split(/\r?\n/);
  const switches = [
    'NOTIFICATION_CENTER_ENABLED',
    'NOTIFICATION_DELIVERY_ENABLED',
    'NOTIFICATION_EMAIL_ENABLED',
    'APP_OUTBOX_ENQUEUE_ENABLED',
    'APP_OUTBOX_ENABLED',
    'APP_UPDATE_R2_ENABLED',
  ];

  for (const name of switches) {
    setEnvironmentValue(lines, name, "'true'");
  }
  setEnvironmentValue(lines, 'APP_NOTIFICATION_EMAIL_RECIPIENT_ALLOWLIST_USER_IDS', `"${TEST_SUPER_ADMIN_USER_ID}"`, {
    insertAfter: 'DEPLOYMENT_ENVIRONMENT',
  });

  const updated = lines.join(lineEnding);
  return source.endsWith('\n') && !updated.endsWith(lineEnding)
    ? `${updated}${lineEnding}`
    : updated;
}

export function configureExpoUpdates(source) {
  const lines = source.split(/\r?\n/);
  setEnvironmentValue(lines, 'APP_UPDATE_OTA_ENABLED', "'true'", { insertAfter: 'DEPLOYMENT_ENVIRONMENT' });
  return lines.join(source.includes('\r\n') ? '\r\n' : '\n');
}

export function configureClientDistribution(source) {
  const lines = source.split(/\r?\n/);
  setEnvironmentValue(lines, 'APP_UPDATE_R2_PUBLIC_BASE_URL', 'https://download.omnnu.xyz');
  return lines.join(source.includes('\r\n') ? '\r\n' : '\n');
}

function setEnvironmentValue(lines, name, value, { insertAfter } = {}) {
  const indexes = lines.reduce((matches, line, index) => {
    if (line.trim() === `- name: ${name}`) matches.push(index);
    return matches;
  }, []);
  if (indexes.length > 1) throw new Error(`Duplicate deployment environment entry: ${name}`);

  if (indexes.length === 1) {
    const valueIndex = indexes[0] + 1;
    const valueMatch = lines[valueIndex]?.match(/^(\s*value:\s*).*$/);
    if (!valueMatch) throw new Error(`${name} must use a scalar value`);
    lines[valueIndex] = `${valueMatch[1]}${value}`;
    return;
  }

  if (!insertAfter) throw new Error(`Deployment environment entry not found: ${name}`);
  const markerIndexes = lines.reduce((matches, line, index) => {
    if (line.trim() === `- name: ${insertAfter}`) matches.push(index);
    return matches;
  }, []);
  if (markerIndexes.length !== 1) throw new Error(`Expected one deployment environment entry: ${insertAfter}`);
  const valueIndex = markerIndexes[0] + 1;
  if (!/^\s*value:\s*\S+\s*$/.test(lines[valueIndex] ?? '')) {
    throw new Error(`${insertAfter} must use a scalar value`);
  }
  lines.splice(valueIndex + 1, 0, `        - name: ${name}`, `          value: ${value}`);
}

function parseOptions(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 2) {
    if (!argv[index]?.startsWith('--') || !argv[index + 1]) {
      throw new Error('Expected --config-root, --environment, --backend-image and --frontend-image');
    }
    values.set(argv[index].slice(2), argv[index + 1]);
  }
  for (const required of ['config-root', 'environment', 'backend-image', 'frontend-image']) {
    if (!values.get(required)) throw new Error(`Missing --${required}`);
  }
  if (!environments.has(values.get('environment'))) {
    throw new Error('Environment must be test or production');
  }
  return values;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const options = parseOptions(process.argv.slice(2));
  const environment = options.get('environment');
  for (const service of ['backend', 'frontend']) {
    const manifest = resolve(options.get('config-root'), 'environments', environment, `e-report-${service}.yaml`);
    const source = readFileSync(manifest, 'utf8');
    const withEnvironmentConfig = environment === 'production' && service === 'backend'
      ? ensureMailSenderSecretMount(source)
      : environment === 'test' && service === 'backend'
        ? configureTestMailIsolation(source)
        : source;
    const withExpoUpdates = service === 'backend' ? configureExpoUpdates(withEnvironmentConfig) : withEnvironmentConfig;
    const withDistribution = service === 'backend' ? configureClientDistribution(withExpoUpdates) : withExpoUpdates;
    const updated = replaceDeploymentImage(withDistribution, service, options.get(`${service}-image`));
    if (updated !== source) writeFileSync(manifest, updated);
  }
  configureDatabaseMigration(options.get('config-root'), environment, options.get('backend-image'));
  console.log(`GITOPS_IMAGES_UPDATED environment=${environment}`);
}
