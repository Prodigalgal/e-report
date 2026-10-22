import assert from 'node:assert/strict';
import test from 'node:test';

import {
  requestBodyBytes,
  sha256RequestBody,
  sha256Text,
} from '../src/security/request-signature-body.ts';

test('request signing normalizes every supported body to an owned Uint8Array', async () => {
  const empty = await requestBodyBytes();
  const text = await requestBodyBytes('工单');
  const buffer = await requestBodyBytes(Uint8Array.from([1, 2, 3]).buffer);
  const view = await requestBodyBytes(new Uint16Array([0x0201]));

  assert.ok(empty instanceof Uint8Array);
  assert.ok(text instanceof Uint8Array);
  assert.ok(buffer instanceof Uint8Array);
  assert.ok(view instanceof Uint8Array);
  assert.deepEqual([...empty], []);
  assert.deepEqual([...buffer], [1, 2, 3]);
  assert.deepEqual([...view], [1, 2]);
});

test('request signing serializes URLSearchParams and Blob deterministically', async () => {
  const params = await requestBodyBytes(new URLSearchParams([['状态', '待处理']]));
  const blob = await requestBodyBytes(new Blob(['abc']));

  assert.equal(new TextDecoder().decode(params), '%E7%8A%B6%E6%80%81=%E5%BE%85%E5%A4%84%E7%90%86');
  assert.equal(new TextDecoder().decode(blob), 'abc');
});

test('request signing hashes text and empty bodies without a native bridge call', async () => {
  assert.equal(
    sha256Text('abc'),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
  );
  assert.equal(
    await sha256RequestBody(),
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  );
});
