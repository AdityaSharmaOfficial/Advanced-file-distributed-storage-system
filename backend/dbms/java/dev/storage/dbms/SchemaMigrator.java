package dev.storage.dbms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Applies SQL scripts from sql/ in plain JDBC, once each, recorded in schema_migrations.
 * The advisory lock keeps two coordinator instances from migrating at the same time.
 */
@Component
class SchemaMigrator implements ApplicationRunner {

    private static final String VERSION = "V1__schema";
    private static final long MIGRATION_LOCK_KEY = 4242L;

    private final DataSource dataSource;

    SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lock = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, MIGRATION_LOCK_KEY);
                lock.execute();
            }
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS schema_migrations ("
                        + "version TEXT PRIMARY KEY, "
                        + "applied_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            }
            if (!isApplied(conn, VERSION)) {
                String script = readScript("sql/" + VERSION + ".sql");
                try (Statement st = conn.createStatement()) {
                    st.execute(script);
                }
                try (PreparedStatement record = conn.prepareStatement(
                        "INSERT INTO schema_migrations (version) VALUES (?)")) {
                    record.setString(1, VERSION);
                    record.executeUpdate();
                }
            }
            conn.commit();
        }
    }

    private static boolean isApplied(Connection conn, String version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String readScript(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
