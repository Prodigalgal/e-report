package com.pro.ereportbacken.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class UtcTimeTests {

    @Test
    void writesUtcEvenWhenJvmDefaultZoneIsDifferent() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Instant before = Instant.now();
            Instant actual = UtcTime.now().toInstant(ZoneOffset.UTC);
            Instant after = Instant.now();

            assertThat(actual).isBetween(before.minus(Duration.ofMillis(1)), after.plus(Duration.ofMillis(1)));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
