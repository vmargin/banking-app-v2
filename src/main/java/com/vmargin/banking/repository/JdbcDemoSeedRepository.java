package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.TransactionCategory;
import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.SavedRecipient;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.MoneyValidation;
import com.vmargin.banking.util.PinHasher;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

/** Explicit synthetic fixture creation, separate from schema migrations. */
public final class JdbcDemoSeedRepository {
    private static final String MIGUEL_MOBILE = "09990000001";
    private static final String MARIA_MOBILE = "09990000002";
    private static final String REF_TRIP_GOAL = "00000000-0000-4000-8000-000000000001";
    private static final String REF_EMERGENCY_GOAL = "00000000-0000-4000-8000-000000000002";
    private static final String REF_SAVINGS_OPENING = "00000000-0000-4000-8000-000000000003";
    private static final String REF_USD_OPENING = "00000000-0000-4000-8000-000000000004";
    private static final String REF_SALARY = "00000000-0000-4000-8000-000000000005";
    private static final String REF_SAVINGS_TRANSFER = "00000000-0000-4000-8000-000000000006";
    private static final String REF_COFFEE = "00000000-0000-4000-8000-000000000007";
    private static final String REF_RIDE = "00000000-0000-4000-8000-000000000008";
    private static final String REF_STREAMING = "00000000-0000-4000-8000-000000000009";
    private static final String REF_BILL = "00000000-0000-4000-8000-000000000010";
    private static final String REF_MARKET = "00000000-0000-4000-8000-000000000011";
    private static final String REF_LAPTOP_GOAL = "00000000-0000-4000-8000-000000000012";
    private static final String REF_PERSONAL_CARE = "00000000-0000-4000-8000-000000000013";

    /** Seeds an idempotent, local-only Cash - G practice ledger after the explicit demo migration. */
    public void seedCashGDemo() throws SQLException {
        seedIfMissing(MIGUEL_MOBILE, "Miguel Santos", "1234", new BigDecimal("271761.00"));
        seedIfMissing(MARIA_MOBILE, "Maria Reyes", "1234", new BigDecimal("35000.00"));

        JdbcUserRepository users = new JdbcUserRepository();
        User miguel = users.findByMobileNumber(MIGUEL_MOBILE)
            .orElseThrow(() -> new SQLException("Cash - G demo profile was not created"));
        User maria = users.findByMobileNumber(MARIA_MOBILE)
            .orElseThrow(() -> new SQLException("Cash - G demo contact was not created"));
        BankAccount checking = miguel.getBankAccount();
        JdbcAccountRepository accounts = new JdbcAccountRepository();
        BankAccount savings = accounts.openDemoAccountIfMissing(miguel.getId(), miguel.getFullName(),
            "Savings", "SAVINGS", "PHP", BigDecimal.ZERO.setScale(2));
        BankAccount foreign = accounts.openDemoAccountIfMissing(miguel.getId(), miguel.getFullName(),
            "USD account", "FOREIGN_CURRENCY", "USD", BigDecimal.ZERO.setScale(2));

        JdbcSavingsGoalRepository goals = new JdbcSavingsGoalRepository();
        goals.createDemoIfMissing(miguel.getId(), "Trip to Japan", new BigDecimal("120000.00"),
            new BigDecimal("80000.00"), LocalDateTime.now().minusDays(12), REF_TRIP_GOAL);
        goals.createDemoIfMissing(miguel.getId(), "Emergency fund", new BigDecimal("300000.00"),
            new BigDecimal("120000.00"), LocalDateTime.now().minusDays(5), REF_EMERGENCY_GOAL);
        goals.createDemoIfMissing(miguel.getId(), "New Laptop", new BigDecimal("300000.00"),
            new BigDecimal("60000.00"), LocalDateTime.now().minusDays(2), REF_LAPTOP_GOAL);

        recordDemoCredit(miguel.getId(), savings.getId(), new BigDecimal("60000.00"), "Opening savings balance",
            REF_SAVINGS_OPENING);
        recordDemoCredit(miguel.getId(), foreign.getId(), new BigDecimal("1250.00"), "Opening demo balance",
            REF_USD_OPENING);
        recordCashInOnce(miguel.getId(), checking.getId(), new BigDecimal("45000.00"),
            "Salary deposit · Demo employer", REF_SALARY);
        transferOnce(miguel.getId(), checking.getId(), savings.getAccountNumber(),
            new BigDecimal("20000.00"), REF_SAVINGS_TRANSFER);
        recordDemoCardPurchase(miguel.getId(), checking.getId(), new BigDecimal("235.00"),
            "Coffee Project · Food & beverage", REF_COFFEE, TransactionCategory.FOOD_AND_DINING);
        recordDemoCardPurchase(miguel.getId(), checking.getId(), new BigDecimal("320.00"),
            "Ride share · Transportation", REF_RIDE, TransactionCategory.TRANSPORTATION);
        recordDemoCardPurchase(miguel.getId(), checking.getId(), new BigDecimal("549.00"),
            "Netflix · Entertainment", REF_STREAMING, TransactionCategory.ENTERTAINMENT);
        recordBillOnce(miguel.getId(), checking.getId());
        recordDemoCardPurchase(miguel.getId(), checking.getId(), new BigDecimal("6416.00"),
            "Weekend market · Shopping", REF_MARKET, TransactionCategory.SHOPPING);
        recordDemoCardPurchase(miguel.getId(), checking.getId(), new BigDecimal("1911.00"),
            "Pharmacy · Personal care", REF_PERSONAL_CARE, TransactionCategory.OTHER);

        JdbcCardRepository cards = new JdbcCardRepository();
        cards.createDemoIfMissing(miguel.getId(), checking.getId(), checking.getAccountName(),
            "Everyday debit", "VISA", "4217", new BigDecimal("100000.00"));
        cards.createDemoIfMissing(maria.getId(), maria.getBankAccount().getId(),
            maria.getBankAccount().getAccountName(), "Everyday debit", "VISA", "2048",
            new BigDecimal("50000.00"));
        seedSavedRecipient(miguel, maria.getBankAccount());
        seedSavedRecipient(maria, checking);
    }

