import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

test('both runtime PostgreSQL and H2 changelogs include the edition and OTA migrations before the business tag', () => {
  for (const root of ['db/changelog/db.changelog-master.yaml', 'db/changelog/postgresql/db.changelog-master.yaml']) {
    const changelog = readFileSync(new URL(`../../e-report-backen/src/main/resources/${root}`, import.meta.url), 'utf8');
    const edition = changelog.indexOf('2026-10-21-075-app-editions.sql');
    const ota = changelog.indexOf('2026-10-21-076-expo-updates.sql');
    const tag = changelog.indexOf('tag: e-report-6.8.0');
    assert.ok(edition > 0 && ota > edition && tag > ota, `${root} must migrate both clients before tagging 6.8.0`);
  }
});
