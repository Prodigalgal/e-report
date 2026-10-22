const DISPLAY_OFFSET_MS = 8 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
export const EMAIL_DELIVERY_MAX_DAYS = 93;

export function defaultEmailDeliveryRange(now: Date = new Date()): { from: string; to: string } {
    const to = new Date(now.getTime() + DISPLAY_OFFSET_MS).toISOString().slice(0, 10);
    const from = new Date(Date.parse(`${to}T00:00:00Z`) - 6 * DAY_MS).toISOString().slice(0, 10);
    return { from, to };
}

export function validateEmailDeliveryRange(from: string, to: string): string | null {
    const dates = [from, to].map(value => /^\d{4}-\d{2}-\d{2}$/.test(value) ? Date.parse(`${value}T00:00:00Z`) : NaN);
    if (dates.some((value, index) => !Number.isFinite(value) || new Date(value).toISOString().slice(0, 10) !== [from, to][index])) {
        return '请输入有效的起止日期（YYYY-MM-DD）。';
    }
    const days = (dates[1] - dates[0]) / DAY_MS;
    return days < 0 || days >= EMAIL_DELIVERY_MAX_DAYS ? '日期顺序不正确或跨度超过 93 天。' : null;
}