    private void seedSavedRecipient(User owner, BankAccount recipient) throws SQLException {
        boolean alreadySaved = new JdbcSavedRecipientRepository().findByOwner(owner.getId()).stream()
            .map(SavedRecipient::accountNumber).anyMatch(recipient.getAccountNumber()::equals);
        if (!alreadySaved) {
            new JdbcSavedRecipientRepository().save(owner.getId(), owner.getMobileNumber(),
                recipient.getAccountNumber(), recipient.getHolderName());
        }
    }

    private void recordDemoCredit(long ownerId, long accountId, BigDecimal amount, String details, String reference)
        throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            if (operationExists(connection, reference)) {
                return;
            }
            connection.setAutoCommit(false);
            try {
                MoneyWrites.claim(connection, ownerId, reference, "CASH_IN");
                MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
                if (account.ownerId() != ownerId) {
                    throw new SQLException("Synthetic account owner did not match");
                }
                MoneyWrites.balanceAccount(connection, account, account.balance().add(amount));
                MoneyWrites.ledger(connection, ownerId, accountId, TransactionType.CASH_IN, amount,
                    details, LocalDateTime.now().minusDays(30), reference);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void recordCashInOnce(long ownerId, long accountId, BigDecimal amount,
                                  String details, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            if (operationExists(connection, reference)) {
                return;
            }
        }
        new JdbcCashInRepository().cashInAccount(ownerId, accountId, amount,
            details, LocalDateTime.now().minusDays(4), reference);
    }

