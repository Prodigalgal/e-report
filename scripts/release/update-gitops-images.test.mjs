import assert from 'node:assert/strict';
import test from 'node:test';

import { configureTestMailIsolation, configureExpoUpdates, configureClientDistribution } from './update-gitops-images.mjs';

const testManifest = [
  '        - name: DEPLOYMENT_ENVIRONMENT',
  '          value: test',
  "        - name: NOTIFICATION_CENTER_ENABLED",
  "          value: 'false'",
  "        - name: NOTIFICATION_DELIVERY_ENABLED",
  "          value: 'false'",
  "        - name: NOTIFICATION_EMAIL_ENABLED",
  "          value: 'false'",
  "        - name: APP_OUTBOX_ENQUEUE_ENABLED",
  "          value: 'false'",
  "        - name: APP_OUTBOX_ENABLED",
  "          value: 'false'",
  "        - name: APP_UPDATE_R2_ENABLED",
  "          value: 'false'",
  '',
].join('\n');

test('test GitOps opens only the mail/outbox gates and restricts recipients to test SuperAdmin', () => {
  const configured = configureTestMailIsolation(testManifest);

  for (const name of [
    'NOTIFICATION_CENTER_ENABLED',
    'NOTIFICATION_DELIVERY_ENABLED',
    'NOTIFICATION_EMAIL_ENABLED',
    'APP_OUTBOX_ENQUEUE_ENABLED',
    'APP_OUTBOX_ENABLED',
    'APP_UPDATE_R2_ENABLED',
  ]) {
    assert.match(configured, new RegExp(`- name: ${name}\\n\\s+value: 'true'`));
  }
  assert.match(configured, /name: APP_NOTIFICATION_EMAIL_RECIPIENT_ALLOWLIST_USER_IDS\n\s+value: "01a000cb-6bc4-72c4-ac47-05168d5fb9b9"/);
  assert.equal(configureTestMailIsolation(configured), configured);
});

test('test mail isolation rejects malformed manifests rather than deploying an open recipient policy', () => {
  assert.throws(
    () => configureTestMailIsolation(testManifest.replace('value: test', 'valueFrom:')),
    /DEPLOYMENT_ENVIRONMENT must use a scalar value/,
  );
  assert.throws(
    () => configureTestMailIsolation(`${testManifest}        - name: APP_OUTBOX_ENABLED\n          value: 'false'\n`),
    /Duplicate deployment environment entry: APP_OUTBOX_ENABLED/,
  );
});

test('enables OTA explicitly and idempotently without changing deployment identity or mail settings', () => {
  const source = configureExpoUpdates(testManifest);
  assert.match(source, /name: APP_UPDATE_OTA_ENABLED\n\s+value: 'true'/);
  assert.match(source, /name: DEPLOYMENT_ENVIRONMENT\n\s+value: test/);
  assert.match(source, /name: NOTIFICATION_EMAIL_ENABLED\n\s+value: 'false'/);
  assert.equal(configureExpoUpdates(source), source);
});

test('client distribution uses the verified bucket domain and preserves environment object prefixes', () => {
  for (const environment of ['test', 'production']) {
    const prefix = environment === 'test' ? 'e-report-test' : 'e-report';
    const source = `        - name: APP_UPDATE_R2_PUBLIC_BASE_URL\n          value: https://e-report.example\n        - name: APP_UPDATE_R2_OBJECT_PREFIX\n          value: ${prefix}\n`;
    const configured = configureClientDistribution(source);
    assert.match(configured, /APP_UPDATE_R2_PUBLIC_BASE_URL\n\s+value: https:\/\/download\.omnnu\.xyz/);
    assert.ok(configured.includes(`value: ${prefix}\n`));
    assert.equal(configureClientDistribution(configured), configured);
  }
  assert.throws(() => configureClientDistribution(''), /environment entry not found/);
});
