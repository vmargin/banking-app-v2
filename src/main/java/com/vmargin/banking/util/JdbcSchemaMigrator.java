package com.vmargin.banking.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explicit additive schema upgrades. Normal application startup must not invoke this for PostgreSQL. */
public final class JdbcSchemaMigrator {
    public static final int LATEST_VERSION = 13;
    private static final long ADVISORY_LOCK = 7382145106L;
    private final SqlExecutor executor;

    public JdbcSchemaMigrator() {
        this((connection, sql) -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
    }

    JdbcSchemaMigrator(SqlExecutor executor) {
        this.executor = executor;
    }

    /** Caller closes the connection. Caller-managed PostgreSQL transactions remain caller-owned. */
    public synchronized void migrate(Connection connection) throws SQLException {
        synchronized (JdbcSchemaMigrator.class) {
            Dialect dialect = dialect(connection);
            boolean ownsTransaction = connection.getAutoCommit();
            if (!ownsTransaction && dialect == Dialect.H2) {
                throw new SQLException("H2 migrations require a standalone connection because DDL commits implicitly");
            }
            if (ownsTransaction) {
                connection.setAutoCommit(false);
            }
            Savepoint savepoint = ownsTransaction ? null : connection.setSavepoint();
            try {
                if (dialect == Dialect.POSTGRESQL) {
                    try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                        lock.setLong(1, ADVISORY_LOCK);
                        lock.executeQuery().close();
                    }
                }
                int version = readVersion(connection);
                if (version == LATEST_VERSION) {
                    verifyCore(connection);
                    verifySavedRecipients(connection);
                    verifySavingsGoals(connection);
                    verifyTransactionTypeV10(connection);
                    verifyAccounts(connection);
                    verifyTransactionAccounts(connection);
                    verifyCards(connection);
                    verifyLocalRequests(connection);
                    verifyNotificationReads(connection);
                    verifyTransactionCategory(connection);
                    finish(connection, ownsTransaction, savepoint);
                    return;
                }
                execute(connection, """
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                if (version == 0) {
                    createCore(connection, dialect);
                    verifyCore(connection);
                    recordVersion(connection, 1);
                    version = 1;
                }
                if (version < 2) {
                    // v2 intentionally repairs already-marked v1 installations from the prior setup scripts.
                    repairCore(connection, dialect);
                    verifyCore(connection);
                    recordVersion(connection, 2);
                    version = 2;
                }
                if (version < 3) {
                    repairSavedRecipients(connection);
                    verifySavedRecipients(connection);
                    recordVersion(connection, 3);
                    version = 3;
                }
                if (version < 4) {
                    repairSavingsGoals(connection, dialect);
                    verifySavingsGoals(connection);
                    verifyTransactionTypeV4(connection);
                    recordVersion(connection, 4);
                    version = 4;
                }
                if (version < 5) {
                    repairBillPaymentType(connection);
                    verifyTransactionTypeV5(connection);
                    recordVersion(connection, 5);
                    version = 5;
                }
                if (version < 6) {
                    repairAccounts(connection, dialect);
                    verifyAccounts(connection);
                    recordVersion(connection, 6);
                    version = 6;
                }
                if (version < 7) {
                    repairTransactionAccounts(connection);
                    verifyTransactionAccounts(connection);
                    verifySavedRecipients(connection);
                    recordVersion(connection, 7);
                    version = 7;
                }
                if (version < 8) {
                    repairCards(connection, dialect);
                    verifyCards(connection);
                    recordVersion(connection, 8);
                    version = 8;
                }
                if (version < 9) {
                    repairCardControls(connection);
                    verifyCards(connection);
                    recordVersion(connection, 9);
                    version = 9;
                }
                if (version < 10) {
                    repairCardPurchaseType(connection);
                    verifyTransactionTypeV10(connection);
                    recordVersion(connection, 10);
                    version = 10;
                }
                if (version < 11) {
                    repairLocalRequests(connection, dialect);
                    verifyLocalRequests(connection);
                    recordVersion(connection, 11);
                    version = 11;
                }
                if (version < 12) {
                    repairNotificationReads(connection);
                    verifyNotificationReads(connection);
                    recordVersion(connection, 12);
                    version = 12;
                }
                if (version < 13) {
                    repairTransactionCategory(connection);
                    verifyTransactionCategory(connection);
                    recordVersion(connection, 13);
                }
                finish(connection, ownsTransaction, savepoint);
            } catch (SQLException | RuntimeException exception) {
                if (ownsTransaction) {
                    connection.rollback();
                } else {
                    connection.rollback(savepoint);
                }
                throw exception;
            } finally {
                if (ownsTransaction) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private void finish(Connection connection, boolean ownsTransaction, Savepoint savepoint) throws SQLException {
        if (ownsTransaction) {
            connection.commit();
        } else {
            connection.releaseSavepoint(savepoint);
        }
    }

    private Dialect dialect(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if ("PostgreSQL".equals(product)) {
            return Dialect.POSTGRESQL;
        }
        if ("H2".equals(product)) {
            return Dialect.H2;
        }
        throw new SQLException("Unsupported migration database dialect");
    }

    private int readVersion(Connection connection) throws SQLException {
        if (!tableExists(connection, "schema_migrations")) {
            return 0;
        }
        int expected = 1;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM schema_migrations ORDER BY version")) {
            while (result.next()) {
                int version = result.getInt(1);
                if (version > LATEST_VERSION) {
                    throw new SQLException("Database contains an unsupported future migration version");
                }
                if (version != expected) {
                    throw new SQLException("Database migration history contains a gap or invalid version");
                }
                expected++;
            }
        }
        return expected - 1;
    }

    private void createCore(Connection connection, Dialect dialect) throws SQLException {
        String identity = dialect == Dialect.POSTGRESQL ? "BIGSERIAL" : "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        execute(connection, "CREATE TABLE IF NOT EXISTS users (id " + identity + " PRIMARY KEY, "
            + "mobile_number VARCHAR(20) NOT NULL UNIQUE, pin VARCHAR(100) NOT NULL, "
            + "full_name VARCHAR(120) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'USER', "
            + "balance NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0), "
            + "CONSTRAINT valid_user_role CHECK (role IN ('ADMIN', 'USER')))");
        execute(connection, "CREATE TABLE IF NOT EXISTS transactions (id " + identity + " PRIMARY KEY, "
            + "user_id BIGINT NOT NULL REFERENCES users(id), type VARCHAR(30) NOT NULL, "
            + "amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0), details VARCHAR(255) NOT NULL, "
            + "occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, reference VARCHAR(36), "
            + "CONSTRAINT valid_transaction_type CHECK (type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED')))");
        repairCore(connection, dialect);
    }

    private void repairCore(Connection connection, Dialect dialect) throws SQLException {
        requireTable(connection, "users");
        requireTable(connection, "transactions");
        if (columnSize(connection, "users", "role") < 0) {
            execute(connection, "ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER'");
        }
        int pinWidth = columnSize(connection, "users", "pin");
        if (pinWidth < 0) {
            throw new SQLException("Existing account schema is missing credential storage");
        }
        if (pinWidth < 100) {
            execute(connection, "ALTER TABLE users ALTER COLUMN pin "
                + (dialect == Dialect.POSTGRESQL ? "TYPE " : "") + "VARCHAR(100)");
        }
        if (!checkExists(connection, "users", "valid_user_role")) {
            execute(connection, "ALTER TABLE users ADD CONSTRAINT valid_user_role CHECK (role IN ('ADMIN', 'USER'))");
        }
        if (columnSize(connection, "transactions", "reference") < 0) {
            execute(connection, "ALTER TABLE transactions ADD COLUMN reference VARCHAR(36)");
        }
        if (!checkExists(connection, "transactions", "valid_transaction_type")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type "
                + "CHECK (type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED'))");
        }
        execute(connection, """
            CREATE TABLE IF NOT EXISTS money_operations (
                reference VARCHAR(36) PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id),
                kind VARCHAR(30) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        if (!indexExists(connection, "money_operations", "idx_money_operations_owner_id")) {
            execute(connection, "CREATE INDEX idx_money_operations_owner_id ON money_operations(owner_id)");
        }
        if (!indexExists(connection, "transactions", "idx_transactions_operation")) {
            execute(connection, "CREATE UNIQUE INDEX idx_transactions_operation ON transactions(user_id, reference)");
        }
        if (!indexExists(connection, "transactions", "idx_transactions_user_id")) {
            execute(connection, "CREATE INDEX idx_transactions_user_id ON transactions(user_id)");
        }
    }

    private void verifyCore(Connection connection) throws SQLException {
        requireColumns(connection, "users", List.of("id", "mobile_number", "pin", "full_name", "role", "balance"));
        requireColumns(connection, "transactions",
            List.of("id", "user_id", "type", "amount", "details", "occurred_at", "reference"));
        requireColumns(connection, "money_operations", List.of("reference", "owner_id", "kind", "created_at"));
        if (columnSize(connection, "users", "pin") < 100
            || columnSize(connection, "transactions", "reference") < 36
            || !checkExists(connection, "users", "valid_user_role")
            || (!checkExists(connection, "transactions", "valid_transaction_type")
                && !checkExists(connection, "transactions", "valid_transaction_type_v4")
                && !checkExists(connection, "transactions", "valid_transaction_type_v5")
                && !checkExists(connection, "transactions", "valid_transaction_type_v10"))) {
            throw new SQLException("Migration account/ledger postconditions did not pass");
        }
        verifyOperationKey(connection);
        verifyIndex(connection, "idx_money_operations_owner_id", List.of("owner_id"), false, "money_operations");
        if (columnSize(connection, "transactions", "account_id") < 0) {
            verifyIndex(connection, "idx_transactions_operation", List.of("user_id", "reference"), true);
        } else {
            verifyIndex(connection, "idx_transactions_operation", List.of("user_id", "account_id", "reference"),
                true);
        }
        verifyIndex(connection, "idx_transactions_user_id", List.of("user_id"), false);
    }

    private void repairSavedRecipients(Connection connection) throws SQLException {
        execute(connection, """
            CREATE TABLE IF NOT EXISTS saved_recipients (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                recipient_mobile VARCHAR(11) NOT NULL REFERENCES users(mobile_number),
                label VARCHAR(40) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT saved_recipients_label_length CHECK (length(trim(label)) BETWEEN 1 AND 40),
                CONSTRAINT saved_recipients_owner_mobile UNIQUE (owner_id, recipient_mobile)
            )
            """);
        if (!indexExists(connection, "saved_recipients", "idx_saved_recipients_owner")) {
            execute(connection, "CREATE INDEX idx_saved_recipients_owner ON saved_recipients(owner_id, label)");
        }
    }

    private void repairSavingsGoals(Connection connection, Dialect dialect) throws SQLException {
        execute(connection, """
            CREATE TABLE IF NOT EXISTS savings_goals (
                id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id),
                name VARCHAR(40) NOT NULL,
                target_amount NUMERIC(15, 2) NOT NULL,
                saved_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT savings_goals_name_length CHECK (length(trim(name)) BETWEEN 1 AND 40),
                CONSTRAINT savings_goals_target_positive CHECK (target_amount > 0),
                CONSTRAINT savings_goals_saved_nonnegative CHECK (saved_amount >= 0)
            )
            """);
        if (!indexExists(connection, "savings_goals", "idx_savings_goals_owner")) {
            execute(connection, "CREATE INDEX idx_savings_goals_owner ON savings_goals(owner_id, created_at)");
        }
        if (!checkExists(connection, "transactions", "valid_transaction_type_v4")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type_v4 CHECK "
                + "(type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', "
                + "'SAVINGS_CONTRIBUTION', 'SAVINGS_WITHDRAWAL'))");
        }
        if (checkExists(connection, "transactions", "valid_transaction_type")) {
            execute(connection, "ALTER TABLE transactions DROP CONSTRAINT valid_transaction_type");
        }
    }

    private void verifySavingsGoals(Connection connection) throws SQLException {
        requireColumns(connection, "savings_goals",
            List.of("id", "owner_id", "name", "target_amount", "saved_amount", "created_at"));
        if (!checkExists(connection, "savings_goals", "savings_goals_name_length")
            || !checkExists(connection, "savings_goals", "savings_goals_target_positive")
            || !checkExists(connection, "savings_goals", "savings_goals_saved_nonnegative")) {
            throw new SQLException("Savings goal migration constraints did not pass");
        }
        verifyIndex(connection, "idx_savings_goals_owner", List.of("owner_id", "created_at"), false,
            "savings_goals");
        if (columnSize(connection, "savings_goals", "name") != 40
            || columnSize(connection, "savings_goals", "target_amount") != 15
            || columnSize(connection, "savings_goals", "saved_amount") != 15) {
            throw new SQLException("Savings goal migration column limits did not pass");
        }
        verifyColumn(connection, "savings_goals", "owner_id", java.sql.Types.BIGINT, false);
        verifyColumn(connection, "savings_goals", "name", java.sql.Types.VARCHAR, false);
        verifyColumn(connection, "savings_goals", "target_amount", java.sql.Types.NUMERIC, false);
        verifyColumn(connection, "savings_goals", "saved_amount", java.sql.Types.NUMERIC, false);
        verifyColumn(connection, "savings_goals", "created_at", java.sql.Types.TIMESTAMP, false);
        verifyDecimalScale(connection, "savings_goals", "target_amount", 2);
        verifyDecimalScale(connection, "savings_goals", "saved_amount", 2);
        verifyPrimaryKey(connection, "savings_goals", List.of("id"));
        verifyForeignKey(connection, "savings_goals", "owner_id", "users", "id");
    }

    private void repairBillPaymentType(Connection connection) throws SQLException {
        if (!checkExists(connection, "transactions", "valid_transaction_type_v5")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type_v5 CHECK "
                + "(type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'SAVINGS_CONTRIBUTION', "
                + "'SAVINGS_WITHDRAWAL', 'BILL_PAYMENT'))");
        }
        if (checkExists(connection, "transactions", "valid_transaction_type_v4")) {
            execute(connection, "ALTER TABLE transactions DROP CONSTRAINT valid_transaction_type_v4");
        }
    }

    private void repairAccounts(Connection connection, Dialect dialect) throws SQLException {
        String identity = dialect == Dialect.POSTGRESQL ? "BIGSERIAL" : "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        execute(connection, """
            CREATE TABLE IF NOT EXISTS accounts (
                id IDENTITY_PLACEHOLDER PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id),
                account_number VARCHAR(12) NOT NULL UNIQUE,
                account_name VARCHAR(40) NOT NULL,
                account_type VARCHAR(20) NOT NULL,
                currency_code VARCHAR(3) NOT NULL,
                balance NUMERIC(15, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
                status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
                is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT accounts_type_valid CHECK (account_type IN ('CHECKING', 'SAVINGS', 'FOREIGN_CURRENCY')),
                CONSTRAINT accounts_currency_valid CHECK (currency_code IN ('PHP', 'USD')),
                CONSTRAINT accounts_status_valid CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
                CONSTRAINT accounts_primary_valid CHECK (NOT is_primary OR
                    (account_type = 'CHECKING' AND currency_code = 'PHP')),
                CONSTRAINT accounts_owner_name_unique UNIQUE (owner_id, account_name)
            )
            """.replace("IDENTITY_PLACEHOLDER", identity));
        if (!indexExists(connection, "accounts", "idx_accounts_owner")) {
            execute(connection, "CREATE INDEX idx_accounts_owner ON accounts(owner_id, created_at)");
        }

        try (PreparedStatement users = connection.prepareStatement("""
                 SELECT u.id, u.full_name, u.balance
                 FROM users u
                 WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.owner_id = u.id AND a.is_primary = TRUE)
                 ORDER BY u.id
                 """);
             ResultSet rows = users.executeQuery()) {
            while (rows.next()) {
                com.vmargin.banking.repository.JdbcAccountRepository.insertPrimary(connection,
                    rows.getLong("id"), rows.getString("full_name"), rows.getBigDecimal("balance"));
            }
        }
    }

    private void verifyAccounts(Connection connection) throws SQLException {
        requireColumns(connection, "accounts", List.of("id", "owner_id", "account_number", "account_name",
            "account_type", "currency_code", "balance", "status", "is_primary", "created_at"));
        if (!checkExists(connection, "accounts", "accounts_type_valid")
            || !checkExists(connection, "accounts", "accounts_currency_valid")
            || !checkExists(connection, "accounts", "accounts_status_valid")
            || !checkExists(connection, "accounts", "accounts_primary_valid")
            || !indexExists(connection, "accounts", "idx_accounts_owner")) {
            throw new SQLException("Account migration constraints or index did not pass");
        }
        verifyColumn(connection, "accounts", "owner_id", java.sql.Types.BIGINT, false);
        verifyColumn(connection, "accounts", "account_number", java.sql.Types.VARCHAR, false);
        verifyColumn(connection, "accounts", "balance", java.sql.Types.NUMERIC, false);
        verifyDecimalScale(connection, "accounts", "balance", 2);
        verifyPrimaryKey(connection, "accounts", List.of("id"));
        verifyForeignKey(connection, "accounts", "owner_id", "users", "id");
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM users u
            WHERE (SELECT COUNT(*) FROM accounts a WHERE a.owner_id = u.id AND a.is_primary = TRUE) <> 1
               OR EXISTS (SELECT 1 FROM accounts a WHERE a.owner_id = u.id
                   AND length(a.account_number) <> 12)
            """);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0) {
                throw new SQLException("Each customer must have one 12-digit primary account number");
            }
        }
    }

    private void repairTransactionAccounts(Connection connection) throws SQLException {
        if (columnSize(connection, "transactions", "account_id") < 0) {
            execute(connection, "ALTER TABLE transactions ADD COLUMN account_id BIGINT");
        }
        execute(connection, """
            UPDATE transactions
            SET account_id = (SELECT a.id FROM accounts a
                WHERE a.owner_id = transactions.user_id AND a.is_primary = TRUE)
            WHERE account_id IS NULL
            """);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM transactions WHERE account_id IS NULL");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0) {
                throw new SQLException("Existing transaction rows could not be assigned to accounts");
            }
        }
        execute(connection, "ALTER TABLE transactions ALTER COLUMN account_id SET NOT NULL");
        if (!foreignKeyExists(connection, "transactions", "account_id", "accounts", "id")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT fk_transactions_account "
                + "FOREIGN KEY (account_id) REFERENCES accounts(id)");
        }
        if (!indexExists(connection, "transactions", "idx_transactions_account")) {
            execute(connection, "CREATE INDEX idx_transactions_account ON transactions(account_id, occurred_at)");
        }
        execute(connection, "DROP INDEX IF EXISTS idx_transactions_operation");
        execute(connection, "CREATE UNIQUE INDEX idx_transactions_operation "
            + "ON transactions(user_id, account_id, reference)");

        if (columnSize(connection, "saved_recipients", "recipient_account_number") < 0) {
            execute(connection, "ALTER TABLE saved_recipients ADD COLUMN recipient_account_number VARCHAR(12)");
        }
        execute(connection, """
            UPDATE saved_recipients r
            SET recipient_account_number = (SELECT a.account_number
                FROM users u JOIN accounts a ON a.owner_id = u.id AND a.is_primary = TRUE
                WHERE u.mobile_number = r.recipient_mobile)
            WHERE recipient_account_number IS NULL
            """);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM saved_recipients WHERE recipient_account_number IS NULL");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0) {
                throw new SQLException("Saved recipients could not be mapped to account numbers");
            }
        }
        execute(connection, "ALTER TABLE saved_recipients ALTER COLUMN recipient_account_number SET NOT NULL");
        if (!foreignKeyExists(connection, "saved_recipients", "recipient_account_number", "accounts",
            "account_number")) {
            execute(connection, "ALTER TABLE saved_recipients ADD CONSTRAINT fk_saved_recipient_account "
                + "FOREIGN KEY (recipient_account_number) REFERENCES accounts(account_number)");
        }
        if (!indexExists(connection, "saved_recipients", "idx_saved_recipients_account")) {
            execute(connection, "CREATE UNIQUE INDEX idx_saved_recipients_account "
                + "ON saved_recipients(owner_id, recipient_account_number)");
        }
    }

    private void verifyTransactionAccounts(Connection connection) throws SQLException {
        requireColumns(connection, "transactions", List.of("id", "user_id", "account_id", "type", "amount",
            "details", "occurred_at", "reference"));
        verifyColumn(connection, "transactions", "account_id", java.sql.Types.BIGINT, false);
        verifyForeignKey(connection, "transactions", "account_id", "accounts", "id");
        verifyIndex(connection, "idx_transactions_operation", List.of("user_id", "account_id", "reference"),
            true, "transactions");
        verifyIndex(connection, "idx_transactions_account", List.of("account_id", "occurred_at"), false,
            "transactions");
        verifyColumn(connection, "saved_recipients", "recipient_account_number", java.sql.Types.VARCHAR, false);
        verifyForeignKey(connection, "saved_recipients", "recipient_account_number", "accounts", "account_number");
        verifyIndex(connection, "idx_saved_recipients_account", List.of("owner_id", "recipient_account_number"),
            true, "saved_recipients");
    }

    private void repairCards(Connection connection, Dialect dialect) throws SQLException {
        String identity = dialect == Dialect.POSTGRESQL ? "BIGSERIAL" : "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        execute(connection, """
            CREATE TABLE IF NOT EXISTS cards (
                id IDENTITY_PLACEHOLDER PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                account_id BIGINT NOT NULL REFERENCES accounts(id),
                card_name VARCHAR(40) NOT NULL,
                brand VARCHAR(20) NOT NULL,
                card_type VARCHAR(12) NOT NULL DEFAULT 'DEBIT',
                last_four VARCHAR(4) NOT NULL,
                status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
                daily_limit NUMERIC(15, 2) NOT NULL DEFAULT 100000 CHECK (daily_limit > 0),
                online_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT cards_brand_valid CHECK (brand IN ('VISA', 'MASTERCARD')),
                CONSTRAINT cards_type_valid CHECK (card_type IN ('DEBIT')),
                CONSTRAINT cards_last_four_valid CHECK (length(last_four) = 4),
                CONSTRAINT cards_status_valid CHECK (status IN ('ACTIVE', 'FROZEN')),
                CONSTRAINT cards_owner_name_unique UNIQUE (owner_id, card_name)
            )
            """.replace("IDENTITY_PLACEHOLDER", identity));
        if (!indexExists(connection, "cards", "idx_cards_owner")) {
            execute(connection, "CREATE INDEX idx_cards_owner ON cards(owner_id, account_id)");
        }
    }

    private void verifyCards(Connection connection) throws SQLException {
        requireColumns(connection, "cards", List.of("id", "owner_id", "account_id", "card_name", "brand",
            "card_type", "last_four", "status", "daily_limit", "online_enabled", "created_at"));
        if (!checkExists(connection, "cards", "cards_brand_valid")
            || !checkExists(connection, "cards", "cards_type_valid")
            || !checkExists(connection, "cards", "cards_last_four_valid")
            || !checkExists(connection, "cards", "cards_status_valid")
            || !indexExists(connection, "cards", "idx_cards_owner")) {
            throw new SQLException("Card migration constraints or index did not pass");
        }
        verifyColumn(connection, "cards", "last_four", java.sql.Types.VARCHAR, false);
        verifyColumn(connection, "cards", "daily_limit", java.sql.Types.NUMERIC, false);
        verifyColumn(connection, "cards", "online_enabled", java.sql.Types.BOOLEAN, false);
        verifyDecimalScale(connection, "cards", "daily_limit", 2);
        verifyPrimaryKey(connection, "cards", List.of("id"));
        verifyForeignKey(connection, "cards", "owner_id", "users", "id");
        verifyForeignKey(connection, "cards", "account_id", "accounts", "id");
    }

    private void repairCardControls(Connection connection) throws SQLException {
        if (columnSize(connection, "cards", "online_enabled") < 0) {
            execute(connection, "ALTER TABLE cards ADD COLUMN online_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        }
    }

    private void repairCardPurchaseType(Connection connection) throws SQLException {
        if (!checkExists(connection, "transactions", "valid_transaction_type_v10")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT valid_transaction_type_v10 CHECK "
                + "(type IN ('CASH_IN', 'TRANSFER_SENT', 'TRANSFER_RECEIVED', 'SAVINGS_CONTRIBUTION', "
                + "'SAVINGS_WITHDRAWAL', 'BILL_PAYMENT', 'CARD_PURCHASE'))");
        }
        if (checkExists(connection, "transactions", "valid_transaction_type_v5")) {
            execute(connection, "ALTER TABLE transactions DROP CONSTRAINT valid_transaction_type_v5");
        }
    }

    private void repairLocalRequests(Connection connection, Dialect dialect) throws SQLException {
        String identity = dialect == Dialect.POSTGRESQL ? "BIGSERIAL" : "BIGINT GENERATED BY DEFAULT AS IDENTITY";
        if (!constraintExists(connection, "accounts", "accounts_owner_id_id_unique")) {
            execute(connection, "ALTER TABLE accounts ADD CONSTRAINT accounts_owner_id_id_unique "
                + "UNIQUE (owner_id, id)");
        }
        if (!constraintExists(connection, "cards", "cards_owner_id_id_unique")) {
            execute(connection, "ALTER TABLE cards ADD CONSTRAINT cards_owner_id_id_unique "
                + "UNIQUE (owner_id, id)");
        }
        execute(connection, """
            CREATE TABLE IF NOT EXISTS money_requests (
                id IDENTITY_PLACEHOLDER PRIMARY KEY,
                reference VARCHAR(36) NOT NULL UNIQUE,
                requester_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                requester_account_id BIGINT NOT NULL,
                payer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
                note VARCHAR(140) NOT NULL DEFAULT '',
                status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
                payment_reference VARCHAR(36) UNIQUE,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                CONSTRAINT money_requests_parties_different CHECK (requester_user_id <> payer_user_id),
                CONSTRAINT money_requests_status_valid CHECK (status IN ('PENDING', 'PAID', 'DECLINED', 'CANCELED')),
                CONSTRAINT money_requests_requester_account_fk FOREIGN KEY (requester_user_id, requester_account_id)
                    REFERENCES accounts(owner_id, id)
            )
            """.replace("IDENTITY_PLACEHOLDER", identity));
        if (!indexExists(connection, "money_requests", "idx_money_requests_payer_status")) {
            execute(connection, "CREATE INDEX idx_money_requests_payer_status "
                + "ON money_requests(payer_user_id, status, created_at)");
        }
        if (!indexExists(connection, "money_requests", "idx_money_requests_requester_status")) {
            execute(connection, "CREATE INDEX idx_money_requests_requester_status "
                + "ON money_requests(requester_user_id, status, created_at)");
        }
        execute(connection, """
            CREATE TABLE IF NOT EXISTS card_replacement_requests (
                id IDENTITY_PLACEHOLDER PRIMARY KEY,
                owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                card_id BIGINT NOT NULL,
                card_name VARCHAR(40) NOT NULL,
                last_four VARCHAR(4) NOT NULL,
                reason VARCHAR(12) NOT NULL,
                status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
                reference VARCHAR(36) NOT NULL UNIQUE,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL,
                CONSTRAINT card_replacements_reason_valid CHECK (reason IN ('LOST', 'STOLEN', 'DAMAGED', 'EXPIRED')),
                CONSTRAINT card_replacements_status_valid CHECK (status IN ('OPEN', 'CANCELED')),
                CONSTRAINT card_replacements_owner_card_fk FOREIGN KEY (owner_id, card_id)
                    REFERENCES cards(owner_id, id) ON DELETE CASCADE
            )
            """.replace("IDENTITY_PLACEHOLDER", identity));
        if (!indexExists(connection, "card_replacement_requests", "idx_card_replacements_owner_status")) {
            execute(connection, "CREATE INDEX idx_card_replacements_owner_status "
                + "ON card_replacement_requests(owner_id, status, created_at)");
        }
    }

    private void verifyLocalRequests(Connection connection) throws SQLException {
        requireColumns(connection, "money_requests", List.of("id", "reference", "requester_user_id",
            "requester_account_id", "payer_user_id", "amount", "note", "status", "payment_reference",
            "created_at", "updated_at"));
        if (!constraintExists(connection, "accounts", "accounts_owner_id_id_unique")
            || !constraintExists(connection, "cards", "cards_owner_id_id_unique")
            || !checkExists(connection, "money_requests", "money_requests_parties_different")
            || !checkExists(connection, "money_requests", "money_requests_status_valid")
            || !indexExists(connection, "money_requests", "idx_money_requests_payer_status")
            || !indexExists(connection, "money_requests", "idx_money_requests_requester_status")) {
            throw new SQLException("Money request migration constraints or indexes did not pass");
        }
        verifyColumn(connection, "money_requests", "amount", java.sql.Types.NUMERIC, false);
        verifyDecimalScale(connection, "money_requests", "amount", 2);
        verifyPrimaryKey(connection, "money_requests", List.of("id"));
        verifyForeignKey(connection, "money_requests", "requester_user_id", "users", "id");
        verifyForeignKey(connection, "money_requests", "requester_account_id", "accounts", "id");
        verifyForeignKey(connection, "money_requests", "payer_user_id", "users", "id");

        requireColumns(connection, "card_replacement_requests", List.of("id", "owner_id", "card_id", "card_name",
            "last_four", "reason", "status", "reference", "created_at", "updated_at"));
        if (!checkExists(connection, "card_replacement_requests", "card_replacements_reason_valid")
            || !checkExists(connection, "card_replacement_requests", "card_replacements_status_valid")
            || !indexExists(connection, "card_replacement_requests", "idx_card_replacements_owner_status")) {
            throw new SQLException("Card replacement migration constraints or indexes did not pass");
        }
        verifyPrimaryKey(connection, "card_replacement_requests", List.of("id"));
        verifyForeignKey(connection, "card_replacement_requests", "owner_id", "users", "id");
        verifyForeignKey(connection, "card_replacement_requests", "card_id", "cards", "id");
    }

    private void repairNotificationReads(Connection connection) throws SQLException {
        execute(connection, """
            CREATE TABLE IF NOT EXISTS notification_reads (
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
                read_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, transaction_id)
            )
            """);
    }

    private void repairTransactionCategory(Connection connection) throws SQLException {
        if (columnSize(connection, "transactions", "category") < 0) {
            execute(connection, "ALTER TABLE transactions ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'OTHER'");
        }
        execute(connection, """
            UPDATE transactions SET category = CASE
                WHEN type = 'CASH_IN' THEN 'INCOME'
                WHEN type IN ('TRANSFER_SENT', 'TRANSFER_RECEIVED') THEN 'TRANSFERS'
                WHEN type IN ('SAVINGS_CONTRIBUTION', 'SAVINGS_WITHDRAWAL') THEN 'SAVINGS'
                WHEN type = 'BILL_PAYMENT' THEN 'BILLS_AND_UTILITIES'
                ELSE 'OTHER'
            END
            WHERE category IS NULL OR category = 'OTHER'
            """);
        if (!checkExists(connection, "transactions", "valid_transaction_category")) {
            execute(connection, "ALTER TABLE transactions ADD CONSTRAINT valid_transaction_category CHECK "
                + "(category IN ('FOOD_AND_DINING', 'SHOPPING', 'TRANSPORTATION', 'BILLS_AND_UTILITIES', "
                + "'ENTERTAINMENT', 'OTHER', 'TRANSFERS', 'SAVINGS', 'INCOME'))");
        }
    }

    private void verifyNotificationReads(Connection connection) throws SQLException {
        requireColumns(connection, "notification_reads", List.of("user_id", "transaction_id", "read_at"));
        verifyColumn(connection, "notification_reads", "user_id", java.sql.Types.BIGINT, false);
        verifyColumn(connection, "notification_reads", "transaction_id", java.sql.Types.BIGINT, false);
        verifyColumn(connection, "notification_reads", "read_at", java.sql.Types.TIMESTAMP, false);
        verifyPrimaryKey(connection, "notification_reads", List.of("user_id", "transaction_id"));
        verifyForeignKey(connection, "notification_reads", "user_id", "users", "id");
        verifyForeignKey(connection, "notification_reads", "transaction_id", "transactions", "id");
    }

    private void verifyTransactionCategory(Connection connection) throws SQLException {
        verifyColumn(connection, "transactions", "category", java.sql.Types.VARCHAR, false);
        if (columnSize(connection, "transactions", "category") != 32
            || !checkExists(connection, "transactions", "valid_transaction_category")) {
            throw new SQLException("Transaction category migration postconditions did not pass");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM transactions WHERE category IS NULL "
                 + "OR category NOT IN ('FOOD_AND_DINING', 'SHOPPING', 'TRANSPORTATION', 'BILLS_AND_UTILITIES', "
                 + "'ENTERTAINMENT', 'OTHER', 'TRANSFERS', 'SAVINGS', 'INCOME')")) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw new SQLException("Transaction category values did not pass migration verification");
            }
        }
    }

    private boolean foreignKeyExists(Connection connection, String table, String column,
                                     String parentTable, String parentColumn) throws SQLException {
        try (ResultSet keys = connection.getMetaData().getImportedKeys(connection.getCatalog(),
            connection.getSchema(), actualTable(connection, table))) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                    && parentTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                    && parentColumn.equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void verifyTransactionTypeV4(Connection connection) throws SQLException {
        if (!checkExists(connection, "transactions", "valid_transaction_type_v4")) {
            throw new SQLException("Version 4 transaction type constraint is missing");
        }
    }

    private void verifyTransactionTypeV5(Connection connection) throws SQLException {
        if (!checkExists(connection, "transactions", "valid_transaction_type_v5")
            || checkExists(connection, "transactions", "valid_transaction_type_v4")) {
            throw new SQLException("Version 5 transaction type constraint did not pass");
        }
    }

    private void verifyTransactionTypeV10(Connection connection) throws SQLException {
        if (!checkExists(connection, "transactions", "valid_transaction_type_v10")
            || checkExists(connection, "transactions", "valid_transaction_type_v5")
            || checkExists(connection, "transactions", "valid_transaction_type_v4")) {
            throw new SQLException("Version 10 transaction type constraint did not pass");
        }
    }

    private void verifyDecimalScale(Connection connection, String table, String column, int expectedScale)
        throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(),
            actualTable(connection, table), column)) {
            if (!result.next() || result.getInt("DECIMAL_DIGITS") != expectedScale) {
                throw new SQLException("Migration decimal scale did not pass: " + table + "." + column);
            }
        }
    }

    private void verifyPrimaryKey(Connection connection, String table, List<String> expectedColumns)
        throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), connection.getSchema(),
            actualTable(connection, table))) {
            while (result.next()) {
                int sequence = result.getShort("KEY_SEQ") - 1;
                while (columns.size() <= sequence) {
                    columns.add(null);
                }
                columns.set(sequence, result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (columns.contains(null) || !columns.equals(expectedColumns)) {
            throw new SQLException("Primary key postcondition did not pass: " + table);
        }
    }

    private void verifyColumn(Connection connection, String table, String column, int expectedType,
                              boolean nullable) throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(),
            actualTable(connection, table), column)) {
            if (!result.next() || !matchesJdbcType(result, expectedType)
                || result.getInt("NULLABLE") != (nullable
                    ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls)) {
                throw new SQLException("Migration column definition did not pass: " + table + "." + column);
            }
        }
    }

    private boolean matchesJdbcType(ResultSet column, int expectedType) throws SQLException {
        int actualType = column.getInt("DATA_TYPE");
        if (actualType == expectedType) {
            return true;
        }
        // PostgreSQL JDBC reports its BOOLEAN type as BIT with the native name "bool".
        return expectedType == java.sql.Types.BOOLEAN && actualType == java.sql.Types.BIT
            && "bool".equalsIgnoreCase(column.getString("TYPE_NAME"));
    }

    private void verifyForeignKey(Connection connection, String table, String column,
                                  String parentTable, String parentColumn) throws SQLException {
        try (ResultSet keys = connection.getMetaData().getImportedKeys(connection.getCatalog(),
            connection.getSchema(), actualTable(connection, table))) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                    && parentTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))
                    && parentColumn.equalsIgnoreCase(keys.getString("PKCOLUMN_NAME"))) {
                    return;
                }
            }
        }
        throw new SQLException("Migration foreign key is missing: " + table + "." + column);
    }

    private void verifySavedRecipients(Connection connection) throws SQLException {
        requireColumns(connection, "saved_recipients",
            List.of("id", "owner_id", "recipient_mobile", "label", "created_at"));
        if (!indexExists(connection, "saved_recipients", "idx_saved_recipients_owner")) {
            throw new SQLException("Saved recipient owner index is missing");
        }
    }

    private void verifyOperationKey(Connection connection) throws SQLException {
        List<String> key = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getPrimaryKeys(
            connection.getCatalog(), connection.getSchema(), actualTable(connection, "money_operations"))) {
            while (result.next()) {
                key.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (!key.equals(List.of("reference"))) {
            throw new SQLException("Money operations must have a unique reference primary key");
        }
    }

    private void verifyIndex(Connection connection, String name, List<String> expected, boolean unique)
        throws SQLException {
        verifyIndex(connection, name, expected, unique, "transactions");
    }

    private void verifyIndex(Connection connection, String name, List<String> expected, boolean unique, String table)
        throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(
            connection.getCatalog(), connection.getSchema(), actualTable(connection, table), false, false)) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                    if (unique && result.getBoolean("NON_UNIQUE")) {
                        throw new SQLException("Operation index must enforce uniqueness");
                    }
                    columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        if (!columns.equals(expected)) {
            throw new SQLException("Migration index postconditions did not pass: " + name);
        }
    }

    private void requireColumns(Connection connection, String table, List<String> columns) throws SQLException {
        requireTable(connection, table);
        for (String column : columns) {
            if (columnSize(connection, table, column) < 0) {
                throw new SQLException("Migration required column is missing: " + table + "." + column);
            }
        }
    }

    private boolean checkExists(Connection connection, String table, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT constraint_name FROM information_schema.table_constraints
            WHERE table_schema = ? AND table_name = ? AND constraint_type = 'CHECK'
            """)) {
            statement.setString(1, connection.getSchema());
            statement.setString(2, actualTable(connection, table));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (name.equalsIgnoreCase(result.getString(1))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private boolean constraintExists(Connection connection, String table, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT constraint_name FROM information_schema.table_constraints
            WHERE table_schema = ? AND table_name = ?
            """)) {
            statement.setString(1, connection.getSchema());
            statement.setString(2, actualTable(connection, table));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (name.equalsIgnoreCase(result.getString(1))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private boolean indexExists(Connection connection, String table, String name) throws SQLException {
        try (ResultSet result = connection.getMetaData().getIndexInfo(
            connection.getCatalog(), connection.getSchema(), actualTable(connection, table), false, false)) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int columnSize(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(
            connection.getCatalog(), connection.getSchema(), actualTable(connection, table), null)) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("COLUMN_NAME"))) {
                    return result.getInt("COLUMN_SIZE");
                }
            }
        }
        return -1;
    }

    private void requireTable(Connection connection, String table) throws SQLException {
        if (!tableExists(connection, table)) {
            throw new SQLException("Migration required table is missing: " + table);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        return actualTable(connection, table) != null;
    }

    private String actualTable(Connection connection, String name) throws SQLException {
        try (ResultSet result = connection.getMetaData().getTables(
            connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
            while (result.next()) {
                if (name.equalsIgnoreCase(result.getString("TABLE_NAME"))) {
                    return result.getString("TABLE_NAME");
                }
            }
        }
        return null;
    }

    private void recordVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO schema_migrations(version) VALUES (?)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        executor.execute(connection, sql);
    }

    @FunctionalInterface
    interface SqlExecutor {
        void execute(Connection connection, String sql) throws SQLException;
    }

    private enum Dialect {
        POSTGRESQL,
        H2
    }
}
