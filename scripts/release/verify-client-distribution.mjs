import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const apkTypes = new Set(['application/vnd.android.package-archive', 'application/octet-stream']);

async function download(url, options, fetchImpl) {
  for (let redirects = 0; redirects <= 5; redirects++) {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password) throw new Error('APK 分发必须使用无凭据的 HTTPS 地址。');
    const response = await fetchImpl(url, { ...options, redirect: 'manual', signal: AbortSignal.timeout(120000) });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get('location');
      await response.body?.cancel();
      if (!location) throw new Error('APK 分发跳转缺少地址。');
      url = new URL(location, url).href;
      continue;
    }
    return response;
  }
  throw new Error('APK 分发跳转过多。');
}

export async function verifyPublishedApk(published, { fetchImpl = fetch } = {}) {
  const size = published.apkSize;
  if (!Number.isSafeInteger(size) || size <= 0 || size > 300 * 1024 * 1024 || !/^[a-f0-9]{64}$/.test(published.sha256 || '')) {
    throw new Error('APK 分发元数据无效。');
  }
  const head = await download(published.apkUrl, { method: 'HEAD' }, fetchImpl);
  if (head.status !== 200 || !apkTypes.has(head.headers.get('content-type')?.split(';')[0])
      || Number(head.headers.get('content-length')) !== size) throw new Error('APK 下载入口没有返回候选安装包，可能错误地返回了网页。');

  const end = Math.min(size, 1024) - 1;
  const range = await download(published.apkUrl, { headers: { Range: `bytes=0-${end}` } }, fetchImpl);
  if (range.status !== 206 || range.headers.get('content-range') !== `bytes 0-${end}/${size}`) {
    await range.body?.cancel();
    throw new Error('APK 分片下载响应无效。');
  }
  let rangeSize = 0;
  for await (const chunk of range.body) {
    rangeSize += chunk.length;
    if (rangeSize > end + 1) throw new Error('APK 分片大小超过请求范围。');
  }
  if (rangeSize !== end + 1) throw new Error('APK 分片大小不完整。');

  const response = await download(published.apkUrl, {}, fetchImpl);
  if (response.status !== 200) throw new Error(`APK 下载失败 ${response.status}`);
  const hash = createHash('sha256');
  let downloaded = 0;
  for await (const chunk of response.body) {
    downloaded += chunk.length;
    if (downloaded > size) throw new Error('APK 下载体积超过候选大小。');
    hash.update(chunk);
  }
  if (downloaded !== size || hash.digest('hex') !== published.sha256) throw new Error('APK 真实下载大小或摘要与不可变候选不一致。');
  console.log(`APK_DISTRIBUTION_VERIFIED edition=${published.edition} version=${published.versionName} bytes=${downloaded} sha256=${published.sha256}`);
}

async function main() {
  const options = Object.fromEntries(Array.from({ length: (process.argv.length - 2) / 2 }, (_, index) => process.argv.slice(2 + index * 2, 4 + index * 2)));
  const api = new URL(options['--api-base-url']);
  if (api.protocol !== 'https:' || api.username || api.password || !['NATIVE', 'LITE'].includes(options['--edition'])) throw new Error('目标环境或客户端类型无效。');
  const response = await fetch(`${api.href.replace(/\/$/, '')}/app-updates/latest?edition=${options['--edition']}&platform=ANDROID&channel=stable`, { signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw new Error(`发布中心查询失败 ${response.status}`);
  const published = await response.json();
  if (published.edition !== options['--edition'] || published.sha256 !== options['--expected-sha256'] || published.apkSize !== Number(options['--expected-size'])) throw new Error('发布中心与原始候选 APK 不一致。');
  await verifyPublishedApk(published);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
