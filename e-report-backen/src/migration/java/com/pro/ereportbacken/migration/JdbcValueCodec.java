package com.pro.ereportbacken.migration;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

final class JdbcValueCodec {

    private static final HexFormat HEX = HexFormat.of();

    private JdbcValueCodec() {
    }

    static Object convertForTarget(Object sourceValue, JdbcSchemaInspector.Column targetColumn) {
        if (sourceValue == null) {
            return null;
        }
        if (targetColumn.isUuid()) {
            return toUuid(sourceValue);
        }
        if (targetColumn.jdbcType() == Types.BOOLEAN || targetColumn.jdbcType() == Types.BIT) {
            return toBoolean(sourceValue);
        }
        if (targetColumn.isJson()) {
            return String.valueOf(sourceValue);
        }
        return sourceValue;
    }

    static void bind(PreparedStatement statement, int index, Object value, JdbcSchemaInspector.Column targetColumn)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, targetColumn.isUuid() || targetColumn.isJson()
                    ? Types.OTHER
                    : targetColumn.jdbcType());
        } else if (targetColumn.isJson()) {
            statement.setObject(index, value, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length != 16) {
                throw new IllegalArgumentException("UUID 二进制值必须正好为 16 字节，实际为 " + bytes.length + "。 ");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    static String canonicalValue(Object value) {
        if (value == null) {
            return "N";
        }
        if (value instanceof UUID uuid) {
            return "U" + uuid.toString().replace("-", "");
        }
        if (value instanceof byte[] bytes) {
            return bytes.length == 16
                    ? "U" + toUuid(bytes).toString().replace("-", "")
                    : "B" + HEX.formatHex(bytes);
        }
        if (value instanceof Timestamp timestamp) {
            return "T" + timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (value instanceof LocalDateTime dateTime) {
            return "T" + dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (value instanceof Date date) {
            return "D" + date.toLocalDate();
        }
        if (value instanceof Time time) {
            return "M" + time.toLocalTime();
        }
        if (value instanceof BigDecimal decimal) {
            return "D" + decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return "D" + number;
        }
        if (value instanceof Boolean bool) {
            return bool ? "Z1" : "Z0";
        }
        return "S" + value;
    }

    static void updateDigest(MessageDigest digest, String columnName, Object value) {
        updateLengthPrefixed(digest, columnName);
        updateLengthPrefixed(digest, canonicalValue(value));
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256。", exception);
        }
    }

    static String finishDigest(MessageDigest digest) {
        return HEX.formatHex(digest.digest());
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof byte[] bytes && bytes.length == 1) {
            return bytes[0] != 0;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static void updateLengthPrefixed(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
