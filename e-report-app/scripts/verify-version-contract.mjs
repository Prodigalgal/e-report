#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { X509Certificate } from 'node:crypto';
import { createRequire } from 'node:module';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    throw new Error(`${label} 不一致：期望 ${expected}，实际 ${actual}。`);
  }
}

export function calculateAndroidVersionCode(versionName) {
  const match = /^(?<major>[1-9]\d*)\.(?<minor>0|[1-9]\d*)\.(?<patch>0|[1-9]\d*)$/.exec(versionName);
  if (!match?.groups) {
    throw new Error(`versionName 必须使用 x.y.z 格式：${versionName}。`);
  }
  const major = Number(match.groups.major);
  const minor = Number(match.groups.minor);
  const patch = Number(match.groups.patch);
  if (major < 1 || minor >= 1000 || patch >= 1000) {
    throw new Error('versionName 必须满足 x >= 1 且 0 <= y,z < 1000。');
  }
  const versionCode = ((major - 1) * 1_000_000) + (minor * 1_000) + patch;
  if (!Number.isSafeInteger(versionCode) || versionCode <= 0 || versionCode > 2_147_483_647) {
    throw new Error('versionName 超出 Android 构建号可用范围。');
  }
  return versionCode;
}

export function readNativeApkContract(appRoot) {
  const { version } = readJson(path.join(appRoot, 'package.json'));
  const runtime = readJson(path.join(appRoot, 'native-runtime.json'));
  return { version, versionCode: calculateAndroidVersionCode(version),
    runtimeVersion: runtime.runtimeVersion, applicationId: runtime.applicationId };
}

function parseGradleVersion(buildGradle) {
  const versionCodeMatch = buildGradle.match(/^\s*versionCode\s+(\d+)\s*$/m);
  const versionNameMatch = buildGradle.match(/^\s*versionName\s+["']([^"']+)["']\s*$/m);
  if (!versionCodeMatch || !versionNameMatch) {
    throw new Error('无法从 android/app/build.gradle 读取 versionCode/versionName。');
  }
  return {
    versionCode: Number(versionCodeMatch[1]),
    versionName: versionNameMatch[1],
  };
}

export function verifySourceVersionContract({
  appRoot,
  expectedVersionCode,
  expectedVersionName,
  expectedRuntimeVersion,
}) {
  const packageJson = readJson(path.join(appRoot, 'package.json'));
  const packageLock = readJson(path.join(appRoot, 'package-lock.json'));
  const appJson = readJson(path.join(appRoot, 'app.json'));
  const nativeRuntime = readJson(path.join(appRoot, 'native-runtime.json'));
  const gradleVersion = parseGradleVersion(
    readFileSync(path.join(appRoot, 'android', 'app', 'build.gradle'), 'utf8'),
  );

  const versionCode = appJson.expo?.android?.versionCode;
  const businessVersion = appJson.expo?.version;
  const versionName = businessVersion;
  const runtimeVersion = appJson.expo?.runtimeVersion;
  if (!Number.isSafeInteger(versionCode) || versionCode <= 0) {
    throw new Error('app.json expo.android.versionCode 必须是正整数。');
  }
  const contractVersionCode = calculateAndroidVersionCode(versionName);
  const runtimeCode = calculateAndroidVersionCode(nativeRuntime.version);
  const contractRuntimeVersion = `android-${nativeRuntime.version}-native${runtimeCode}`;

  assertEqual(versionCode, contractVersionCode, 'app.json expo.android.versionCode');
  assertEqual(nativeRuntime.versionCode, runtimeCode, 'native-runtime.json versionCode');
  assertEqual(nativeRuntime.runtimeVersion, contractRuntimeVersion, 'native-runtime.json runtimeVersion');
  assertEqual(nativeRuntime.applicationId, appJson.expo?.android?.package, '原生应用包名');
  assertEqual(runtimeVersion, contractRuntimeVersion, 'app.json expo.runtimeVersion');
  if (contractVersionCode < runtimeCode) {
    throw new Error('安装包版本不能低于原生兼容版本。');
  }
  assertEqual(packageJson.version, businessVersion, 'package.json version');
  assertEqual(packageLock.version, businessVersion, 'package-lock.json version');
  assertEqual(packageLock.packages?.['']?.version, businessVersion, 'package-lock 根包 version');
  assertEqual(gradleVersion.versionCode, versionCode, 'Gradle versionCode');
  assertEqual(gradleVersion.versionName, versionName, 'Gradle versionName');

  if (expectedVersionCode !== undefined) {
    assertEqual(versionCode, expectedVersionCode, '发布参数 VersionCode');
  }
  if (expectedVersionName !== undefined) {
    assertEqual(versionName, expectedVersionName, '发布参数 VersionName');
  }
  if (expectedRuntimeVersion !== undefined) {
    assertEqual(runtimeVersion, expectedRuntimeVersion, '发布参数 RuntimeVersion');
  }

  return { versionCode, versionName, runtimeVersion };
}

