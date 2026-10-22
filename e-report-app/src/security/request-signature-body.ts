import { sha256 } from 'js-sha256';

const encoder = new TextEncoder();

export function sha256Text(value: string): string {
  return sha256(value);
}

export async function sha256RequestBody(body?: BodyInit | null): Promise<string> {
  return sha256(await requestBodyBytes(body));
}

export async function requestBodyBytes(body?: BodyInit | null): Promise<Uint8Array<ArrayBuffer>> {
  if (body === undefined || body === null) return new Uint8Array(new ArrayBuffer(0));
  if (typeof body === 'string') return copyView(encoder.encode(body));
  if (body instanceof ArrayBuffer) return new Uint8Array(body.slice(0));
  if (ArrayBuffer.isView(body)) return copyView(body);
  if (typeof Blob !== 'undefined' && body instanceof Blob) {
    return new Uint8Array(await body.arrayBuffer());
  }
  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
    return copyView(encoder.encode(body.toString()));
  }
  throw new Error('当前请求体无法进行安全签名，请改用 JSON 或分片上传。');
}

function copyView(view: ArrayBufferView): Uint8Array<ArrayBuffer> {
  const copy = new Uint8Array(new ArrayBuffer(view.byteLength));
  copy.set(new Uint8Array(view.buffer, view.byteOffset, view.byteLength));
  return copy;
}
