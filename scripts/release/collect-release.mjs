import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const json = file => JSON.parse(readFileSync(file, 'utf8'));
export function validateNativeArtifact(manifest, contract, fingerprint, sourceSha, businessVersion, bytes) {
  if (manifest.sourceSha !== sourceSha || manifest.businessVersion !== businessVersion
      || manifest.version !== contract.version || manifest.versionCode !== contract.versionCode
      || manifest.runtimeVersion !== contract.runtimeVersion || manifest.applicationId !== contract.applicationId
      || manifest.nativeFingerprint !== fingerprint || !/^[a-f0-9]{40}$/.test(manifest.artifactSourceSha)
      || manifest.size !== bytes.length || manifest.sha256 !== createHash('sha256').update(bytes).digest('hex')) {
    throw new Error('APK 与源码、业务版本、原生基础包或摘要契约不一致。');
  }
}

async function main() {
  const repository = process.env.GITHUB_REPOSITORY;
  const sourceSha = process.env.SOURCE_SHA;
  const gh = args => execFileSync('gh', args, { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  const api = resource => JSON.parse(gh(['api', `repos/${repository}/${resource}`]));
  if (!repository || !/^[a-f0-9]{40}$/.test(sourceSha)) throw new Error('缺少可信仓库与完整源码 SHA。');
  if (process.env.GITHUB_EVENT_NAME === 'workflow_run' && api('commits/main').sha !== sourceSha) {
    writeFileSync(process.env.GITHUB_OUTPUT, 'ready=false\n', { flag: 'a' }); console.log('STALE_BUILD_SKIPPED'); return;
  }
  if (process.env.GITHUB_EVENT_NAME !== 'workflow_run' && !['behind', 'identical'].includes(api(`compare/main...${sourceSha}`).status)) throw new Error('源码不在 main 提交历史中。');
  execFileSync(process.execPath, ['scripts/verify-version-contract.mjs'], { stdio: 'inherit' });
  const workflows = ['backend', 'frontend', 'android', 'lite', 'ota'];
  let builds;
  for (let attempt = 0; attempt < 120; attempt++) {
    builds = Object.fromEntries(workflows.map(component => {
      const runs = api(`actions/workflows/build-${component}.yml/runs?head_sha=${sourceSha}&per_page=20`).workflow_runs
        .filter(run => run.event === 'workflow_run' && run.head_sha === sourceSha && run.head_repository.full_name === repository);
      return [component, runs.find(run => run.conclusion === 'success') || runs[0]];
    }));
    if (workflows.every(component => builds[component]?.conclusion === 'success')) break;
    if (workflows.some(component => ['failure', 'cancelled', 'timed_out', 'skipped'].includes(builds[component]?.conclusion))) throw new Error(`构建失败：${JSON.stringify(Object.fromEntries(workflows.map(component => [component, builds[component]?.conclusion || 'missing'])))}`);
    console.log(`BUILD_WAIT ${JSON.stringify(Object.fromEntries(workflows.map(component => [component, builds[component]?.status || 'missing'])))}`);
    await new Promise(resolve => setTimeout(resolve, 10_000));
  }
  if (!workflows.every(component => builds[component]?.conclusion === 'success')) throw new Error('等待构建超时。');
  if (process.env.GITHUB_EVENT_NAME === 'workflow_run' && api('commits/main').sha !== sourceSha) {
    writeFileSync(process.env.GITHUB_OUTPUT, 'ready=false\n', { flag: 'a' }); return;
  }
  const version = json('e-report/package.json').version;
  const images = {};
  const checksums = {};
  for (const component of workflows) {
    const directory = `.release-artifacts/${component}`;
    mkdirSync(directory, { recursive: true });
    gh(['run', 'download', String(builds[component].id), '-R', repository, '-n',
      ['backend', 'frontend'].includes(component) ? `e-report-${component}-manifest` : `e-report-${component}-${sourceSha}`, '-D', directory]);
    const manifest = json(path.join(directory, 'release-manifest.json'));
    if (manifest.sourceSha !== sourceSha) throw new Error(`${component} 制品源码 SHA 不一致。`);
    if (['backend', 'frontend'].includes(component)) {
      const prefix = `ghcr.io/${repository.split('/')[0].toLowerCase()}/e-report-${component}@`;
      if (manifest.version !== version || !manifest.image.startsWith(prefix) || !/^sha256:[a-f0-9]{64}$/.test(manifest.image.slice(prefix.length))) throw new Error(`${component} 镜像契约不一致。`);
      images[component] = manifest.image;
    } else if (component !== 'ota') {
      const app = component === 'android' ? 'e-report-app' : 'e-report-lite';
      const native = json(`${app}/native-release.json`); const fingerprint = json(`${app}/native-fingerprint.json`).hash;
      validateNativeArtifact(manifest, native, fingerprint, sourceSha, version, readFileSync(path.join(directory, 'app-release.apk')));
      if (manifest.edition !== (component === 'android' ? 'NATIVE' : 'LITE')) throw new Error('APK 客户端身份不一致。');
      checksums[component] = manifest.sha256;
    } else {
      if (manifest.version !== version || manifest.runtimeVersion !== json('e-report-app/native-release.json').runtimeVersion || !Array.isArray(manifest.files)) throw new Error('OTA 导出版本或运行时不一致。');
      for (const file of manifest.files) {
        const root = path.resolve(directory); const resource = path.resolve(root, file.path);
        if (!resource.startsWith(`${root}${path.sep}`)) throw new Error('OTA 导出资源路径越界。');
        const bytes = readFileSync(resource);
        if (bytes.length !== file.bytes || createHash('sha256').update(bytes).digest('hex') !== file.sha256) throw new Error('OTA 导出资源校验失败。');
      }
    }
  }
  const outputs = { ready: 'true', source_sha: sourceSha, backend_image: images.backend, frontend_image: images.frontend,
    android_run_id: builds.android.id, lite_run_id: builds.lite.id, ota_run_id: builds.ota.id,
    android_sha256: checksums.android, lite_sha256: checksums.lite };
  writeFileSync(process.env.GITHUB_OUTPUT, Object.entries(outputs).map(([key, value]) => `${key}=${value}\n`).join(''), { flag: 'a' });
  console.log(`RELEASE_ARTIFACTS_READY version=${version} source=${sourceSha}`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