function resolveBuildTools(androidSdkRoot) {
  const sdkCandidates = [
    androidSdkRoot,
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    'D:\\DevlopEnv\\Android\\SDK',
  ].filter(Boolean);
  for (const sdkCandidate of sdkCandidates) {
    const buildToolsRoot = path.join(sdkCandidate, 'build-tools');
    if (!existsSync(buildToolsRoot)) {
      continue;
    }
    const versions = readdirSync(buildToolsRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort((left, right) => right.localeCompare(left, undefined, { numeric: true }));
    for (const version of versions) {
      const directory = path.join(buildToolsRoot, version);
      const aapt = path.join(directory, process.platform === 'win32' ? 'aapt.exe' : 'aapt');
      const apksigner = path.join(directory, process.platform === 'win32' ? 'apksigner.bat' : 'apksigner');
      if (existsSync(aapt) && existsSync(apksigner)) {
        return { aapt, apksigner };
      }
    }
  }
  throw new Error('未找到同时包含 aapt 和 apksigner 的 Android build-tools。');
}

function runTool(command, args) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    shell: process.platform === 'win32' && command.toLowerCase().endsWith('.bat'),
    windowsHide: true,
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${path.basename(command)} 执行失败：${(result.stderr || result.stdout || '').trim()}`);
  }
  return `${result.stdout || ''}\n${result.stderr || ''}`.trim();
}

export function verifyApkVersionContract({
  apkPath,
  contract,
  androidSdkRoot,
  expectedPackageName = 'com.mnnu.ereport',
  expectedVersionName = contract.versionName,
  expectedSourceSha,
}) {
  if (!existsSync(apkPath)) {
    throw new Error(`APK 文件不存在：${apkPath}`);
  }
  const { aapt, apksigner } = resolveBuildTools(androidSdkRoot);
  const badging = runTool(aapt, ['dump', 'badging', apkPath]);
  const packageLine = badging.split(/\r?\n/).find((line) => line.startsWith('package:')) || '';
  const versionCode = Number(packageLine.match(/versionCode='(\d+)'/)?.[1]);
  const versionName = packageLine.match(/versionName='([^']+)'/)?.[1];
  const packageName = packageLine.match(/name='([^']+)'/)?.[1];
  assertEqual(packageName, expectedPackageName, 'APK packageName');
  assertEqual(versionCode, contract.versionCode, 'APK versionCode');
  assertEqual(versionName, expectedVersionName, 'APK versionName');

  const signatureOutput = runTool(apksigner, ['verify', '--verbose', '--print-certs-pem', apkPath]);
  const certificates = signatureOutput.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g) || [];
  if (certificates.length !== 1) {
    throw new Error(`APK 签名验证通过，但期望一份签名证书，实际读取到 ${certificates.length} 份。apksigner 输出：${signatureOutput.slice(0, 1200)}`);
  }
  const certificateSha256 = new X509Certificate(certificates[0]).fingerprint256
    .replaceAll(':', '').toLowerCase();
  const embedded = packageName === 'com.mnnu.ereport' || packageName === 'com.mnnu.ereport.acceptance'
    ? verifyEmbeddedRelease(apkPath, { version: contract.versionName, runtimeVersion: contract.runtimeVersion,
      sourceSha: expectedSourceSha }) : undefined;
  return { packageName, versionCode, versionName, certificateSha256, ...(embedded ? { embedded } : {}) };
}

export function validateEmbeddedRelease(metadata, expected) {
  if (metadata.version !== expected.version || metadata.runtimeVersion !== expected.runtimeVersion
      || !/^[a-f0-9]{40}$/.test(metadata.sourceSha)
      || (expected.sourceSha && metadata.sourceSha !== expected.sourceSha)) {
    throw new Error('APK 内置业务版本、运行时或源码 SHA 与发布候选不一致。');
  }
  return metadata;
}

export function verifyEmbeddedRelease(apkPath, expected) {
  if (statSync(apkPath).size > 300 * 1024 * 1024) throw new Error('APK 超出 300MB 体积限制。');
  const appRequire = createRequire(new URL('../package.json', import.meta.url));
  const { unzipSync } = appRequire('fflate');
  const requiredEntries = new Map([
    ['assets/e-report-release.json', 64 * 1024], ['assets/app.manifest', 1024 * 1024],
    ['assets/app.config', 1024 * 1024], ['assets/index.android.bundle', 64 * 1024 * 1024],
  ]);
  const entries = unzipSync(readFileSync(apkPath), {
    filter: entry => {
      if (!requiredEntries.has(entry.name)) return false;
      if (entry.originalSize > requiredEntries.get(entry.name)) throw new Error('APK 内置发布资源超过体积限制。');
      return true;
    },
  });
  if (!entries['assets/e-report-release.json'] || !entries['assets/app.manifest']
      || !entries['assets/app.config'] || !entries['assets/index.android.bundle']?.length) {
    throw new Error('APK 缺少内置业务发布信息或 Expo 功能包清单。');
  }
  const metadata = validateEmbeddedRelease(JSON.parse(Buffer.from(entries['assets/e-report-release.json']).toString('utf8')), expected);
  const manifest = JSON.parse(Buffer.from(entries['assets/app.manifest']).toString('utf8'));
  const configuration = JSON.parse(Buffer.from(entries['assets/app.config']).toString('utf8'));
  if (!Array.isArray(manifest.assets) || manifest.assets.length === 0) throw new Error('APK 内置功能包资源清单为空。');
  assertEqual(configuration.version, expected.version, 'APK 内置 Expo 业务版本');
  assertEqual(configuration.runtimeVersion, expected.runtimeVersion, 'APK 内置 Expo runtimeVersion');
  return metadata;
}

function parseArguments(argv) {
  const options = {};
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (!argument.startsWith('--')) {
      throw new Error(`未知参数：${argument}`);
    }
    const key = argument.slice(2);
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`参数 --${key} 缺少值。`);
    }
    options[key] = value;
    index += 1;
  }
  return options;
}

function optionalInteger(value, label) {
  if (value === undefined) {
    return undefined;
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${label} 必须是正整数。`);
  }
  return parsed;
}

export function main(argv = process.argv.slice(2)) {
  const options = parseArguments(argv);
  const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
  const appRoot = path.resolve(options['app-root'] || path.join(scriptDirectory, '..'));
  const contract = verifySourceVersionContract({
    appRoot,
    expectedVersionCode: optionalInteger(options['expected-version-code'], 'expected-version-code'),
    expectedVersionName: options['expected-version-name'],
    expectedRuntimeVersion: options['expected-runtime-version'],
  });
  const apk = options.apk
    ? verifyApkVersionContract({
      apkPath: path.resolve(options.apk),
      contract,
      androidSdkRoot: options['android-sdk'],
      expectedPackageName: options['apk-package-name'] || 'com.mnnu.ereport',
      expectedVersionName: options['apk-version-name'] || contract.versionName,
    })
    : undefined;
  console.log(`VERSION_CONTRACT=${JSON.stringify({ ...contract, apk })}`);
}

const invokedDirectly = process.argv[1]
  && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url));
if (invokedDirectly) {
  try {
    main();
  } catch (error) {
    console.error(`[版本契约失败] ${error.message}`);
    process.exitCode = 1;
  }
}
