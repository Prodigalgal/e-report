package com.pro.ereportbacken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationDataSanitizerTests {

    private final ObservationDataSanitizer sanitizer = new ObservationDataSanitizer(
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void removesSensitiveKeysAndIdentifiersFromStructuredContext() {
        String json = sanitizer.json(Map.of(
                "password", "secret-password",
                "authorization", "Bearer private-token",
                "phone", "13912345678",
                "email", "user@example.com",
                "address", "测试路1号",
                "latitude", 24.123456,
                "longitude", 117.123456,
                "androidId", "abcdef1234567890",
                "imei", "867530900000000",
                "safe", Map.of("provider", "gps", "accuracy", 18)
        ), 16_000);

        assertThat(json)
                .doesNotContain("secret-password", "private-token", "13912345678", "user@example.com")
                .doesNotContain("测试路1号", "24.123456", "117.123456", "abcdef1234567890", "867530900000000")
                .contains("[已脱敏]", "gps", "accuracy");
    }

    @Test
    void removesSensitiveValuesEmbeddedInMessages() {
        String sanitized = sanitizer.text(
                "Bearer abc.def.ghi 手机13912345678 邮箱user@example.com MAC=AA:BB:CC:DD:EE:FF latitude=24.12",
                2000
        );

        assertThat(sanitized)
                .doesNotContain("13912345678", "user@example.com", "AA:BB:CC:DD:EE:FF", "24.12")
                .contains("[手机号已脱敏]", "[邮箱已脱敏]", "[已移除]", "[坐标已脱敏]");
    }

    @Test
    void removesQueryStringFromRoute() {
        assertThat(sanitizer.route("/work-orders/search?phone=13912345678&token=secret"))
                .isEqualTo("/work-orders/search");
    }
}
