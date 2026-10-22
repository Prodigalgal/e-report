package com.pro.ereportbacken.security;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class WebEnvironmentSignals {

    private static final Set<String> ALLOWED = Set.of(
            "WEBDRIVER", "HEADLESS_USER_AGENT", "AUTOMATION_GLOBAL", "LANGUAGES_EMPTY",
            "DESKTOP_PLUGINS_EMPTY", "CLIENT_HINTS_UNAVAILABLE", "HIGH_ENTROPY_HINTS_UNAVAILABLE",
            "HIGH_ENTROPY_HINTS_BLOCKED", "CANVAS_UNAVAILABLE", "CANVAS_BLOCKED",
            "WEBGL_UNAVAILABLE", "WEBGL_RENDERER_MASKED", "WEBGL_BLOCKED",
            "WEB_AUDIO_UNAVAILABLE", "WEB_AUDIO_BLOCKED", "FONT_PROBE_UNAVAILABLE",
            "FONT_PROBE_BLOCKED", "COLLECTION_TIMEOUT", "SECURE_BROWSER_API_UNAVAILABLE"
    );
    private static final Set<String> INTERACTIVE_CHALLENGE = Set.of(
            "WEBDRIVER", "HEADLESS_USER_AGENT", "AUTOMATION_GLOBAL"
    );

    private WebEnvironmentSignals() {
    }

    public static Set<String> normalize(Set<String> submitted) {
        Set<String> normalized = new LinkedHashSet<>();
        if (submitted == null) {
            return normalized;
        }
        submitted.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(ALLOWED::contains)
                .sorted()
                .forEach(normalized::add);
        return normalized;
    }

    public static boolean requiresInteractiveChallenge(Set<String> submitted) {
        return normalize(submitted).stream().anyMatch(INTERACTIVE_CHALLENGE::contains);
    }
}
