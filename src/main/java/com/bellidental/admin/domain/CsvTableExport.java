package com.bellidental.admin.domain;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reglas puras de la exportación CSV: qué tablas se vuelcan, cómo se llaman sus entradas y qué SELECT se usa.
 */
public final class CsvTableExport {

    private static final String FLYWAY_TABLE = "flyway_schema_history";
    private static final String PASSWORD_HASH_COLUMN = "password_hash";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private CsvTableExport() {
    }

    public static List<String> exportableTables(Collection<String> tableNames) {
        return tableNames.stream()
                .filter(name -> !FLYWAY_TABLE.equals(name))
                .sorted()
                .toList();
    }

    public static String entryName(String tableName) {
        return tableName + ".csv";
    }

    public static String copySql(String tableName, List<String> columnNames) {
        String columns = columnNames.stream()
                .map(column -> PASSWORD_HASH_COLUMN.equals(column)
                        ? "NULL AS " + quote(column)
                        : quote(column))
                .collect(Collectors.joining(", "));
        return "COPY (SELECT " + columns + " FROM " + quote(tableName) + ") TO STDOUT WITH (FORMAT csv, HEADER)";
    }

    public static String exportFileName(OffsetDateTime clinicNow) {
        return "bellidental-export-" + clinicNow.format(FILE_TIMESTAMP) + ".zip";
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
