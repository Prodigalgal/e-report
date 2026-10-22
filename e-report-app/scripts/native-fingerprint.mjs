import { createFingerprintAsync, SourceSkips } from '@expo/fingerprint';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createNativeSourceTransform } from '../../scripts/release/native-source-hash.mjs';

const appRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const fingerprint = await createFingerprintAsync(appRoot, {
  platforms: ['android'], hashAlgorithm: 'sha256',
  sourceSkips: SourceSkips.ExpoConfigVersions | SourceSkips.ExpoConfigRuntimeVersionIfString
    | SourceSkips.ExpoConfigExtraSection | SourceSkips.GitIgnore | SourceSkips.PackageJsonScriptsAll,
  ignorePaths: ['native-fingerprint.json', '**/debug.keystore', '**/local.properties', '**/.kotlin/**'],
  fileHookTransform: createNativeSourceTransform(),
});
const contractFile = path.join(appRoot, 'native-fingerprint.json');
const nativeRelease = JSON.parse(readFileSync(path.join(appRoot, 'native-release.json'), 'utf8'));
const result = { schemaVersion: 1, nativeVersion: nativeRelease.version, runtimeVersion: nativeRelease.runtimeVersion,
  hash: fingerprint.hash, sources: fingerprint.sources };
if (process.argv.includes('--record')) {
  writeFileSync(contractFile, `${JSON.stringify(result, null, 2)}\n`);
  console.log(`NATIVE_FINGERPRINT_RECORDED=${fingerprint.hash}`);
} else {
  const expected = JSON.parse(readFileSync(contractFile, 'utf8'));
  if (expected.nativeVersion !== result.nativeVersion || expected.runtimeVersion !== result.runtimeVersion || expected.hash !== result.hash) {
    const changed = fingerprint.sources.filter(source => !expected.sources.some(previous => previous.id === source.id && previous.filePath === source.filePath && previous.hash === source.hash));
    console.error(`原生兼容指纹变化，需要更新原生基础包身份并重新记录指纹：${JSON.stringify(changed.map(source => source.filePath || source.id))}`);
    process.exitCode = 1;
  } else console.log(`NATIVE_FINGERPRINT=${fingerprint.hash}`);
}
