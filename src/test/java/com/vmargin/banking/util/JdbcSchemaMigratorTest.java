package com.vmargin.banking.util;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.JdbcUserRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSchemaMigratorTest {
    private Connection connection;
    private final LocalDateTime originalTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DatabaseConnection.useLocalH2(url);
        connection = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            connection.close();
        } finally {
            DatabaseConnection.clearLocalH2();
        }
    }

    @Test
    void freshSetupCreatesBothVersionsAndCompleteEmptySchema() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM users"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM money_operations"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM notification_reads"));
        assertEquals(32, columnWidth("transactions", "category"));
        assertEquals(100, columnWidth("users", "pin"));
        assertTrue(connection.getAutoCommit());
        assertThrows(SQLException.class, () -> sql("""
            INSERT INTO users(mobile_number, pin, full_name, role) VALUES ('09990000001','1234','Demo','UNKNOWN')
            """));
    }

    @Test
    void currentV1MarkerKeepsItsOriginalAppliedTimestamp() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        sql("DELETE FROM schema_migrations WHERE version >= 2");
        setOriginalV1Time();
        new JdbcSchemaMigrator().migrate(connection);
        assertOriginalV1Time();
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
    }

    @Test
    void v12UpgradeAddsAndBackfillsTransactionCategories() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        sql("DELETE FROM schema_migrations WHERE version = 13");
        sql("ALTER TABLE transactions DROP CONSTRAINT valid_transaction_category");
        sql("ALTER TABLE transactions DROP COLUMN category");
        var owner = new JdbcUserRepository().save(new User(0, "09990000031", "1234", "Legacy owner",
            new BankAccount("PENDING", "Legacy owner", BigDecimal.ZERO)));
        insertVersionTwelveTransaction(owner, "CASH_IN");
        insertVersionTwelveTransaction(owner, "BILL_PAYMENT");
        insertVersionTwelveTransaction(owner, "CARD_PURCHASE");

        new JdbcSchemaMigrator().migrate(connection);

        assertEquals(13, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals("INCOME", category("CASH_IN"));
        assertEquals("BILLS_AND_UTILITIES", category("BILL_PAYMENT"));
        assertEquals("OTHER", category("CARD_PURCHASE"));
    }

    @Test
    void upgradesAnExistingV2DatabaseWithTheAdditiveRecipientTable() throws Exception {
        createVersionTwoSchema();
        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertTrue(columnWidth("saved_recipients", "recipient_mobile") >= 11);
        assertTrue(indexExists("saved_recipients", "idx_saved_recipients_owner"));
    }

    @Test
    void v3UpgradePreservesBusinessRowsMarkerTimesAndAddsExpandedLedgerConstraintFirst() throws Exception {
        createVersionThreeSchema();
        sql("INSERT INTO users(mobile_number,pin,full_name,balance) VALUES ('09990000001','hash','Legacy',75.00)");
        sql("INSERT INTO transactions(user_id,type,amount,details) VALUES (1,'CASH_IN',75.00,'Keep cash')");
        for (int version = 1; version <= 3; version++) {
            try (var update = connection.prepareStatement(
                "UPDATE schema_migrations SET applied_at=? WHERE version=?")) {
                update.setTimestamp(1, Timestamp.valueOf(originalTime.plusDays(version)));
                update.setInt(2, version);
                update.executeUpdate();
            }
        }
        new JdbcSchemaMigrator().migrate(connection);

        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM users"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM savings_goals"));
        for (int version = 1; version <= 3; version++) {
            try (var query = connection.prepareStatement("SELECT applied_at FROM schema_migrations WHERE version=?")) {
                query.setInt(1, version);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(originalTime.plusDays(version), result.getTimestamp(1).toLocalDateTime());
                }
            }
        }
        long accountId = scalar("SELECT id FROM accounts WHERE owner_id=1 AND is_primary=TRUE");
        sql("INSERT INTO transactions(user_id,account_id,type,amount,details) "
            + "VALUES (1," + accountId + ",'SAVINGS_CONTRIBUTION',1.00,'Goal contribution')");
        assertThrows(SQLException.class, () -> sql("INSERT INTO transactions(user_id,account_id,type,amount,details) "
            + "VALUES (1," + accountId + ",'UNKNOWN_TYPE',1.00,'Invalid type')"));
    }

    @Test
    void latestSchemaRejectsSavingsGoalSavedAmountWithWrongDecimalScale() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        replaceSavingsGoalsForVerificationTest(0, true);

        assertThrows(SQLException.class, () -> new JdbcSchemaMigrator().migrate(connection));
    }

    @Test
    void latestSchemaRejectsSavingsGoalsWithoutIdPrimaryKey() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        replaceSavingsGoalsForVerificationTest(2, false);

        assertThrows(SQLException.class, () -> new JdbcSchemaMigrator().migrate(connection));
    }

    private void replaceSavingsGoalsForVerificationTest(int savedScale, boolean includePrimaryKey) throws Exception {
        sql("DROP TABLE savings_goals");
        String primaryKey = includePrimaryKey ? " PRIMARY KEY" : "";
        sql("CREATE TABLE savings_goals (id BIGINT GENERATED BY DEFAULT AS IDENTITY" + primaryKey + ", "
            + "owner_id BIGINT NOT NULL REFERENCES users(id), name VARCHAR(40) NOT NULL, "
            + "target_amount NUMERIC(15,2) NOT NULL, saved_amount NUMERIC(15," + savedScale
            + ") NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "CONSTRAINT savings_goals_name_length CHECK (length(trim(name)) BETWEEN 1 AND 40), "
            + "CONSTRAINT savings_goals_target_positive CHECK (target_amount > 0), "
            + "CONSTRAINT savings_goals_saved_nonnegative CHECK (saved_amount >= 0))");
        sql("CREATE INDEX idx_savings_goals_owner ON savings_goals(owner_id, created_at)");
    }

    @Test
    void markedLegacyV1RepairsMissingCoreColumnsTablesAndIndexes() throws Exception {
        legacySchema(20);
        sql("INSERT INTO users(mobile_number,pin,full_name,balance) VALUES ('09990000001','5678','Legacy',42.50)");
        sql("""
            INSERT INTO transactions(user_id,type,amount,details,occurred_at)
            VALUES (1,'CASH_IN',42.50,'Historical',TIMESTAMP '2026-01-01 00:00:00')
            """);
        new JdbcSchemaMigrator().migrate(connection);
        assertOriginalV1Time();
        assertEquals(100, columnWidth("users", "pin"));
        assertEquals(36, columnWidth("transactions", "reference"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM money_operations"));
        try (var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT pin,role,balance FROM users")) {
            assertTrue(result.next());
            assertEquals("5678", result.getString("pin"));
            assertEquals("USER", result.getString("role"));
            assertEquals(new BigDecimal("42.50"), result.getBigDecimal("balance"));
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM transactions"));
        assertTrue(indexExists("money_operations", "idx_money_operations_owner_id"));
        assertFalse(indexNonUnique("idx_transactions_operation"));
        assertTrue(indexExists("transactions", "idx_transactions_user_id"));
    }

    @Test
    void rerunPreservesChangedProfileHashBalanceAndLedgerAndNeverNarrowsWidePin() throws Exception {
        legacySchema(200);
        new JdbcSchemaMigrator().migrate(connection);
        String hash = PinHasher.hash("5678");
        User user = new JdbcUserRepository().save(new User(0, "09990000001", hash, "Changed",
            new BankAccount("PENDING", "Changed", new BigDecimal("99.50"))));
        try (var update = connection.prepareStatement("UPDATE users SET role='ADMIN' WHERE id=?")) {
            update.setLong(1, user.getId());
            update.executeUpdate();
        }
        try (var insert = connection.prepareStatement("""
            INSERT INTO transactions(user_id,account_id,type,amount,details,occurred_at)
            VALUES (?,?,'CASH_IN',99.50,'Keep history',TIMESTAMP '2026-01-01 00:00:00')
            """)) {
            insert.setLong(1, user.getId());
            insert.setLong(2, user.getBankAccount().getId());
            insert.executeUpdate();
        }
        new JdbcSchemaMigrator().migrate(connection);
        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(200, columnWidth("users", "pin"));
        assertOriginalV1Time();
        try (var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT full_name,pin,role,balance FROM users")) {
            assertTrue(result.next());
            assertEquals("Changed", result.getString("full_name"));
            assertEquals(hash, result.getString("pin"));
            assertEquals("ADMIN", result.getString("role"));
            assertEquals(new BigDecimal("99.50"), result.getBigDecimal("balance"));
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
    }

    @Test
    void injectedDdlFailureLeavesNoV2MarkerAndRetryFinishesSafely() throws Exception {
        legacySchema(20);
        AtomicBoolean failed = new AtomicBoolean();
        JdbcSchemaMigrator failing = new JdbcSchemaMigrator((target, statementText) -> {
            try (var statement = target.createStatement()) {
                statement.execute(statementText);
            }
            if (statementText.contains("ADD COLUMN reference") && failed.compareAndSet(false, true)) {
                throw new SQLException("Injected after implicitly committed H2 DDL");
            }
        });
        assertThrows(SQLException.class, () -> failing.migrate(connection));
        assertTrue(failed.get());
        assertEquals(1, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertOriginalV1Time();
        assertEquals(36, columnWidth("transactions", "reference"));
        new JdbcSchemaMigrator().migrate(connection);
        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertOriginalV1Time();
    }

    @Test
    void freshPartialDdlFailureDoesNotRecordV1UntilCompletePostconditionsPass() throws Exception {
        JdbcSchemaMigrator failing = new JdbcSchemaMigrator((target, statementText) -> {
            try (var statement = target.createStatement()) {
                statement.execute(statementText);
            }
            if (statementText.startsWith("CREATE TABLE IF NOT EXISTS users")) {
                throw new SQLException("Injected fresh bootstrap interruption");
            }
        });
        assertThrows(SQLException.class, () -> failing.migrate(connection));
        assertEquals(0, scalar("SELECT COUNT(*) FROM schema_migrations"));
        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT COUNT(*) FROM schema_migrations"));
    }

    @Test
    void interruptedV5ConstraintReplacementRetriesAfterH2ImplicitDdlCommit() throws Exception {
        new JdbcSchemaMigrator().migrate(connection);
        sql("DELETE FROM schema_migrations WHERE version >= 5");
        sql("ALTER TABLE transactions DROP CONSTRAINT valid_transaction_type_v10");
        sql("ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type_v4 CHECK "
            + "(type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'SAVINGS_CONTRIBUTION', "
            + "'SAVINGS_WITHDRAWAL'))");

        AtomicBoolean failed = new AtomicBoolean();
        JdbcSchemaMigrator interrupted = new JdbcSchemaMigrator((target, statementText) -> {
            try (var statement = target.createStatement()) {
                statement.execute(statementText);
            }
            if (statementText.contains("DROP CONSTRAINT valid_transaction_type_v4")
                && failed.compareAndSet(false, true)) {
                throw new SQLException("Injected after H2 committed v5 constraint replacement");
            }
        });

        assertThrows(SQLException.class, () -> interrupted.migrate(connection));
        assertTrue(failed.get());
        assertEquals(4, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = 'transactions' AND constraint_name = 'valid_transaction_type_v5'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = 'transactions' AND constraint_name = 'valid_transaction_type_v4'"));

        new JdbcSchemaMigrator().migrate(connection);
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, scalar("SELECT MAX(version) FROM schema_migrations"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = 'transactions' AND constraint_name = 'valid_transaction_type_v10'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = 'transactions' AND constraint_name = 'valid_transaction_type_v5'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.table_constraints "
            + "WHERE table_name = 'transactions' AND constraint_name = 'valid_transaction_type_v4'"));
    }

    @Test
    void refusesFutureVersionsAndMigrationGapsBeforeRepairingSchema() throws Exception {
        legacySchema(20);
        sql("INSERT INTO schema_migrations(version) VALUES (6)");
        assertThrows(SQLException.class, () -> new JdbcSchemaMigrator().migrate(connection));
        assertEquals(20, columnWidth("users", "pin"));
        sql("DELETE FROM schema_migrations");
        sql("INSERT INTO schema_migrations(version) VALUES (3)");
        assertThrows(SQLException.class, () -> new JdbcSchemaMigrator().migrate(connection));
        assertEquals(20, columnWidth("users", "pin"));
        assertEquals(-1, columnWidth("users", "role"));
    }

    @Test
    void refusesIncorrectExistingOperationIndexWithoutRecordingRepairVersion() throws Exception {
        legacySchema(20);
        sql("ALTER TABLE transactions ADD COLUMN reference VARCHAR(36)");
        sql("CREATE INDEX idx_transactions_operation ON transactions(user_id, reference)");
        assertThrows(SQLException.class, () -> new JdbcSchemaMigrator().migrate(connection));
        assertEquals(1, scalar("SELECT COUNT(*) FROM schema_migrations"));
        assertOriginalV1Time();
    }

    private void createVersionTwoSchema() throws Exception {
        legacySchema(100);
        sql("ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'");
        sql("ALTER TABLE users ADD CONSTRAINT valid_user_role CHECK (role IN ('ADMIN','USER'))");
        sql("ALTER TABLE transactions ADD COLUMN reference VARCHAR(36)");
        sql("ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type CHECK "
            + "(type IN ('CASH_IN','TRANSFER_SENT','TRANSFER_RECEIVED'))");
        sql("CREATE TABLE money_operations(reference VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL "
            + "REFERENCES users(id), kind VARCHAR(30) NOT NULL, "
            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        sql("CREATE INDEX idx_money_operations_owner_id ON money_operations(owner_id)");
        sql("CREATE UNIQUE INDEX idx_transactions_operation ON transactions(user_id, reference)");
        sql("CREATE INDEX idx_transactions_user_id ON transactions(user_id)");
        sql("INSERT INTO schema_migrations(version) VALUES (2)");
    }

    private void createVersionThreeSchema() throws Exception {
        createVersionTwoSchema();
        sql("CREATE TABLE saved_recipients(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
            + "owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
            + "recipient_mobile VARCHAR(11) NOT NULL REFERENCES users(mobile_number), "
            + "label VARCHAR(40) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "CONSTRAINT saved_recipients_label_length CHECK (length(trim(label)) BETWEEN 1 AND 40), "
            + "CONSTRAINT saved_recipients_owner_mobile UNIQUE (owner_id, recipient_mobile))");
        sql("CREATE INDEX idx_saved_recipients_owner ON saved_recipients(owner_id, label)");
        sql("INSERT INTO schema_migrations(version) VALUES (3)");
    }

    private void legacySchema(int pinWidth) throws Exception {
        sql("CREATE TABLE users(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
            + "mobile_number VARCHAR(20) NOT NULL UNIQUE, pin VARCHAR(" + pinWidth + ") NOT NULL, "
            + "full_name VARCHAR(120) NOT NULL, balance NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK(balance>=0))");
        sql("CREATE TABLE transactions(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, "
            + "user_id BIGINT NOT NULL REFERENCES users(id), type VARCHAR(30) NOT NULL, "
            + "amount NUMERIC(15,2) NOT NULL CHECK(amount>0), details VARCHAR(255) NOT NULL, "
            + "occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        sql("CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY, "
            + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        sql("INSERT INTO schema_migrations(version) VALUES(1)");
        setOriginalV1Time();
    }

    private void setOriginalV1Time() throws Exception {
        try (var statement = connection.prepareStatement("UPDATE schema_migrations SET applied_at=? WHERE version=1")) {
            statement.setTimestamp(1, Timestamp.valueOf(originalTime));
            statement.executeUpdate();
        }
    }

    private void assertOriginalV1Time() throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT applied_at FROM schema_migrations WHERE version=1")) {
            assertTrue(result.next());
            assertEquals(originalTime, result.getTimestamp(1).toLocalDateTime());
        }
    }

    private int columnWidth(String table, String column) throws Exception {
        try (var result = connection.getMetaData().getColumns(connection.getCatalog(), "public", table, column)) {
            return result.next() ? result.getInt("COLUMN_SIZE") : -1;
        }
    }

    private boolean indexExists(String table, String name) throws Exception {
        try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), "public",
            table, false, false)) {
            while (result.next()) {
                if (name.equals(result.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean indexNonUnique(String name) throws Exception {
        try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), "public",
            "transactions", false, false)) {
            while (result.next()) {
                if (name.equals(result.getString("INDEX_NAME"))) {
                    return result.getBoolean("NON_UNIQUE");
                }
            }
        }
        throw new IllegalStateException("Index missing");
    }

    private long scalar(String text) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(text)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void insertVersionTwelveTransaction(User owner, String type) throws Exception {
        try (var statement = connection.prepareStatement("""
            INSERT INTO transactions(user_id, account_id, type, amount, details)
            VALUES (?, ?, ?, 1.00, 'Legacy entry')
            """)) {
            statement.setLong(1, owner.getId());
            statement.setLong(2, owner.getBankAccount().getId());
            statement.setString(3, type);
            statement.executeUpdate();
        }
    }

    private String category(String type) throws Exception {
        try (var statement = connection.prepareStatement("SELECT category FROM transactions WHERE type = ?")) {
            statement.setString(1, type);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void sql(String text) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(text);
        }
    }
}
