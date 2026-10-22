#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { mkdir, open, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024;
const DEFAULT_CONCURRENCY = 4;
const DEFAULT_MAX_RETRIES = 5;
const REQUEST_TIMEOUT_MS = 90_000;

const options = parseArguments(process.argv.slice(2));
const officialUrl = requireOption(options, 'url');
const expectedSize = positiveInteger(requireOption(options, 'size'), 'size');
const expectedSha256 = requireOption(options, 'sha256').trim().toLowerCase();
const outputPath = resolve(requireOption(options, 'output'));
const chunkSize = positiveInteger(options.get('chunk-size') || String(DEFAULT_CHUNK_SIZE), 'chunk-size');
const concurrency = positiveInteger(options.get('concurrency') || String(DEFAULT_CONCURRENCY), 'concurrency');
const maxRetries = nonNegativeInteger(options.get('max-retries') || String(DEFAULT_MAX_RETRIES), 'max-retries');

if (!/^[a-f0-9]{64}$/.test(expectedSha256)) {
  throw new Error('sha256 must be a 64-character lowercase or uppercase hexadecimal value.');
}

const r2Url = await resolveR2Url(officialUrl, expectedSize, expectedSha256);
const partDirectory = `${outputPath}.parts`;
await mkdir(dirname(outputPath), { recursive: true });
await mkdir(partDirectory, { recursive: true });

const ranges = createRanges(expectedSize, chunkSize);
let nextRangeIndex = 0;
let completedBytes = 0;
const startedAt = Date.now();

await Promise.all(Array.from({ length: Math.min(concurrency, ranges.length) }, async () => {
  while (true) {
    const rangeIndex = nextRangeIndex;
    nextRangeIndex += 1;
    if (rangeIndex >= ranges.length) {
      return;
    }
    const range = ranges[rangeIndex];
    const result = await downloadRange(r2Url, partDirectory, range, expectedSize, maxRetries);
    completedBytes += result.bytes;
    const percentage = ((completedBytes / expectedSize) * 100).toFixed(1);
    process.stdout.write(`range ${range.index + 1}/${ranges.length} ${percentage}% retries=${result.retries}\n`);
  }
}));

const actualSha256 = await assembleAndHash(partDirectory, ranges, outputPath);
const downloadedFile = await stat(outputPath);
if (downloadedFile.size !== expectedSize) {
  throw new Error(`assembled size mismatch: expected ${expectedSize}, got ${downloadedFile.size}`);
}
if (actualSha256 !== expectedSha256) {
  throw new Error(`SHA-256 mismatch: expected ${expectedSha256}, got ${actualSha256}`);
}

await rm(partDirectory, { recursive: true, force: true });
process.stdout.write(
  `R2_RANGE_DOWNLOAD_VERIFIED size=${downloadedFile.size} sha256=${actualSha256} seconds=${((Date.now() - startedAt) / 1000).toFixed(1)}\n`,
);

