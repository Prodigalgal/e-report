import { ApiError, apiFetch } from '@/api/http';
import type { ReverseGeocodeResponse } from '@/types/api';

const REVERSE_GEOCODE_TIMEOUT_MS = 60000;
const REVERSE_GEOCODE_MAX_ATTEMPTS = 3;

export function reverseGeocode(gpsCoordinates: string) {
  return retryReverseGeocode(() => apiFetch<ReverseGeocodeResponse>('/location/reverse-geocode', {
      query: { gpsCoordinates },
      timeoutMs: REVERSE_GEOCODE_TIMEOUT_MS,
    }));
}

async function retryReverseGeocode(request: () => Promise<ReverseGeocodeResponse>) {
  let lastError: unknown;
  for (let attempt = 1; attempt <= REVERSE_GEOCODE_MAX_ATTEMPTS; attempt += 1) {
    try {
      return await request();
    } catch (error) {
      lastError = error;
      if (attempt >= REVERSE_GEOCODE_MAX_ATTEMPTS || !shouldRetryReverseGeocode(error)) {
        throw error;
      }
      await sleep(Math.min(800 * attempt, 2000));
    }
  }
  throw lastError;
}

function shouldRetryReverseGeocode(error: unknown) {
  if (!(error instanceof ApiError)) {
    return false;
  }
  return error.status === 0
    || error.status === 408
    || error.status === 429
    || error.status >= 500;
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
