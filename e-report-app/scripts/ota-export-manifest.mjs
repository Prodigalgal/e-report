import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function describeOtaExport(exportRoot, appRoot, sourceSha) {
  if (!/^[a-f0-9]{40}$/.test(sourceSha)) throw new Error('OTA 需要完整源码 SHA。');
  const root = path.resolve(exportRoot);
  const metadata = JSON.parse(readFileSync(path.join(root, 'metadata.json'), 'utf8'));
  const platform = metadata.fileMetadata?.android;
  const native = JSON.parse(readFileSync(path.join(appRoot, 'native-runtime.json'), 'utf8'));
  const businessVersion = JSON.parse(readFileSync(path.join(appRoot, 'package.json'), 'utf8')).version;
  if (!platform?.bundle || !Array.isArray(platform.assets)) throw new Error('OTA Android 导出缺少资源清单。');
  const paths = new Set(['metadata.json', 'expoConfig.json', platform.bundle, ...platform.assets.map(asset => asset.path)]);
  const files = [...paths].sort().map(relative => {
    const file = path.resolve(root, relative);
    if (!file.startsWith(`${root}${path.sep}`)) throw new Error('OTA 导出资源路径越界。');
    const bytes = readFileSync(file);
    return { path: relative, bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') };
  });
  return { schemaVersion: 1, sourceSha, version: businessVersion, runtimeVersion: native.runtimeVersion,
    nativeVersion: native.version, nativeVersionCode: native.versionCode, files };
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const options = Object.fromEntries(Array.from({ length: (process.argv.length - 2) / 2 }, (_, index) => process.argv.slice(2 + index * 2, 4 + index * 2)));
  const appRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const exportRoot = path.resolve(options['--export-root']);
  const manifest = describeOtaExport(exportRoot, appRoot, options['--source-sha']);
  const file = path.join(exportRoot, 'release-manifest.json');
  if (options['--verify'] === 'true') {
    if (readFileSync(file, 'utf8') !== `${JSON.stringify(manifest, null, 2)}\n`) throw new Error('OTA 资源与原始导出清单不一致。');
    console.log(`OTA_EXPORT_VERIFIED source=${manifest.sourceSha} version=${manifest.version} runtime=${manifest.runtimeVersion}`);
  } else { writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`); console.log(`OTA_EXPORT_READY files=${manifest.files.length}`); }
}
