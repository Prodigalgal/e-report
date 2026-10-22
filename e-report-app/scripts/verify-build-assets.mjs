import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const IGNORED_DIRECTORIES = new Set([
  '.expo', '.git', '.gradle', 'build', 'dist', 'node_modules', 'output', 'test-results',
]);
const ISOBMFF_BLOCKED_BRANDS = new Set([
  'avif', 'avis', 'heic', 'heix', 'heim', 'heis', 'hevc', 'hevx', 'hevm', 'hevs', 'mif1', 'msf1',
]);

export function detectBlockedImageFormat(header) {
  const bytes = Buffer.from(header);
  if (bytes.length >= 4 && bytes.subarray(0, 4).toString('ascii') === 'icns') return 'ICNS';
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0x0a) return 'JXL';
  if (bytes.length >= 12
      && bytes.subarray(4, 8).toString('ascii') === 'JXL '
      && bytes.subarray(8, 12).equals(Buffer.from([0x0d, 0x0a, 0x87, 0x0a]))) {
    return 'JXL';
  }
  if (bytes.length >= 12 && bytes.subarray(4, 8).toString('ascii') === 'ftyp') {
    for (let offset = 8; offset + 4 <= bytes.length; offset += 4) {
      if (ISOBMFF_BLOCKED_BRANDS.has(bytes.subarray(offset, offset + 4).toString('ascii'))) {
        return 'HEIF/AVIF';
      }
    }
  }
  return null;
}

async function verifyProjectAssets(root) {
  const blocked = [];
  await walk(root, async file => {
    const handle = await fs.open(file, 'r');
    try {
      const header = Buffer.alloc(128);
      const { bytesRead } = await handle.read(header, 0, header.length, 0);
      const format = detectBlockedImageFormat(header.subarray(0, bytesRead));
      if (format) blocked.push(`${format}: ${path.relative(root, file)}`);
    } finally {
      await handle.close();
    }
  });
  if (blocked.length > 0) {
    throw new Error(`构建资产包含当前解析器无法安全处理的格式：\n${blocked.join('\n')}`);
  }
  process.stdout.write('ANDROID_ASSET_SECURITY=PASS\n');
}

async function walk(directory, inspectFile) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.isSymbolicLink()) continue;
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (!IGNORED_DIRECTORIES.has(entry.name)) await walk(absolute, inspectFile);
    } else if (entry.isFile()) {
      await inspectFile(absolute);
    }
  }
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : '';
if (invokedPath === fileURLToPath(import.meta.url)) {
  verifyProjectAssets(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'))
    .catch(error => {
      process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
      process.exitCode = 1;
    });
}
