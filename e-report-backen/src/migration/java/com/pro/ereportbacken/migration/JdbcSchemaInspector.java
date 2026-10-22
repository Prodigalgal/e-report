package com.pro.ereportbacken.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class JdbcSchemaInspector {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private JdbcSchemaInspector() {
    }

    static Set<String> tableNames(Connection connection) throws SQLException {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                String schema = result.getString("TABLE_SCHEM");
                if (schema == null || schema.equalsIgnoreCase("public") || schema.equalsIgnoreCase(connection.getCatalog())) {
                    names.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(names);
    }

    static List<Column> columns(Connection connection, String tableName) throws SQLException {
        requireSafeIdentifier(tableName);
        Map<Integer, Column> columns = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, tableName, "%")) {
            while (result.next()) {
                int position = result.getInt("ORDINAL_POSITION");
                columns.put(position, new Column(
                        result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                        result.getInt("DATA_TYPE"),
                        result.getString("TYPE_NAME").toLowerCase(Locale.ROOT),
                        "YES".equalsIgnoreCase(result.getString("IS_NULLABLE"))));
            }
        }
        if (columns.isEmpty()) {
            try (ResultSet result = metadata.getColumns(null, "public", tableName, "%")) {
                while (result.next()) {
                    int position = result.getInt("ORDINAL_POSITION");
                    columns.put(position, new Column(
                            result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                            result.getInt("DATA_TYPE"),
                            result.getString("TYPE_NAME").toLowerCase(Locale.ROOT),
                            "YES".equalsIgnoreCase(result.getString("IS_NULLABLE"))));
                }
            }
        }
        return columns.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    static List<String> primaryKeyColumns(Connection connection, String tableName) throws SQLException {
        requireSafeIdentifier(tableName);
        List<PrimaryKeyColumn> columns = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getPrimaryKeys(connection.getCatalog(), null, tableName)) {
            while (result.next()) {
                columns.add(new PrimaryKeyColumn(
                        result.getShort("KEY_SEQ"),
                        result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)));
            }
        }
        if (columns.isEmpty()) {
            try (ResultSet result = metadata.getPrimaryKeys(null, "public", tableName)) {
                while (result.next()) {
                    columns.add(new PrimaryKeyColumn(
                            result.getShort("KEY_SEQ"),
                            result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)));
                }
            }
        }
        return columns.stream()
                .sorted(Comparator.comparingInt(PrimaryKeyColumn::sequence))
                .map(PrimaryKeyColumn::name)
                .toList();
    }

    static void requireSafeIdentifier(String identifier) {
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("检测到不安全的数据库标识符：" + identifier);
        }
    }

    record Column(String name, int jdbcType, String typeName, boolean nullable) {
        Column {
            requireSafeIdentifier(name);
        }

        boolean isUuid() {
            return "uuid".equals(typeName);
        }

        boolean isJson() {
            return "json".equals(typeName) || "jsonb".equals(typeName);
        }
    }

    private record PrimaryKeyColumn(int sequence, String name) {
    }
}
