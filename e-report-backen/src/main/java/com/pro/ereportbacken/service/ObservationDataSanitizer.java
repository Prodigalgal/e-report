package com.pro.ereportbacken.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ObservationDataSanitizer {

    private static final int MAX_DEPTH = 6;
    private static final int MAX_COLLECTION_SIZE = 50;
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}(?![A-Za-z0-9_-])");
    private static final Pattern HARDWARE_IDENTIFIER = Pattern.compile(
            "(?i)\\b(imei|android[_ -]?id|serial(?: number)?|mac(?: address)?)\\s*[:=]\\s*[^,;\\s]+"
    );
    private static final Pattern MAC_ADDRESS = Pattern.compile("(?i)(?<![0-9A-F])(?:[0-9A-F]{2}[:-]){5}[0-9A-F]{2}(?![0-9A-F])");
    private static final Pattern COORDINATE = Pattern.compile(
            "(?i)\\b(latitude|longitude|lat|lng)\\s*[:=]\\s*-?\\d{1,3}(?:\\.\\d+)?"
    );

    private final ObjectMapper objectMapper;

    public ObservationDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String text(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = BEARER.matcher(value).replaceAll("Bearer [已脱敏]");
        sanitized = JWT.matcher(sanitized).replaceAll("[令牌已脱敏]");
        sanitized = EMAIL.matcher(sanitized).replaceAll("[邮箱已脱敏]");
        sanitized = PHONE.matcher(sanitized).replaceAll("[手机号已脱敏]");
        sanitized = HARDWARE_IDENTIFIER.matcher(sanitized).replaceAll("$1=[已移除]");
        sanitized = MAC_ADDRESS.matcher(sanitized).replaceAll("[MAC已移除]");
        sanitized = COORDINATE.matcher(sanitized).replaceAll("$1=[坐标已脱敏]");
        sanitized = sanitized.strip();
        return sanitized.length() <= maximumLength ? sanitized : sanitized.substring(0, maximumLength);
    }

    public String route(String value) {
        String sanitized = text(value, 255);
        if (sanitized == null) {
            return null;
        }
        int queryIndex = sanitized.indexOf('?');
        return queryIndex >= 0 ? sanitized.substring(0, queryIndex) : sanitized;
    }

    public String json(Object value, int maximumLength) {
        if (value == null) {
            return null;
        }
        Object sanitized = sanitizeValue(value, 0);
        try {
            String json = objectMapper.writeValueAsString(sanitized);
            if (json.length() <= maximumLength) {
                return json;
            }
            return objectMapper.writeValueAsString(Map.of(
                    "truncated", true,
                    "reason", "诊断上下文超过存储限制"
            ));
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (depth >= MAX_DEPTH) {
            return "[层级已截断]";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION_SIZE) {
                    sanitized.put("truncated", true);
                    break;
                }
                String key = text(String.valueOf(entry.getKey()), 100);
                if (key == null) {
                    continue;
                }
                sanitized.put(key, isSensitiveKey(key) ? "[已脱敏]" : sanitizeValue(entry.getValue(), depth + 1));
            }
            return sanitized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            int count = 0;
            for (Object item : iterable) {
                if (count++ >= MAX_COLLECTION_SIZE) {
                    sanitized.add("[数量已截断]");
                    break;
                }
                sanitized.add(sanitizeValue(item, depth + 1));
            }
            return sanitized;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return text(String.valueOf(value), 1000);
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("captcha")
                || normalized.contains("phone")
                || normalized.contains("email")
                || normalized.contains("address")
                || normalized.equals("lat")
                || normalized.equals("lng")
                || normalized.contains("latitude")
                || normalized.contains("longitude")
                || normalized.contains("imei")
                || normalized.contains("androidid")
                || normalized.contains("serial")
                || normalized.equals("mac")
                || normalized.contains("macaddress")
                || normalized.contains("advertisingid")
                || normalized.contains("gaid")
                || normalized.contains("idfa")
                || normalized.contains("bssid")
                || normalized.equals("ssid")
                || normalized.contains("ipaddress")
                || normalized.contains("gateway")
                || normalized.contains("subnet");
    }
}
