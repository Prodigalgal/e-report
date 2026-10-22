import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateNativeModuleLocks(resolution, lockfile) {
  const errors = [];
  let publications = 0;
  const lines = lockfile.split(/\r?\n/);
  for (const module of resolution.modules ?? []) {
    for (const project of module.projects ?? []) {
      const publication = project.publication;
      if (!publication) continue;
      publications++;
      const prefix = `${publication.groupId}:${publication.artifactId}:`;
      const coordinate = `${prefix}${publication.version}`;
      const locks = lines.filter(line => line.startsWith(prefix));
      if (!locks.some(line => line.startsWith(`${coordinate}=`)
          && line.slice(line.indexOf('=') + 1).split(',').includes('releaseRuntimeClasspath'))) {
        errors.push(`${module.packageName}: releaseRuntimeClasspath 缺少当前原生组件 ${coordinate}`);
      }
      for (const lock of locks) {
        if (!lock.startsWith(`${coordinate}=`)) {
          errors.push(`${module.packageName}: 原生依赖锁仍引用其他版本 ${lock.split('=')[0]}`);
        }
      }
    }
  }
  if (publications === 0) errors.push('Expo autolinking 没有返回任何可校验的原生组件。');
  return errors;
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const require = createRequire(import.meta.url);
  const packageRoot = dirname(require.resolve('expo-modules-autolinking/package.json'));
  const output = execFileSync(process.execPath, [
    resolve(packageRoot, 'bin/expo-modules-autolinking.js'), 'resolve', '--platform', 'android', '--json',
  ], { cwd: appRoot, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024 });
  const resolution = JSON.parse(output);
  const lockfile = readFileSync(resolve(appRoot, 'android/app/gradle.lockfile'), 'utf8');
  const errors = validateNativeModuleLocks(resolution, lockfile);
  if (errors.length > 0) {
    process.stderr.write(`${errors.join('\n')}\n请使用 Gradle 同步原生依赖锁和校验元数据后再构建。\n`);
    process.exitCode = 1;
  } else {
    process.stdout.write('ANDROID_NATIVE_MODULE_LOCKS=PASS\n');
  }
}
