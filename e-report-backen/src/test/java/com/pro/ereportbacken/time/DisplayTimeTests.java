package com.pro.ereportbacken.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayTimeTests {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void convertsUtcStorageValueToChinaDisplayTime() {
        LocalDateTime utc = LocalDateTime.of(2026, 9, 16, 9, 21, 50);

        assertThat(DisplayTime.utcLocalDateTime(utc))
                .isEqualTo(LocalDateTime.of(2026, 9, 16, 17, 21, 50));
        assertThat(DisplayTime.formatUtc(utc, FORMATTER)).isEqualTo("2026-09-16 17:21:50");
    }

    @Test
    void keepsNullDisplayValuesEmpty() {
        assertThat(DisplayTime.utcToDisplay(null)).isNull();
        assertThat(DisplayTime.formatUtc(null, FORMATTER)).isEmpty();
    }

    @Test
    void convertsChinaWallTimeAndDateBoundariesBackToUtc() {
        LocalDate displayDate = LocalDate.of(2026, 9, 17);

        assertThat(DisplayTime.startOfDisplayDateUtc(displayDate))
                .isEqualTo(LocalDateTime.of(2026, 9, 16, 16, 0));
        assertThat(DisplayTime.startOfDisplayDateUtc(displayDate.plusDays(1)))
                .isEqualTo(LocalDateTime.of(2026, 9, 17, 16, 0));
        assertThat(DisplayTime.displayToUtc(displayDate.atTime(23, 59, 59)))
                .isEqualTo(LocalDateTime.of(2026, 9, 17, 15, 59, 59));
    }
}
