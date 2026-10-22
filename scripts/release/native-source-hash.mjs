const TEXT_SOURCE = /(?:\.(?:kt|java|gradle|kts|properties|xml|json|crt|pro|txt|md|js|ts|c|cpp|h|cmake|sh|bat|lockfile)|(?:^|[/\\])gradlew)$/i;

export function normalizeNativeSource(filePath, bytes) {
  if (!TEXT_SOURCE.test(filePath)) return bytes;
  let text = bytes.toString('utf8').replace(/\r\n/g, '\n');
  if (filePath.replaceAll('\\', '/').endsWith('node_modules/@react-native-masked-view/masked-view/android/src/main/AndroidManifest.xml')) {
    // The library's Gradle build removes this deprecated package attribute; the Gradle namespace remains part of the fingerprint.
    text = text.replace(/\bpackage="org\.reactnative\.maskedview"\s*/, '').replace(/<manifest\s+/, '<manifest ');
  }
  return Buffer.from(text);
}

export function createNativeSourceTransform() {
  const pending = new WeakMap();
  return (source, chunk, end) => {
    if (source.type !== 'file' || !TEXT_SOURCE.test(source.filePath)) return chunk;
    const chunks = pending.get(source) || [];
    if (!end) { chunks.push(Buffer.from(chunk)); pending.set(source, chunks); return null; }
    pending.delete(source);
    return normalizeNativeSource(source.filePath, Buffer.concat(chunks));
  };
}
