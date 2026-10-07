package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.exception.InsufficientBalanceException;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.h2.api.Trigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSavingsGoalRepositoryTest {
    private JdbcSavingsGoalRepository repository;

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:savings_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        DatabaseConnection.useLocalH2(url);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            new JdbcSchemaMigrator().migrate(connection);
        }
        JdbcUserRepository users = new JdbcUserRepository();
        users.save(new com.vmargin.banking.model.User(0, "09990000001", "1234", "Owner A",
            new com.vmargin.banking.model.BankAccount("PENDING", "Owner A", new BigDecimal("100.00"))));
        users.save(new com.vmargin.banking.model.User(0, "09990000002", "1234", "Owner B",
            new com.vmargin.banking.model.BankAccount("PENDING", "Owner B", new BigDecimal("5.00"))));
        repository = new JdbcSavingsGoalRepository();
    }

    @AfterEach
    void cleanup() {
        DatabaseConnection.clearLocalH2();
    }

    @Test
    void goalMovesConserveFundsAndUseCorrectOwnerLedgerDirection() throws Exception {
        var goal = repository.create(1, "Emergency", new BigDecimal("50.00"));
        repository.move(1, goal.id(), new BigDecimal("60.00"), true, LocalDateTime.now(), UUID.randomUUID().toString());
        assertEquals(0, scalar("SELECT balance FROM accounts WHERE owner_id=1 AND is_primary=TRUE")
            .compareTo(new BigDecimal("40.00")));
        assertEquals(0, scalar("SELECT saved_amount FROM savings_goals WHERE id=" + goal.id())
            .compareTo(new BigDecimal("60.00")));
        assertEquals(TransactionType.SAVINGS_CONTRIBUTION.name(), text("SELECT type FROM transactions"));
        repository.move(1, goal.id(), new BigDecimal("15.00"), false, LocalDateTime.now(),
            UUID.randomUUID().toString());
        assertEquals(0, scalar("SELECT balance FROM accounts WHERE owner_id=1 AND is_primary=TRUE")
            .compareTo(new BigDecimal("55.00")));
        assertEquals(0, scalar("SELECT saved_amount FROM savings_goals WHERE id=" + goal.id())
            .compareTo(new BigDecimal("45.00")));
        assertEquals(TransactionType.SAVINGS_WITHDRAWAL.name(),
            text("SELECT type FROM transactions ORDER BY id DESC LIMIT 1"));
        assertEquals(2, number("SELECT COUNT(*) FROM transactions WHERE user_id=1"));
        assertEquals(0, number("SELECT COUNT(*) FROM transactions WHERE user_id=2"));
    }

    @Test
    void ownerScopeAndMovesRefuseOverspendOverwithdrawalAndStaleReferences() throws Exception {
        var goal = repository.create(1, "Reserve", new BigDecimal("1.00"));
        assertTrue(repository.findByOwner(2).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.move(2, goal.id(),
            new BigDecimal("1.00"), false, LocalDateTime.now(), UUID.randomUUID().toString()));
        assertThrows(InsufficientBalanceException.class, () -> repository.move(1, goal.id(),
            new BigDecimal("101.00"), true, LocalDateTime.now(), UUID.randomUUID().toString()));
        assertThrows(InsufficientBalanceException.class, () -> repository.move(1, goal.id(),
            new BigDecimal("1.00"), false, LocalDateTime.now(), UUID.randomUUID().toString()));
        String reference = UUID.randomUUID().toString();
        repository.move(1, goal.id(), new BigDecimal("1.00"), true, LocalDateTime.now(), reference);
        assertThrows(SQLException.class, () -> repository.move(1, goal.id(), new BigDecimal("1.00"), true,
            LocalDateTime.now(), reference));
        assertEquals(99, scalar("SELECT balance FROM accounts WHERE owner_id=1 AND is_primary=TRUE").intValue());
    }

    @Test
    void ledgerFailureRollsBackClaimCashAndGoalTogether() throws Exception {
        var goal = repository.create(1, "Reserve", new BigDecimal("25.00"));
        String reference = UUID.randomUUID().toString();
        try (var connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_savings_ledger BEFORE INSERT ON transactions "
                + "FOR EACH ROW CALL 'com.vmargin.banking.repository.JdbcSavingsGoalRepositoryTest$FailLedgerTrigger'");
        }
        assertThrows(SQLException.class, () -> repository.move(1, goal.id(), new BigDecimal("10.00"), true,
            LocalDateTime.now(), reference));
        assertEquals(0, scalar("SELECT balance FROM accounts WHERE owner_id=1 AND is_primary=TRUE")
            .compareTo(new BigDecimal("100.00")));
        assertEquals(0, scalar("SELECT saved_amount FROM savings_goals WHERE id=" + goal.id())
            .compareTo(BigDecimal.ZERO));
        assertEquals(0, number("SELECT COUNT(*) FROM money_operations WHERE reference='" + reference + "'"));
        assertEquals(0, number("SELECT COUNT(*) FROM transactions WHERE reference='" + reference + "'"));
    }

    @Test
    void concurrentContributionsCannotOverspendLockedAccount() throws Exception {
        var goal = repository.create(1, "Reserve", new BigDecimal("1.00"));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit((java.util.concurrent.Callable<Void>) () -> {
                attempt(start, goal.id(), UUID.randomUUID().toString()); return null;
            });
            var second = pool.submit((java.util.concurrent.Callable<Void>) () -> {
                attempt(start, goal.id(), UUID.randomUUID().toString()); return null;
            });
            start.countDown();
            int successes = 0;
            int rejected = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get();
                    successes++;
                }
                catch (java.util.concurrent.ExecutionException exception) {
                    if (exception.getCause() instanceof InsufficientBalanceException) {
                        rejected++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertEquals(1, successes);
            assertEquals(1, rejected);
            assertEquals(0, scalar("SELECT balance FROM accounts WHERE owner_id=1 AND is_primary=TRUE")
                .compareTo(new BigDecimal("40.00")));
            assertEquals(0, scalar("SELECT saved_amount FROM savings_goals WHERE id=" + goal.id())
                .compareTo(new BigDecimal("60.00")));
        }
    }

    private void attempt(CountDownLatch start, long goalId, String reference) throws Exception {
        start.await();
        repository.move(1, goalId, new BigDecimal("60.00"), true, LocalDateTime.now(), reference);
    }

    public static class FailLedgerTrigger implements Trigger {
        @Override public void init(java.sql.Connection connection, String schemaName, String triggerName,
                                  String tableName, boolean before, int type) { }
        @Override
        public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
            throw new SQLException("Injected ledger failure");
        }
        @Override public void close() { }
        @Override public void remove() { }
    }

    private BigDecimal scalar(String sql) throws Exception {
        try (var connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) { result.next(); return result.getBigDecimal(1); }
    }

    private int number(String sql) throws Exception {
        try (var connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) { result.next(); return result.getInt(1); }
    }

    private String text(String sql) throws Exception {
        try (var connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery(sql)) { result.next(); return result.getString(1); }
    }
}
