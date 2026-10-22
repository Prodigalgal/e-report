export const DISPLAY_TIME_ZONE = 'Asia/Shanghai';

const LOCAL_DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/;
const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const EXPLICIT_OFFSET_PATTERN = /(?:[zZ]|[+-]\d{2}:?\d{2})$/;

function normalizeDateText(value: string): string {
  return String(value).trim()
    .replace(' ', 'T')
    .replace(/(\.\d{3})\d+(?=(?:[zZ]|[+-]\d{2}:?\d{2})?$)/, '$1');
}

/** API 的 LocalDateTime 没有时区后缀，但其语义固定为 UTC。 */
export function parseApiDateTime(value?: string | Date | null): Date | null {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : new Date(value.getTime());
  }
  if (value == null || String(value).trim() === '') return null;
  const normalized = normalizeDateText(value);
  if (DATE_ONLY_PATTERN.test(normalized)) {
    const date = new Date(`${normalized}T12:00:00Z`);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const timestamp = LOCAL_DATE_TIME_PATTERN.test(normalized) && !EXPLICIT_OFFSET_PATTERN.test(normalized)
    ? `${normalized}Z`
    : normalized;
  const date = new Date(timestamp);
  return Number.isNaN(date.getTime()) ? null : date;
}

function displayParts(value: string | Date): Record<string, string> | null {
  const date = parseApiDateTime(value);
  if (!date) return null;
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: DISPLAY_TIME_ZONE,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(date);
  return Object.fromEntries(parts.filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
}

function formatDateTimeWithOptions(value: string | null | undefined, includeSeconds: boolean): string {
  if (!value) return '-';
  const parts = displayParts(value);
  if (!parts) return value;
  const time = `${parts.hour}:${parts.minute}`;
  return `${parts.year}-${parts.month}-${parts.day} ${includeSeconds ? `${time}:${parts.second}` : time}`;
}

export function formatDateTime(value?: string | null): string {
  return formatDateTimeWithOptions(value, false);
}

export function formatDateTimeWithSeconds(value?: string | null): string {
  return formatDateTimeWithOptions(value, true);
}

/** 返回供原生日期选择器显示的 UTC+8 墙上时间。 */
export function formatDateTimeInput(value?: string | null): string {
  if (!value) return '';
  const parts = displayParts(value);
  if (!parts) return '';
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

/** 将 UTC+8 墙上时间转换成 API 约定的 UTC LocalDateTime。 */
export function parseDateTimeInput(value?: string | null): string | null {
  if (!value) return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(String(value));
  if (!match) return null;
  const [, year, month, day, hour, minute, second = '00'] = match;
  const numeric = [year, month, day, hour, minute, second].map(Number);
  const [yearNumber, monthNumber, dayNumber, hourNumber, minuteNumber, secondNumber] = numeric;
  const daysInMonth = new Date(Date.UTC(yearNumber, monthNumber, 0)).getUTCDate();
  if (monthNumber < 1 || monthNumber > 12 || dayNumber < 1 || dayNumber > daysInMonth
    || hourNumber > 23 || minuteNumber > 59 || secondNumber > 59) return null;
  const wallClockMillis = Date.UTC(yearNumber, monthNumber - 1, dayNumber, hourNumber, minuteNumber, secondNumber);
  const utcDate = new Date(wallClockMillis - (8 * 60 * 60 * 1000));
  if (Number.isNaN(utcDate.getTime())) return null;
  return utcDate.toISOString().slice(0, 19);
}

/** 将日期选择器返回的本地 Date 墙上字段按 UTC+8 解释。 */
export function pickerDateToApiDateTime(value: Date): string | null {
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return null;
  return parseDateTimeInput(
    `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
      + `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`,
  );
}

/** 为原生日期选择器构造一个 Date，使其在任意设备时区显示 UTC+8 的墙上时间。 */
export function dateForUtc8Picker(value?: string | null): Date {
  const parts = value
    ? displayParts(value)
    : displayParts(new Date(Date.now() + (30 * 24 * 60 * 60 * 1000)));
  if (parts) {
    return new Date(
      Number(parts.year), Number(parts.month) - 1, Number(parts.day),
      Number(parts.hour), Number(parts.minute), Number(parts.second),
    );
  }
  return new Date();
}

export function displayYear(value: string | Date = new Date()): string {
  const date = parseApiDateTime(value) || new Date();
  return new Intl.DateTimeFormat('en-US', { timeZone: DISPLAY_TIME_ZONE, year: 'numeric' }).format(date);
}

export function formatFileSize(size?: number | null) {
  if (!size) {
    return '0 字节';
  }
  if (size < 1024) {
    return `${size} 字节`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} 千字节`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} 兆字节`;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}