    private void transferOnce(long ownerId, long sourceAccountId, String targetAccountNumber,
                              BigDecimal amount, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            if (operationExists(connection, reference)) {
                return;
            }
        }
        new JdbcTransferRepository().transferToAccount(ownerId, sourceAccountId, targetAccountNumber,
            amount, LocalDateTime.now().minusDays(3), reference);
    }

    private void recordBillOnce(long ownerId, long accountId) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            if (operationExists(connection, REF_BILL)) {
                return;
            }
        }
        new JdbcBillPaymentRepository().payFromAccount(ownerId, accountId, DemoBiller.ELECTRICITY,
            "4481", new BigDecimal("2480.00"), LocalDateTime.now().minusDays(2), REF_BILL);
    }

    private void recordDemoCardPurchase(long ownerId, long accountId, BigDecimal amount,
                                        String details, String reference, TransactionCategory category)
        throws SQLException {
        MoneyValidation.amount(amount);
        try (Connection connection = DatabaseConnection.open()) {
            if (operationExists(connection, reference)) {
                return;
            }
            connection.setAutoCommit(false);
            try {
                MoneyWrites.claim(connection, ownerId, reference, "CARD_PURCHASE");
                MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
                if (account.ownerId() != ownerId || !"PHP".equals(account.currencyCode())
                    || account.balance().compareTo(amount) < 0) {
                    throw new SQLException("Synthetic card purchase account is unavailable");
                }
                MoneyWrites.balanceAccount(connection, account, account.balance().subtract(amount));
                MoneyWrites.ledger(connection, ownerId, accountId, TransactionType.CARD_PURCHASE, amount,
                    details + " · demo purchase", LocalDateTime.now().minusDays(1), reference, category);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean operationExists(Connection connection, String reference) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM money_operations WHERE reference = ?")) {
            statement.setString(1, reference);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public boolean seedIfMissing(String mobile, String fullName, String pin, BigDecimal openingBalance)
        throws SQLException {
        validate(mobile, fullName, pin, openingBalance);
        try (Connection connection = DatabaseConnection.open()) {
            return seedIfMissing(connection, mobile, fullName, pin, openingBalance, UserRole.USER);
        }
    }

    /** Called only by explicit setup tooling, never normal PostgreSQL application startup. */
    public boolean seedAdministratorIfMissing() throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            return seedAdministratorIfMissing(connection);
        }
    }

    public boolean seedAdministratorIfMissing(Connection connection) throws SQLException {
        return seedIfMissing(connection, "09990000000", "Cash - G Administrator", "1234",
            BigDecimal.ZERO, UserRole.ADMIN);
    }

    public boolean seedIfMissing(Connection connection, String mobile, String fullName, String pin,
        BigDecimal openingBalance) throws SQLException {
        validate(mobile, fullName, pin, openingBalance);
        return seedIfMissing(connection, mobile, fullName, pin, openingBalance, UserRole.USER);
    }

    private boolean seedIfMissing(Connection connection, String mobile, String fullName, String pin,
        BigDecimal openingBalance, UserRole role) throws SQLException {
        boolean ownsTransaction = connection.getAutoCommit();
        if (ownsTransaction) {
            connection.setAutoCommit(false);
        }
        Savepoint savepoint = ownsTransaction ? null : connection.setSavepoint();
        try {
            if (exists(connection, mobile)) {
                finish(connection, ownsTransaction, savepoint);
                return false;
            }
            long id = insert(connection, mobile, fullName, PinHasher.hash(pin), role);
            if (openingBalance.signum() > 0) {
                String reference = UUID.randomUUID().toString();
                MoneyWrites.claim(connection, id, reference, "CASH_IN");
                long accountId = MoneyWrites.primaryAccountId(connection, id);
                MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
                BigDecimal balance = account.balance().add(openingBalance);
                MoneyWrites.balanceAccount(connection, account, balance);
                MoneyWrites.ledger(connection, id, accountId, TransactionType.CASH_IN, openingBalance,
                    "Synthetic demo opening funds", LocalDateTime.now(), reference);
            }
            finish(connection, ownsTransaction, savepoint);
            return true;
        } catch (SQLException | RuntimeException exception) {
            rollback(connection, ownsTransaction, savepoint);
            // Concurrent seeds may both observe absence; the unique mobile wins without overwriting it.
            if (exception instanceof SQLException sql && "23505".equals(sql.getSQLState())
                && exists(connection, mobile)) {
                finish(connection, ownsTransaction, savepoint);
                return false;
            }
            throw exception;
        } finally {
            if (ownsTransaction) {
                connection.setAutoCommit(true);
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

    private void rollback(Connection connection, boolean ownsTransaction, Savepoint savepoint) throws SQLException {
        if (ownsTransaction) {
            connection.rollback();
        } else {
            connection.rollback(savepoint);
        }
    }

    private void validate(String mobile, String name, String pin, BigDecimal amount) {
        if (mobile == null || !mobile.matches("09\\d{9}") || name == null || name.isBlank() || name.length() > 120
            || pin == null || !pin.matches("\\d{4}")) {
            throw new IllegalArgumentException("Invalid synthetic fixture identity");
        }
        MoneyValidation.amount(amount);
    }

    private boolean exists(Connection connection, String mobile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM users WHERE mobile_number = ?")) {
            statement.setString(1, mobile);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private long insert(Connection connection, String mobile, String name, String hash, UserRole role)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO users(mobile_number, full_name, pin, role, balance) VALUES (?, ?, ?, ?, 0)
            """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, mobile);
            statement.setString(2, name);
            statement.setString(3, hash);
            statement.setString(4, role.name());
            statement.executeUpdate();
            try (ResultSet result = statement.getGeneratedKeys()) {
                if (!result.next()) {
                    throw new SQLException("Synthetic account ID was not generated");
                }
                long ownerId = result.getLong(1);
                JdbcAccountRepository.insertPrimary(connection, ownerId, name, BigDecimal.ZERO.setScale(2));
                return ownerId;
            }
        }
    }
}
