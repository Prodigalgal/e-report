import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeNativeSource, createNativeSourceTransform } from './native-source-hash.mjs';

test('APK release identity changes preserve compatibility while SDK and native behavior still affect the fingerprint', () => {
  const path = 'android/app/build.gradle';
  const first = Buffer.from('versionCode 5008002\nversionName "6.8.2"\nminSdkVersion 24\n');
  const next = Buffer.from('versionCode 5011000\nversionName "6.11.0"\nminSdkVersion 24\n');
  assert.deepEqual(normalizeNativeSource(path, first), normalizeNativeSource(path, next));
  assert.notDeepEqual(normalizeNativeSource(path, next), normalizeNativeSource(path, Buffer.from(next.toString().replace('minSdkVersion 24', 'minSdkVersion 26'))));
  assert.notDeepEqual(normalizeNativeSource('MainActivity.kt', first), normalizeNativeSource('MainActivity.kt', next));
});

test('native source hashes are stable across Windows and Linux line endings without changing binaries', () => {
  const text = Buffer.from('原生兼容\nsecond line\n');
  assert.deepEqual(normalizeNativeSource('gradle/verification-metadata.xml', Buffer.from('原生兼容\r\nsecond line\r\n')), text);
  const binary = Buffer.from([0, 13, 10, 255]);
  assert.strictEqual(normalizeNativeSource('gradle/wrapper/gradle-wrapper.jar', binary), binary);
});

test('streaming normalization preserves UTF-8 and CRLF split across chunks', () => {
  const transform = createNativeSourceTransform();
  const source = { type: 'file', filePath: 'MainActivity.kt' };
  const bytes = Buffer.from('中文\r\n代码\r\n');
  for (const byte of bytes) assert.equal(transform(source, Buffer.from([byte]), false), null);
  assert.deepEqual(transform(source, null, true), Buffer.from('中文\n代码\n'));
});

test('the Gradle rewrite of masked-view manifest does not change compatibility while a different namespace still does', () => {
  const file = 'node_modules/@react-native-masked-view/masked-view/android/src/main/AndroidManifest.xml';
  const pristine = Buffer.from('<manifest package="org.reactnative.maskedview" xmlns:android="http://schemas.android.com/apk/res/android">\n</manifest>\n');
  const built = Buffer.from('<manifest  xmlns:android="http://schemas.android.com/apk/res/android">\n</manifest>\n');
  assert.deepEqual(normalizeNativeSource(file, pristine), normalizeNativeSource(file, built));
  assert.notDeepEqual(normalizeNativeSource(file, Buffer.from(pristine.toString().replace('org.reactnative.maskedview', 'another.namespace'))), normalizeNativeSource(file, built));
});
