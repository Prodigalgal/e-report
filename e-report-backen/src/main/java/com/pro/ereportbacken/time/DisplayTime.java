package com.pro.ereportbacken.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 用户可见时间的统一转换入口。
 *
 * <p>数据库中的 {@link LocalDateTime} 按 UTC 保存，不能依赖 JVM 默认时区。
 * 只有在生成邮件、电子表格等用户可见内容时才通过本类转换为 UTC+8。</p>
 */
public final class DisplayTime {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private DisplayTime() {
    }

    public static ZonedDateTime utcToDisplay(LocalDateTime utcValue) {
        return utcValue == null
                ? null
                : utcValue.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE_ID);
    }

    public static LocalDateTime utcLocalDateTime(LocalDateTime utcValue) {
        ZonedDateTime displayValue = utcToDisplay(utcValue);
        return displayValue == null ? null : displayValue.toLocalDateTime();
    }

    public static LocalDateTime displayToUtc(LocalDateTime displayValue) {
        return displayValue == null
                ? null
                : displayValue.atZone(ZONE_ID).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime startOfDisplayDateUtc(LocalDate displayDate) {
        return displayToUtc(displayDate.atStartOfDay());
    }

    public static String formatUtc(LocalDateTime utcValue, DateTimeFormatter formatter) {
        ZonedDateTime displayValue = utcToDisplay(utcValue);
        return displayValue == null ? "" : formatter.format(displayValue);
    }
}
