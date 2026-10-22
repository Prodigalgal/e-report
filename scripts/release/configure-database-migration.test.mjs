import assert from 'node:assert/strict';
import test from 'node:test';
import { addMigrationPatch, databaseMigrationPatch } from './configure-database-migration.mjs';

const image = `ghcr.io/prodigalgal/e-report-backend@sha256:${'a'.repeat(64)}`;

test('migration uses the same immutable backend artifact and a separate database credential', () => {
  const patch = databaseMigrationPatch('test', image);
  assert.ok(patch.includes(`image: ${image}`));
  assert.ok(patch.includes('namespace: e-report-test'));
  assert.ok(patch.includes('initContainers:'));
  assert.ok(patch.includes('LiquibaseMigrationMain'));
  assert.ok(patch.includes('secretKeyRef:'));
  assert.ok(patch.includes('name: e-report-postgresql'));
  assert.ok(databaseMigrationPatch('production', image).includes('namespace: e-report\n'));
  assert.throws(() => databaseMigrationPatch('test', 'backend:latest'));
  assert.throws(() => databaseMigrationPatch('../production', image));
});

test('kustomization keeps existing resources and patches and adds the migration exactly once', () => {
  const source = 'resources:\n  - backend.yaml\npatches:\n  - path: custom.yaml\nimages:\n  - name: backend\n';
  const updated = addMigrationPatch(source);
  assert.ok(updated.includes('  - path: custom.yaml'));
  assert.ok(updated.includes('  - backend.yaml'));
  assert.equal(updated.match(/path: patches\/e-report-database-migration.yaml/g)?.length, 1);
  assert.equal(addMigrationPatch(updated), updated);
  assert.ok(addMigrationPatch('resources:\n  - backend.yaml\n').includes('\npatches:\n'));
});
