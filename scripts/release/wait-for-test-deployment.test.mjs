import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { waitForTestDeployment } from './wait-for-test-deployment.mjs';

const sourceSha = 'a'.repeat(40);
const baseUrl = 'https://deployment.example.test';
function probeOptions(response) {
  let elapsed = 0;
  const logs = [];
  return {
    baseUrl, sourceSha, timeoutMs: 1000, intervalMs: 100, now: () => elapsed,
    sleep: async milliseconds => { elapsed += milliseconds; }, log: message => logs.push(message),
    fetchResponse: async (url, options) => response(url, options, elapsed), logs,
  };
}
function ready(url) {
  const body = url.pathname === '/release.json' ? { sourceSha }
    : url.pathname.endsWith('/csrf') ? { token: 'synthetic-csrf-token' } : { edition: url.searchParams.get('edition') };
  return new Response(JSON.stringify(body), { status: 200 });
}

test('waits through unavailable services and a previous release before accepting all four endpoints', async () => {
  const options = probeOptions((url, request, elapsed) => {
    assert.ok(request.signal instanceof AbortSignal);
    if (elapsed < 200) return new Response('', { status: 503 });
    if (elapsed < 400 && url.pathname === '/release.json') return new Response(JSON.stringify({ sourceSha: 'b'.repeat(40) }));
    return ready(url);
  });
  assert.deepEqual(await waitForTestDeployment(options), { sourceSha });
  assert.ok(options.logs.some(message => message.includes('503')));
  assert.ok(options.logs.at(-1).startsWith('RELEASE_SMOKE_OK'));
  assert.ok(options.logs.every(message => !message.includes('synthetic-csrf-token')));
});

test('uses the complete bounded cold-pull window instead of failing after the earlier ten-minute limit', async () => {
  const options = probeOptions((url, request, elapsed) => elapsed < 45 * 60_000 ? new Response('', { status: 503 }) : ready(url));
  options.timeoutMs = 75 * 60_000;
  options.intervalMs = 60_000;
  assert.deepEqual(await waitForTestDeployment(options), { sourceSha });
});

test('rejects wrong source, missing CSRF JSON, wrong client identity, malformed JSON and network failures', async () => {
  for (const response of [
    url => url.pathname === '/release.json' ? new Response(JSON.stringify({ sourceSha: 'b'.repeat(40) })) : ready(url),
    url => url.pathname.endsWith('/csrf') ? new Response('{}') : ready(url),
    url => url.searchParams.get('edition') === 'LITE' ? new Response('{"edition":"NATIVE"}') : ready(url),
    () => new Response('<html>gateway</html>'),
    () => { throw new TypeError('network unavailable'); },
  ]) {
    const options = probeOptions(response);
    await assert.rejects(waitForTestDeployment(options), /RELEASE_SMOKE_TIMEOUT/);
    assert.ok(!options.logs.some(message => message.startsWith('RELEASE_SMOKE_OK')));
  }
});

test('rejects invalid source and unbounded polling parameters before making requests', async () => {
  for (const overrides of [{ sourceSha: 'main' }, { baseUrl: 'http://deployment.example.test' },
    { timeoutMs: 0 }, { intervalMs: -1 }, { requestTimeoutMs: Infinity }]) {
    const options = probeOptions(() => { throw new Error('must not fetch'); });
    await assert.rejects(waitForTestDeployment({ ...options, ...overrides }), /Invalid test deployment/);
  }
});

test('aborts stalled HTTP requests and stops at the deployment deadline', async () => {
  await assert.rejects(waitForTestDeployment({
    baseUrl, sourceSha, timeoutMs: 50, intervalMs: 5, requestTimeoutMs: 10, log: () => {},
    fetchResponse: async (url, { signal }) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true });
      // Keep a real pending request alive until AbortSignal cancels it.
      const timer = setTimeout(() => resolve(ready(url)), 500);
      signal.addEventListener('abort', () => clearTimeout(timer), { once: true });
    }),
  }), /RELEASE_SMOKE_TIMEOUT/);
});

test('requires deployment verification before client publishing without holding the GitOps write lock', () => {
  const workflow = readFileSync(new URL('../../.github/workflows/release-gitops.yml', import.meta.url), 'utf8').replaceAll('\r\n', '\n');
  const job = name => workflow.split(`\n  ${name}:\n`)[1]?.split(/\n  [a-z][a-z-]+:\n/)[0];
  const promote = job('promote-test');
  const verify = job('verify-test');
  const publish = job('publish-test-android');
  assert.match(workflow, /workflows: \[构建 Android APK\]/);
  assert.match(promote, /group: e-report-config-main/);
  assert.doesNotMatch(promote, /wait_for_release|wait-for-test-deployment/);
  assert.match(verify, /needs: \[collect, promote-test\]/);
  assert.doesNotMatch(verify, /concurrency:|group: e-report-config-main/);
  assert.match(verify, /ref: \$\{\{ github.workflow_sha \}\}/);
  assert.match(verify, /run: node scripts\/release\/wait-for-test-deployment.mjs/);
  assert.ok(Number(verify.match(/timeout-minutes: (\d+)/)?.[1]) > 75);
  assert.match(publish, /needs: \[collect, verify-test\]/);
  assert.match(publish, /needs.verify-test.outputs.ready == 'true'/);
  assert.match(job('promote-production'), /github.event_name == 'workflow_dispatch' && inputs.environment == 'production'/);
});
