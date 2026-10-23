import { appendFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

async function readEndpoint(fetchResponse, url, timeoutMs) {
  try {
    const response = await fetchResponse(url, {
      headers: { 'Cache-Control': 'no-cache' }, signal: AbortSignal.timeout(timeoutMs),
    });
    if (!response.ok) return { status: response.status, body: null };
    return { status: response.status, body: await response.json() };
  } catch {
    return { status: 'unavailable', body: null };
  }
}

export async function waitForTestDeployment({
  baseUrl, sourceSha, timeoutMs = 75 * 60_000, intervalMs = 10_000,
  requestTimeoutMs = 8_000, fetchResponse = fetch, sleep = delay,
  now = () => performance.now(), log = console.log,
}) {
  const base = new URL(baseUrl);
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash
      || base.pathname !== '/' || !/^[a-f0-9]{40}$/.test(sourceSha)
      || ![timeoutMs, intervalMs, requestTimeoutMs].every(value => Number.isSafeInteger(value) && value > 0)) {
    throw new Error('Invalid test deployment verification parameters');
  }
  const started = now();
  const deadline = started + timeoutMs;
  let previous;
  let lastLoggedAt = -Infinity;
  let observed;
  while (now() < deadline) {
    const requestTimeout = Math.max(1, Math.min(requestTimeoutMs, Math.ceil(deadline - now())));
    const routes = [`/release.json?source=${sourceSha}`, '/api/auth/csrf',
      '/api/app-updates/latest?edition=NATIVE', '/api/app-updates/latest?edition=LITE'];
    const [release, csrf, native, lite] = await Promise.all(routes.map(route =>
      readEndpoint(fetchResponse, new URL(route, base), requestTimeout)));
    observed = { sourceSha: release.body?.sourceSha ?? null, releaseStatus: release.status,
      csrfStatus: csrf.status, nativeStatus: native.status, liteStatus: lite.status,
      native: native.body?.edition ?? null, lite: lite.body?.edition ?? null };
    if (release.status === 200 && observed.sourceSha === sourceSha && csrf.status === 200
        && typeof csrf.body?.token === 'string' && csrf.body.token.length > 0
        && native.status === 200 && observed.native === 'NATIVE' && lite.status === 200 && observed.lite === 'LITE') {
      log(`RELEASE_SMOKE_OK ${JSON.stringify({ ...observed, waitedSeconds: Math.ceil((now() - started) / 1000) })}`);
      return { sourceSha };
    }
    const state = JSON.stringify(observed);
    if (state !== previous || now() - lastLoggedAt >= 60_000) {
      log(`RELEASE_SMOKE_WAIT ${state} elapsedSeconds=${Math.floor((now() - started) / 1000)}`);
      previous = state;
      lastLoggedAt = now();
    }
    const remaining = deadline - now();
    if (remaining > 0) await sleep(Math.min(intervalMs, remaining));
  }
  throw new Error(`RELEASE_SMOKE_TIMEOUT expected=${sourceSha} observed=${JSON.stringify(observed)}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await waitForTestDeployment({ baseUrl: 'https://e-report-test.fantong.eu.org:60443', sourceSha: process.env.SOURCE_SHA });
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, 'ready=true\n');
}
