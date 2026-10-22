import { createHash, sign, verify, X509Certificate } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { zipSync } from 'fflate';
import { calculateAndroidVersionCode, verifySourceVersionContract } from './verify-version-contract.mjs';

const json = file => JSON.parse(readFileSync(file, 'utf8'));
const mimeTypes = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif',
  svg: 'image/svg+xml', ttf: 'font/ttf', otf: 'font/otf', woff: 'font/woff', woff2: 'font/woff2', json: 'application/json',
  mp4: 'video/mp4', mp3: 'audio/mpeg', wasm: 'application/wasm', xml: 'application/xml' };
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');

function updateId(identity) {
  const bytes = createHash('sha1').update(Buffer.from('c17fba75c4dc45d89a20511fa3666119', 'hex')).update(identity).digest().subarray(0, 16);
  bytes[6] = (bytes[6] & 0x0f) | 0x50; bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const value = bytes.toString('hex');
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}

function requireApiUrl(value) {
  const parsed = new URL(value);
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash) throw new Error('OTA API 地址必须是无凭据的 HTTPS 地址。');
  return parsed.href.replace(/\/+$/, '');
}

export function createOtaArchive({ appRoot, exportRoot, environment, apiBaseUrl, sourceSha, createdAt, privateKey, kind = 'UPDATE' }) {
  if (!['test', 'production'].includes(environment) || !['UPDATE', 'ROLLBACK'].includes(kind)) throw new Error('OTA 环境或更新类型无效。');
  if (!/^[a-f0-9]{40}$/.test(sourceSha)) throw new Error('OTA 需要完整源码 SHA。');
  const timestamp = new Date(createdAt);
  if (!Number.isFinite(timestamp.getTime())) throw new Error('OTA 需要固定的源码创建时间。');
  const contract = verifySourceVersionContract({ appRoot });
  const version = json(path.join(appRoot, 'package.json')).version;
  calculateAndroidVersionCode(version);
  const baseUrl = requireApiUrl(apiBaseUrl);
  const id = updateId(`${sourceSha}\n${environment}\n${contract.runtimeVersion}\n${version}\n${kind}\n${timestamp.toISOString()}`);
  const descriptor = { schemaVersion: 1, id, environment, runtimeVersion: contract.runtimeVersion, version, sourceSha, kind };
  const metadata = { id, environment, runtimeVersion: contract.runtimeVersion, version, sourceSha };
  const files = {};
  let expandedBytes = 0;
  function asset(relativePath, extension, launch) {
    const root = path.resolve(exportRoot);
    const file = path.resolve(root, relativePath);
    if (!file.startsWith(`${root}${path.sep}`)) throw new Error('OTA 导出资源路径越界。');
    const bytes = readFileSync(file);
    expandedBytes += bytes.length;
    if (expandedBytes > 150 * 1024 * 1024 || bytes.length > 64 * 1024 * 1024) throw new Error('OTA 导出资源超过体积限制。');
    const hex = sha256(bytes);
    files[`assets/${hex}`] = bytes;
    const ext = launch ? 'bundle' : String(extension).replace(/^\./, '').toLowerCase();
    if (!/^[a-z0-9]{1,16}$/.test(ext)) throw new Error('OTA 资源扩展名无效。');
    return { hash: createHash('sha256').update(bytes).digest('base64url'), key: createHash('md5').update(bytes).digest('hex'),
      fileExtension: `.${ext}`, contentType: launch ? 'application/javascript' : (mimeTypes[ext] || 'application/octet-stream'),
      url: `${baseUrl}/app-updates/ota/assets/${contract.runtimeVersion}/${hex}` };
  }
  let manifest;
  if (kind === 'ROLLBACK') manifest = { type: 'rollBackToEmbedded', parameters: { commitTime: timestamp.toISOString(), metadata } };
  else {
    const exported = json(path.join(exportRoot, 'metadata.json'))?.fileMetadata?.android;
    if (!exported?.bundle || !Array.isArray(exported.assets) || exported.assets.length > 1900) throw new Error('Expo Android 导出清单不完整。');
    const expoConfig = json(path.join(exportRoot, 'expoConfig.json'));
    if (expoConfig.version !== version || expoConfig.runtimeVersion !== contract.runtimeVersion) throw new Error('Expo 导出配置与业务版本或原生运行时不一致。');
    manifest = { id, createdAt: timestamp.toISOString(), runtimeVersion: contract.runtimeVersion,
      launchAsset: asset(exported.bundle, null, true), assets: exported.assets.map(entry => asset(entry.path, entry.ext, false)),
      metadata, extra: { expoClient: expoConfig } };
  }
  const payload = JSON.stringify(manifest);
  const signature = sign('RSA-SHA256', Buffer.from(payload), privateKey).toString('base64');
  const certificate = new X509Certificate(readFileSync(path.join(appRoot, 'ota/certificate.crt')));
  if (!verify('RSA-SHA256', Buffer.from(payload), certificate.publicKey, Buffer.from(signature, 'base64'))) throw new Error('OTA 私钥与 APK 内置公钥不匹配。');
  files['manifest.json'] = Buffer.from(payload);
  files['signature.txt'] = Buffer.from(signature);
  files['release.json'] = Buffer.from(JSON.stringify(descriptor));
  const archive = zipSync(files, { level: 6, mtime: new Date('2020-01-01T00:00:00Z') });
  if (archive.length > 50 * 1024 * 1024) throw new Error('OTA ZIP 超过 50MB，不能通过发布入口上传。');
  return { archive, descriptor, payload, signature, sha256: sha256(archive) };
}

function main() {
  const options = Object.fromEntries(Array.from({ length: (process.argv.length - 2) / 2 }, (_, index) => process.argv.slice(2 + index * 2, 4 + index * 2)));
  const appRoot = path.resolve(options['--app-root'] || path.join(path.dirname(fileURLToPath(import.meta.url)), '..'));
  const privateKey = options['--private-key-file'] ? readFileSync(options['--private-key-file'], 'utf8') : process.env.EXPO_OTA_PRIVATE_KEY;
  if (!privateKey) throw new Error('缺少 OTA 签名私钥。');
  const result = createOtaArchive({ appRoot, exportRoot: path.resolve(options['--export-root'] || path.join(appRoot, 'dist/ota')),
    environment: options['--environment'], apiBaseUrl: options['--api-base-url'], sourceSha: options['--source-sha'],
    createdAt: options['--created-at'], kind: options['--kind'] || 'UPDATE', privateKey });
  const destination = path.resolve(options['--output'] || 'output/ota-update.zip');
  mkdirSync(path.dirname(destination), { recursive: true });
  writeFileSync(destination, result.archive);
  console.log(`OTA_ARCHIVE=${JSON.stringify({ ...result.descriptor, sha256: result.sha256, bytes: result.archive.length })}`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
