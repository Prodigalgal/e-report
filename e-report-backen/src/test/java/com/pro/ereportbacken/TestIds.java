package com.pro.ereportbacken;

import java.util.UUID;

public final class TestIds {
    private static final long BASE_TIMESTAMP_MILLIS = 1_700_000_000_000L;

    private TestIds() {
    }

    public static UUID uuid(long seed) {
        long timestamp = BASE_TIMESTAMP_MILLIS + seed;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | (seed & 0x0fffl);
        long leastSignificantBits = 0x8000000000000000L | seed;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
