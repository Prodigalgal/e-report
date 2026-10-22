const TERMINAL_BATCH_STATUSES = new Set([400, 409, 413, 422]);

export function shouldDeleteTelemetryBatch(status: number) {
  return status >= 200 && status < 300 || TERMINAL_BATCH_STATUSES.has(status);
}
