export const DISPLAY_TIME_ZONE = 'Asia/Shanghai';

const LOCAL_DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/;
const DATE_ONLY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const EXPLICIT_OFFSET_PATTERN = /(?:[zZ]|[+-]\d{2}:?\d{2})$/;

function normalizeDateText(value) {
    return String(value).trim()
        .replace(' ', 'T')
        .replace(/(\.\d{3})\d+(?=(?:[zZ]|[+-]\d{2}:?\d{2})?$)/, '$1');
}

/**
 * API 的 LocalDateTime 没有时区后缀，但其语义固定为 UTC。所有客户端先
 * 归一化为绝对时间，再由展示 formatter 显式转换为 Asia/Shanghai。
 */
export function parseApiDateTime(value) {
    if (value instanceof Date) {
        return Number.isNaN(value.getTime()) ? null : new Date(value.getTime());
    }
    if (value == null || String(value).trim() === '') return null;
    const normalized = normalizeDateText(value);
    if (DATE_ONLY_PATTERN.test(normalized)) {
        // 日期字段没有时分语义，使用正午避免时区转换跨日。
        const date = new Date(`${normalized}T12:00:00Z`);
        return Number.isNaN(date.getTime()) ? null : date;
    }
    const timestamp = LOCAL_DATE_TIME_PATTERN.test(normalized) && !EXPLICIT_OFFSET_PATTERN.test(normalized)
        ? `${normalized}Z`
        : normalized;
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateValue(dateString, locale, options) {
    const date = parseApiDateTime(dateString);
    if (!date) return null;
    return new Intl.DateTimeFormat(locale, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
        ...options,
        timeZone: DISPLAY_TIME_ZONE,
    }).format(date);
}

export const formatDate = (dateString, locale = 'zh-CN', options = {}) => {
    if (!dateString) return '暂无';
    try {
        return formatDateValue(dateString, locale, options) || dateString;
    } catch (error) {
        console.warn(`Error formatting date string "${dateString}":`, error);
        return dateString;
    }
};

export const formatDateTime = (dateString, locale = 'zh-CN', options = {}, emptyLabel = '-') => {
    if (!dateString) return emptyLabel;
    try {
        return formatDateValue(dateString, locale, options) || emptyLabel;
    } catch (error) {
        console.warn(`Error formatting date string "${dateString}":`, error);
        return emptyLabel;
    }
};

function displayParts(dateString) {
    const date = parseApiDateTime(dateString);
    if (!date) return null;
    const parts = new Intl.DateTimeFormat('en-US', {
        timeZone: DISPLAY_TIME_ZONE,
        hourCycle: 'h23',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    }).formatToParts(date);
    return Object.fromEntries(parts.filter(part => part.type !== 'literal').map(part => [part.type, part.value]));
}

export function formatDateTimeInput(value) {
    const parts = displayParts(value);
    if (!parts) return '';
    return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

/** 将 UTC+8 的 datetime-local 墙上时间转换成 API 约定的 UTC LocalDateTime。 */
export function parseDateTimeInput(value) {
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

export function displayYear(value = new Date()) {
    const date = parseApiDateTime(value) || new Date();
    return new Intl.DateTimeFormat('en-US', { timeZone: DISPLAY_TIME_ZONE, year: 'numeric' }).format(date);
}
