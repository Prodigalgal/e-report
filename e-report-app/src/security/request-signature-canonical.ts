export type CanonicalRequestInput = {
  url: string;
  method: string;
  bodyDigest: string;
  created: number;
  expires: number;
  nonce: string;
  sessionId: string;
};

export function canonicalRequestPayload(input: CanonicalRequestInput) {
  return [
    'e-report-request-v1',
    `@method:${input.method.toUpperCase()}`,
    `@target-uri:${canonicalTarget(input.url)}`,
    `content-sha256:${input.bodyDigest}`,
    `created:${input.created}`,
    `expires:${input.expires}`,
    `nonce:${input.nonce}`,
    `session-id:${input.sessionId}`,
  ].join('\n');
}

export function canonicalTarget(rawUrl: string) {
  const url = new URL(rawUrl);
  const rawQuery = url.search.startsWith('?') ? url.search.slice(1) : url.search;
  if (!rawQuery) return url.pathname;
  return `${url.pathname}?${rawQuery.split('&').sort().join('&')}`;
}
