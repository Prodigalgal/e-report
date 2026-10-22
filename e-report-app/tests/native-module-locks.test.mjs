import assert from 'node:assert/strict';
import test from 'node:test';
import { validateNativeModuleLocks } from '../scripts/verify-native-module-locks.mjs';

const resolution = { modules: [{ packageName: 'expo-crypto', projects: [{ publication: {
  groupId: 'host.exp.exponent', artifactId: 'expo.modules.crypto', version: '57.0.4',
} }] }] };
const coordinate = 'host.exp.exponent:expo.modules.crypto:';

test('accepts current Expo native publications locked in release runtime', () => {
  assert.deepEqual(validateNativeModuleLocks(resolution, `${coordinate}57.0.4=debugRuntimeClasspath,releaseRuntimeClasspath\n`), []);
});

test('rejects an SDK patch whose native locks still name the preceding package', () => {
  assert.equal(validateNativeModuleLocks(resolution, `${coordinate}57.0.3=releaseRuntimeClasspath\n`).length, 2);
});

test('rejects missing release locks, old locks in another variant, and empty autolinking output', () => {
  assert.equal(validateNativeModuleLocks(resolution, `${coordinate}57.0.4=debugRuntimeClasspath\n`).length, 1);
  assert.equal(validateNativeModuleLocks(resolution, `${coordinate}57.0.4=releaseRuntimeClasspath\n${coordinate}57.0.3=debugRuntimeClasspath\n`).length, 1);
  assert.equal(validateNativeModuleLocks({}, '').length, 1);
});
