package com.pro.ereportbacken.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * W3C Trace Context 标识符的边界规范化。
 *
 * <p>Trace/Span ID 是协议标识符，不应经过手机号、邮箱等业务文本脱敏器处理；
 * 这里只接受规范的小写十六进制值，非法值统一按缺失处理。</p>
 */
public final class ObservationTraceContext {

    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("[0-9a-f]{16}");

    private ObservationTraceContext() {
    }

    public static String traceId(String value) {
        return normalize(value, TRACE_ID_PATTERN, 32);
    }

    public static String spanId(String value) {
        return normalize(value, SPAN_ID_PATTERN, 16);
    }

    private static String normalize(String value, Pattern pattern, int length) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != length
                || !pattern.matcher(normalized).matches()
                || normalized.chars().allMatch(character -> character == '0')) {
            return null;
        }
        return normalized;
    }
}
