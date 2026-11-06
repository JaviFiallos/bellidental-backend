package com.bellidental.admin.application;

import com.bellidental.admin.domain.CsvTableExport;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private static final String README_ENTRY = "README.txt";
    private static final String README_ENCODING_NOTE = "CSV UTF-8 con cabecera; ids uuid; fechas ISO 8601 con zona; password_hash vacío";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ClinicClock clinicClock;
    private final CurrentUser currentUser;

    /**
     * Escribe un ZIP con README.txt y un CSV por tabla, todo desde la misma transacción REPEATABLE READ.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(OutputStream output) throws IOException {
        Instant start = Instant.now();
        try {
            List<String> tables = CsvTableExport.exportableTables(findPublicBaseTables());
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                ZipOutputStream zip = new ZipOutputStream(output);
                writeReadme(zip, tables.size());
                for (String table : tables) {
                    writeTable(zip, pgConnection, table);
                }
                zip.finish();
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
            log.info("Exportación completada: userId={}, tablas={}, duración={} ms",
                    currentUser.userId(), tables.size(), Duration.between(start, Instant.now()).toMillis());
        } catch (SQLException e) {
            log.error("Exportación fallida a mitad: userId={}", currentUser.userId(), e);
            throw new DataAccessResourceFailureException("No se pudo exportar la base de datos", e);
        } catch (IOException | RuntimeException e) {
            log.error("Exportación fallida a mitad: userId={}", currentUser.userId(), e);
            throw e;
        }
    }

    private List<String> findPublicBaseTables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class);
    }

    private List<String> findColumns(String table) {
        return jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
                """, String.class, table);
    }

    private void writeReadme(ZipOutputStream zip, int tableCount) throws IOException {
        String flywayVersion = jdbcTemplate.queryForObject(
                "SELECT max(version) FROM flyway_schema_history WHERE success", String.class);
        String readme = String.join("\n",
                "Exportación de Bellidental",
                "Fecha y hora: " + clinicClock.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "Versión de Flyway: " + flywayVersion,
                "Número de tablas: " + tableCount,
                README_ENCODING_NOTE,
                "");
        zip.putNextEntry(new ZipEntry(README_ENTRY));
        zip.write(readme.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void writeTable(ZipOutputStream zip, PGConnection pgConnection, String table)
            throws IOException, SQLException {
        String sql = CsvTableExport.copySql(table, findColumns(table));
        zip.putNextEntry(new ZipEntry(CsvTableExport.entryName(table)));
        pgConnection.getCopyAPI().copyOut(sql, new NonClosingOutputStream(zip));
        zip.closeEntry();
    }

    /** Evita que CopyManager cierre el ZIP entre tablas. */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
