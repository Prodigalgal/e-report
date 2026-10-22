export function normalizeSelectedValues(
  value: string | string[] | undefined,
  allowedValues: readonly string[],
) {
  const allowed = new Set(allowedValues);
  const requested = (Array.isArray(value) ? value : value ? [value] : [])
    .flatMap((item) => item.split(','))
    .map((item) => item.trim())
    .filter((item) => item && allowed.has(item));
  return [...new Set(requested)];
}

export function toggleSelectedValue(values: readonly string[], value: string) {
  return values.includes(value)
    ? values.filter((candidate) => candidate !== value)
    : [...values, value];
}
