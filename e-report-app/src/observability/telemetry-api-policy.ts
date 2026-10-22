export function isExpectedApiStatus(status: number, expectedStatuses: readonly number[] = []) {
  return Number.isInteger(status) && expectedStatuses.includes(status);
}
