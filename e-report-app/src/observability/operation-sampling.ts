export function shouldSampleOperationSuccess(ratePercent: number, randomValue = Math.random()) {
  const rate = Math.max(0, Math.min(Number.isFinite(ratePercent) ? ratePercent : 0, 100));
  return rate >= 100 || (rate > 0 && randomValue * 100 < rate);
}