async function resolveR2Url(url, size, sha256) {
  const response = await fetch(url, {
    method: 'HEAD',
    redirect: 'manual',
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (response.status < 300 || response.status >= 400) {
    throw new Error(`official download URL must redirect to R2, got HTTP ${response.status}`);
  }
  const location = response.headers.get('location');
  if (!location) {
    throw new Error('official download URL did not provide an R2 redirect location');
  }
  const releaseSha256 = response.headers.get('x-app-sha256')?.trim().toLowerCase();
  if (releaseSha256 && releaseSha256 !== sha256) {
    throw new Error(`official download SHA-256 header mismatch: ${releaseSha256}`);
  }

  const resolvedR2Url = new URL(location, url);
  if (resolvedR2Url.host === new URL(url).host) {
    throw new Error('official download URL redirected back to the application origin instead of R2');
  }
  const r2Head = await fetch(resolvedR2Url, {
    method: 'HEAD',
    redirect: 'error',
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (!r2Head.ok) {
    throw new Error(`R2 HEAD failed with HTTP ${r2Head.status}`);
  }
  const contentLength = Number(r2Head.headers.get('content-length'));
  if (contentLength !== size) {
    throw new Error(`R2 content length mismatch: expected ${size}, got ${contentLength}`);
  }
  if (!String(r2Head.headers.get('accept-ranges') || '').toLowerCase().includes('bytes')) {
    throw new Error('R2 object does not advertise byte range support');
  }
  return resolvedR2Url.toString();
}

function createRanges(totalSize, requestedChunkSize) {
  const ranges = [];
  for (let start = 0, index = 0; start < totalSize; start += requestedChunkSize, index += 1) {
    ranges.push({
      index,
      start,
      end: Math.min(start + requestedChunkSize - 1, totalSize - 1),
    });
  }
  return ranges;
}

async function downloadRange(url, directory, range, totalSize, maxRetryCount) {
  const expectedLength = range.end - range.start + 1;
  const partPath = resolve(directory, `${String(range.index).padStart(4, '0')}.part`);
  try {
    const existingPart = await stat(partPath);
    if (existingPart.size === expectedLength) {
      return { bytes: expectedLength, retries: 0 };
    }
  } catch {
    // Missing or incomplete parts are downloaded below.
  }

  let lastError;
  for (let retryCount = 0; retryCount <= maxRetryCount; retryCount += 1) {
    try {
      const response = await fetch(url, {
        headers: {
          Accept: 'application/vnd.android.package-archive',
          Range: `bytes=${range.start}-${range.end}`,
          'X-Request-Id': `e-report-release-verify-${range.index}-${retryCount}`,
        },
        redirect: 'error',
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
      if (response.status !== 206) {
        throw new Error(`HTTP ${response.status}`);
      }
      const contentRange = response.headers.get('content-range') || '';
      if (contentRange !== `bytes ${range.start}-${range.end}/${totalSize}`) {
        throw new Error(`invalid Content-Range: ${contentRange}`);
      }
      const bytes = Buffer.from(await response.arrayBuffer());
      if (bytes.length !== expectedLength) {
        throw new Error(`range length mismatch: expected ${expectedLength}, got ${bytes.length}`);
      }
      await writeFile(partPath, bytes);
      return { bytes: bytes.length, retries: retryCount };
    } catch (error) {
      lastError = error;
      if (retryCount < maxRetryCount) {
        await delay(Math.min(1200 * (retryCount + 1), 5000));
      }
    }
  }
  throw new Error(`range ${range.start}-${range.end} failed after retries: ${String(lastError)}`);
}

async function assembleAndHash(directory, rangesToAssemble, targetPath) {
  const target = await open(targetPath, 'w');
  const hasher = createHash('sha256');
  let position = 0;
  try {
    for (const range of rangesToAssemble) {
      const partPath = resolve(directory, `${String(range.index).padStart(4, '0')}.part`);
      const bytes = await readFile(partPath);
      await target.write(bytes, 0, bytes.length, position);
      hasher.update(bytes);
      position += bytes.length;
    }
  } finally {
    await target.close();
  }
  return hasher.digest('hex');
}

function parseArguments(values) {
  const parsed = new Map();
  for (let index = 0; index < values.length; index += 1) {
    const argument = values[index];
    if (!argument.startsWith('--')) {
      throw new Error(`unexpected argument: ${argument}`);
    }
    const key = argument.slice(2);
    const value = values[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`missing value for --${key}`);
    }
    parsed.set(key, value);
    index += 1;
  }
  return parsed;
}

function requireOption(parsed, name) {
  const value = parsed.get(name);
  if (!value) {
    throw new Error(`missing required option --${name}`);
  }
  return value;
}

function positiveInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function nonNegativeInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0) {
    throw new Error(`${name} must be a non-negative integer`);
  }
  return parsed;
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}
