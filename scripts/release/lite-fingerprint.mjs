import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { normalizeNativeSource } from './native-source-hash.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../e-report-lite');
const sources = [];
function collect(relative) {
  for (const entry of readdirSync(path.join(root, relative), { withFileTypes: true })) {
    const name = path.posix.join(relative, entry.name);
    if (entry.isDirectory()) collect(name);
    else sources.push({ filePath: name, hash: createHash('sha256').update(normalizeNativeSource(name, readFileSync(path.join(root, name)))).digest('hex') });
  }
}
collect('app/src/main'); collect('gradle');
for (const file of ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'app/build.gradle.kts',
  'app/proguard-rules.pro', 'app/gradle.lockfile', 'native-release.json', 'gradlew', 'gradlew.bat']) {
  sources.push({ filePath: file, hash: createHash('sha256').update(normalizeNativeSource(file, readFileSync(path.join(root, file)))).digest('hex') });
}
sources.sort((left, right) => left.filePath.localeCompare(right.filePath, 'en'));
const native = JSON.parse(readFileSync(path.join(root, 'native-release.json'), 'utf8'));
const result = { schemaVersion: 1, nativeVersion: native.version, runtimeVersion: native.runtimeVersion,
  hash: createHash('sha256').update(JSON.stringify(sources)).digest('hex'), sources };
const destination = path.join(root, 'native-fingerprint.json');
if (process.argv.includes('--record')) { writeFileSync(destination, `${JSON.stringify(result, null, 2)}\n`); console.log(`LITE_FINGERPRINT_RECORDED=${result.hash}`); }
else {
  const expected = JSON.parse(readFileSync(destination, 'utf8'));
  if (expected.hash !== result.hash || expected.runtimeVersion !== result.runtimeVersion) throw new Error('Lite 原生指纹变化，需要提升基础包身份并重新记录指纹。');
  console.log(`LITE_FINGERPRINT=${result.hash}`);
}
