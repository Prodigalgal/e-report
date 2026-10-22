package com.pro.ereportbacken.time;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** UTC LocalDateTime 是数据库和 API 的时间戳契约。 */
public final class UtcTime {

    private UtcTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
