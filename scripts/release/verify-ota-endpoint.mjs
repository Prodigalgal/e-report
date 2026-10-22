import { createHash, verify, X509Certificate } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateOtaIdentity(manifest, expected) {
  const metadata = expected.kind === 'ROLLBACK' ? manifest.parameters?.metadata : manifest.metadata;
  for (const key of ['environment', 'runtimeVersion', 'version', 'sourceSha']) {
    if (!expected[key] || metadata?.[key] !== expected[key]) throw new Error(`OTA ${key} 与验收候选不一致。`);
  }
  if (expected.id && metadata?.id !== expected.id) throw new Error('OTA 标识与归档不一致。');
  if (expected.kind === 'ROLLBACK' ? manifest.type !== 'rollBackToEmbedded'
    : manifest.id !== metadata.id || manifest.runtimeVersion !== expected.runtimeVersion) throw new Error('OTA 类型或运行时无效。');
}

export async function verifyOtaEndpoint({ apiBaseUrl, expected: descriptor, expectedPayload, fetchImpl = fetch }) {
  const publicKey = new X509Certificate(readFileSync(new URL('../../e-report-app/ota/certificate.crt', import.meta.url))).publicKey;
  const api = apiBaseUrl.replace(/\/+$/, '');
  const response = await fetchImpl(`${api}/app-updates/ota`, {
    headers: { 'expo-protocol-version': '1', 'expo-platform': 'android', 'expo-runtime-version': descriptor.runtimeVersion,
      'expo-expect-signature': 'sig, keyid="main", alg="rsa-v1_5-sha256"', Accept: 'multipart/mixed', 'e-report-environment': descriptor.environment },
    signal: AbortSignal.timeout(30000),
  });
  if (response.status !== 200 || response.headers.get('expo-protocol-version') !== '1') throw new Error(`OTA 协议响应无效：${response.status}`);
  const boundary = /boundary="?([^";]+)"?/.exec(response.headers.get('content-type') || '')?.[1];
  if (!boundary || !response.headers.get('expo-manifest-filters')?.includes(`environment="${descriptor.environment}"`)) throw new Error('OTA 响应边界或环境过滤无效。');
  const body = await response.text();
  if (Buffer.byteLength(body) > 3 * 1024 * 1024) throw new Error('OTA 响应过大。');
  const part = body.split(`--${boundary}`).find(candidate => candidate.includes(`name="${descriptor.kind === 'ROLLBACK' ? 'directive' : 'manifest'}"`));
  if (!part) throw new Error('OTA 响应缺少已签名内容。');
  const separator = part.indexOf('\r\n\r\n');
  const headers = part.slice(0, separator);
  const signature = /expo-signature:\s*sig="([A-Za-z0-9+/=]+)"/i.exec(headers)?.[1];
  const payload = Buffer.from(part.slice(separator + 4).replace(/\r\n$/, ''));
  if (!signature || (expectedPayload && !payload.equals(expectedPayload)) || !verify('RSA-SHA256', payload, publicKey, Buffer.from(signature, 'base64'))) throw new Error('线上 OTA 签名或不可变清单校验失败。');
  const manifest = JSON.parse(payload);
  validateOtaIdentity(manifest, descriptor);
  const assets = descriptor.kind === 'ROLLBACK' ? [] : [manifest.launchAsset, ...manifest.assets];
  const queue = [...new Map(assets.map(asset => [asset.url, asset])).values()];
  let verifiedBytes = 0;
  async function worker() {
    while (queue.length) {
      const asset = queue.shift();
      const assetPrefix = `${api}/app-updates/ota/assets/${descriptor.runtimeVersion}/`;
      if (!asset.url.startsWith(assetPrefix) || !/^[a-f0-9]{64}$/.test(asset.url.slice(assetPrefix.length))) throw new Error('OTA 资源地址不属于目标环境。');
      let success = false;
      for (let attempt = 0; attempt < 4; attempt++) {
        try {
          const resource = await fetchImpl(asset.url, { signal: AbortSignal.timeout(120000) });
          if (!resource.ok) throw new Error(`OTA 资源下载失败 ${resource.status}`);
          let size = 0; const digest = createHash('sha256');
          for await (const chunk of resource.body) { size += chunk.length; if (size > 64 * 1024 * 1024) throw new Error('OTA 单资源体积超出限制。'); digest.update(chunk); }
          if (digest.digest('base64url') !== asset.hash) throw new Error('线上 OTA 资源摘要不一致。');
          verifiedBytes += size; success = true; break;
        } catch (error) { if (attempt === 3) throw error; await new Promise(resolve => setTimeout(resolve, (attempt + 1) * 2000)); }
      }
      if (!success) throw new Error('OTA 资源验证未完成。');
    }
  }
  await Promise.all(Array.from({ length: Math.min(4, queue.length) }, worker));
  console.log(`OTA_ENDPOINT_VERIFIED id=${manifest.metadata?.id || descriptor.id} version=${descriptor.version} environment=${descriptor.environment} assets=${assets.length} bytes=${verifiedBytes}`);
}

async function main() {
  const appRequire = createRequire(new URL('../../e-report-app/package.json', import.meta.url));
  const { unzipSync } = appRequire('fflate');
  const options = Object.fromEntries(Array.from({ length: (process.argv.length - 2) / 2 }, (_, index) => process.argv.slice(2 + index * 2, 4 + index * 2)));
  const archive = unzipSync(readFileSync(options['--archive']));
  await verifyOtaEndpoint({ apiBaseUrl: options['--api-base-url'], expected: JSON.parse(Buffer.from(archive['release.json']).toString('utf8')),
    expectedPayload: Buffer.from(archive['manifest.json']) });
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
