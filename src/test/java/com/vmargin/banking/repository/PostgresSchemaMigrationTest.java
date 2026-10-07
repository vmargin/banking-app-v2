package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.util.JdbcSchemaMigrator;
import com.vmargin.banking.util.PinHasher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PostgresSchemaMigrationTest {
    private static final LocalDateTime ORIGINAL_V1_TIME = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @Test
    void isolatedPostgresMigratesLegacySchemaAndSeedsWithoutOverwritingExistingIdentities() throws Exception {
        Assumptions.assumeTrue(IsolatedPostgres.enabled()
            && System.getenv("BANKING_DB_USER") != null && System.getenv("BANKING_DB_PASSWORD") != null,
            "Requires explicitly enabled isolated local PostgreSQL test database");
        try (Connection connection = DriverManager.getConnection(System.getenv("BANKING_DB_URL"),
            System.getenv("BANKING_DB_USER"), System.getenv("BANKING_DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                String isolatedSchema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + isolatedSchema);
                    statement.execute("SET LOCAL search_path TO " + isolatedSchema);
                    createLegacySchema(statement);
                }
                setOriginalV1Time(connection);

                new JdbcSchemaMigrator().migrate(connection);
                assertLegacyIdentity(connection);
                assertEquals(36, columnWidth(connection, "transactions", "reference"));
                assertEquals(100, columnWidth(connection, "users", "pin"));
                assertEquals(5, scalar(connection, "SELECT COUNT(*) FROM schema_migrations"));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM transactions"));
                assertOriginalV1Time(connection);

                JdbcDemoSeedRepository seeder = new JdbcDemoSeedRepository();
                assertTrue(seeder.seedAdministratorIfMissing(connection));
                assertFalse(seeder.seedAdministratorIfMissing(connection));
                String changedHash = PinHasher.hash("5678");
                try (var update = connection.prepareStatement(
                    "UPDATE users SET pin=?, full_name='Changed administrator', role='USER', balance=42.50 "
                        + "WHERE mobile_number='09990000000'")) {
                    update.setString(1, changedHash);
                    assertEquals(1, update.executeUpdate());
                }
                assertFalse(seeder.seedAdministratorIfMissing(connection));
                assertAdministrator(connection, changedHash);

                new JdbcSchemaMigrator().migrate(connection);
                new JdbcSchemaMigrator().migrate(connection);
                assertLegacyIdentity(connection);
                assertAdministrator(connection, changedHash);
                assertEquals(5, scalar(connection, "SELECT COUNT(*) FROM schema_migrations"));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM transactions"));
                assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM money_operations"));
                assertOriginalV1Time(connection);
            } finally {
                // PostgreSQL transactional DDL removes only this test's uncommitted schema and rows.
                connection.rollback();
            }
        }
    }

    private void createLegacySchema(Statement statement) throws Exception {
        statement.execute("""
            CREATE TABLE users (
                id BIGSERIAL PRIMARY KEY,
                mobile_number VARCHAR(20) NOT NULL UNIQUE,
                pin VARCHAR(20) NOT NULL,
                full_name VARCHAR(120) NOT NULL,
                balance NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0)
            )
            """);
        statement.execute("""
            CREATE TABLE transactions (
                id BIGSERIAL PRIMARY KEY,
                user_id BIGINT NOT NULL REFERENCES users(id),
                type VARCHAR(30) NOT NULL,
                amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
                details VARCHAR(255) NOT NULL,
                occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        statement.execute("""
            CREATE TABLE schema_migrations (
                version INTEGER PRIMARY KEY,
                applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        statement.execute("INSERT INTO schema_migrations(version) VALUES (1)");
        statement.execute("""
            INSERT INTO users(mobile_number, pin, full_name, balance)
            VALUES ('09990000003', '5678', 'Changed Demo Name', 42.50)
            """);
        statement.execute("""
            INSERT INTO transactions(user_id, type, amount, details, occurred_at)
            VALUES (1, 'CASH_IN', 42.50, 'Historical', TIMESTAMP '2026-01-01 00:00:00')
            """);
    }

    private void setOriginalV1Time(Connection connection) throws Exception {
        try (var update = connection.prepareStatement("UPDATE schema_migrations SET applied_at=? WHERE version=1")) {
            update.setTimestamp(1, Timestamp.valueOf(ORIGINAL_V1_TIME));
            assertEquals(1, update.executeUpdate());
        }
    }

    private void assertLegacyIdentity(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
            "SELECT pin, full_name, role, balance FROM users WHERE mobile_number='09990000003'")) {
            assertTrue(result.next());
            assertEquals("5678", result.getString("pin"));
            assertEquals("Changed Demo Name", result.getString("full_name"));
            assertEquals("USER", result.getString("role"));
            assertEquals("42.50", result.getBigDecimal("balance").toPlainString());
            assertFalse(result.next());
        }
    }

    private void assertAdministrator(Connection connection, String expectedPin) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
            "SELECT pin, full_name, role, balance FROM users WHERE mobile_number='09990000000'")) {
            assertTrue(result.next());
            assertEquals(expectedPin, result.getString("pin"));
            assertEquals("Changed administrator", result.getString("full_name"));
            assertEquals("USER", result.getString("role"));
            assertEquals("42.50", result.getBigDecimal("balance").toPlainString());
            assertFalse(result.next());
        }
    }

    private void assertOriginalV1Time(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT applied_at FROM schema_migrations WHERE version=1")) {
            assertTrue(result.next());
            assertEquals(ORIGINAL_V1_TIME, result.getTimestamp(1).toLocalDateTime());
            assertFalse(result.next());
        }
    }

    private int columnWidth(Connection connection, String table, String column) throws Exception {
        try (var result = connection.getMetaData().getColumns(
            connection.getCatalog(), connection.getSchema(), table, column)) {
            assertTrue(result.next());
            return result.getInt("COLUMN_SIZE");
        }
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
