import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeNativeSource, createNativeSourceTransform } from './native-source-hash.mjs';

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
