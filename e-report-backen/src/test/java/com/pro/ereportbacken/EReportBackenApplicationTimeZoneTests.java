package com.pro.ereportbacken;

import org.junit.jupiter.api.Test;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EReportBackenApplicationTimeZoneTests {

    @Test
    void requiresUtcJvmZoneBeforeServingRequests() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertThatThrownBy(() -> new EReportBackenApplication().requireUtcJvmTimeZone())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("-Duser.timezone=UTC");

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThatCode(() -> new EReportBackenApplication().requireUtcJvmTimeZone())
                    .doesNotThrowAnyException();
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
