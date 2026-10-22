import * as Crypto from 'expo-crypto';

import { getDeviceIdentity, signDevicePayload } from '@/security/device-security';
import { sha256RequestBody, sha256Text } from '@/security/request-signature-body';
import { canonicalRequestPayload } from '@/security/request-signature-canonical';

type SignRequestOptions = {
  url: string;
  method: string;
  body?: BodyInit | null;
  sessionId: string;
  headers?: HeadersInit;
};

export async function buildSignedRequestHeaders(options: SignRequestOptions): Promise<Headers> {
  const identity = await getDeviceIdentity();
  const created = Math.floor(Date.now() / 1000);
  const expires = created + 60;
  const nonce = base64Url(await Crypto.getRandomBytesAsync(24));
  const bodyDigest = await sha256Body(options.body);
  const method = options.method.toUpperCase();
  const headers = new Headers(options.headers);
  const payload = canonicalRequestPayload({
    url: options.url,
    method,
    bodyDigest,
    created,
    expires,
    nonce,
    sessionId: options.sessionId,
  });
  const signature = await signDevicePayload(payload);
  headers.set('X-Device-Key-Id', identity.keyId);
  headers.set('X-Signature-Created', String(created));
  headers.set('X-Signature-Expires', String(expires));
  headers.set('X-Signature-Nonce', nonce);
  headers.set('X-Content-SHA256', bodyDigest);
  headers.set('X-Auth-Session-Id', options.sessionId);
  headers.set('X-Device-Signature', signature);
  return headers;
}

export async function sha256Hex(value: string): Promise<string> {
  return sha256Text(value);
}

async function sha256Body(body?: BodyInit | null): Promise<string> {
  return sha256RequestBody(body);
}

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach(value => { binary += String.fromCharCode(value); });
  return globalThis.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
