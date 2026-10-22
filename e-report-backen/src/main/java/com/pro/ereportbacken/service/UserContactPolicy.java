package com.pro.ereportbacken.service;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UserContactPolicy {
    public static final String PHONE_REGEX = "^1[3-9][0-9]{9}$";
    public static final String PHONE_MESSAGE = "手机号必须是11位中国大陆手机号码";
    public static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private UserContactPolicy() {
    }

    public static String normalizeRequiredPhone(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("手机号不能为空。");
        }
        String normalized = value.trim();
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(PHONE_MESSAGE + "。");
        }
        return normalized;
    }

    public static String normalizeOptionalEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确。");
        }
        return normalized;
    }

    public static boolean isValidPhone(String value) {
        return StringUtils.hasText(value) && PHONE_PATTERN.matcher(value.trim()).matches();
    }

    public static boolean isValidEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_EMAIL_LENGTH && EMAIL_PATTERN.matcher(normalized).matches();
    }
}
