package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.SavingsGoal;
import com.vmargin.banking.model.TransactionCategory;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import com.vmargin.banking.util.PinHasher;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDemoSeedRepositoryTest {
    private final JdbcDemoSeedRepository seeds = new JdbcDemoSeedRepository();

    @BeforeEach
    void setup() throws Exception {
        DatabaseConnection.useLocalH2("jdbc:h2:mem:seed_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try (Connection connection = DatabaseConnection.open()) {
            new JdbcSchemaMigrator().migrate(connection);
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } finally {
            DatabaseConnection.clearLocalH2();
        }
    }

    @Test
    void cashGDemoSeedBalancesAccountsGoalsAndRemainsIdempotent() throws Exception {
        seeds.seedCashGDemo();

        var miguel = new JdbcUserRepository().findByMobileNumber("09990000001").orElseThrow();
        var accounts = new JdbcAccountRepository().findByOwner(miguel.getId());
        List<BigDecimal> phpBalances = accounts.stream()
            .filter(account -> "PHP".equals(account.getCurrencyCode()))
            .map(BankAccount::getBalance)
            .sorted()
            .toList();
        assertEquals(List.of(new BigDecimal("24850.00"), new BigDecimal("80000.00")), phpBalances);
        assertEquals(new BigDecimal("1250.00"), accounts.stream()
            .filter(account -> "USD".equals(account.getCurrencyCode()))
            .map(BankAccount::getBalance).findFirst().orElseThrow());

        List<SavingsGoal> goals = new JdbcSavingsGoalRepository().findByOwner(miguel.getId());
        assertEquals(3, goals.size());
        assertEquals(new BigDecimal("80000.00"), goalBalance(goals, "Trip to Japan"));
        assertEquals(new BigDecimal("120000.00"), goalBalance(goals, "Emergency fund"));
        assertEquals(new BigDecimal("60000.00"), goalBalance(goals, "New Laptop"));
        assertEquals(new BigDecimal("24850.00"), miguel.getBalance());
        var ledger = new JdbcTransactionRepository().findByUserId(miguel.getId());
        BigDecimal seededSpending = ledger.stream().filter(transaction -> transaction.getType().isSpending())
            .map(transaction -> transaction.getAmount()).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        assertEquals(new BigDecimal("11911.00"), seededSpending);
        assertEquals(new BigDecimal("1911.00"), ledger.stream()
            .filter(transaction -> transaction.getCategory() == TransactionCategory.OTHER)
            .map(transaction -> transaction.getAmount()).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add));

        long transactionsBeforeRetry = count("transactions");
        seeds.seedCashGDemo();
        assertEquals(transactionsBeforeRetry, count("transactions"));
        assertEquals(3, new JdbcSavingsGoalRepository().findByOwner(miguel.getId()).size());
        assertEquals(new BigDecimal("24850.00"), new JdbcUserRepository()
            .findByMobileNumber("09990000001").orElseThrow().getBalance());
    }

    @Test
    void newSyntheticUserOpeningBalanceOperationAndLedgerCommitTogether() throws Exception {
        assertTrue(seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("12480.50")));
        var user = new JdbcUserRepository().findByMobileNumber("09990000001").orElseThrow();
        assertTrue(PinHasher.isHash(user.getPinForPersistence()));
        assertTrue(user.matchesPin("1234"));
        assertEquals(new BigDecimal("12480.50"), user.getBalance());
        assertEquals(1, count("users"));
        assertEquals(1, count("money_operations"));
        var ledger = new JdbcTransactionRepository().findByUserId(user.getId());
        assertEquals(1, ledger.size());
        assertEquals(new BigDecimal("12480.50"), ledger.getFirst().getAmount());
        assertEquals("Synthetic demo opening funds", ledger.getFirst().getDetails());
        assertTrue(ledger.getFirst().getReference() != null);
    }

    @Test
    void existingMobileKeepsChangedProfileHashBalanceAndHistoricalRows() throws Exception {
        seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("12480.50"));
        var user = new JdbcUserRepository().findByMobileNumber("09990000001").orElseThrow();
        String hash = PinHasher.hash("5678");
        try (Connection connection = DatabaseConnection.open(); var statement = connection.prepareStatement(
            "UPDATE users SET full_name='Changed', pin=?, role='ADMIN', balance=777.00 WHERE id=?")) {
            statement.setString(1, hash);
            statement.setLong(2, user.getId());
            statement.executeUpdate();
        }
        try (Connection connection = DatabaseConnection.open(); var statement = connection.prepareStatement(
            "UPDATE accounts SET balance=777.00 WHERE owner_id=? AND is_primary=TRUE")) {
            statement.setLong(1, user.getId());
            assertEquals(1, statement.executeUpdate());
        }
        assertFalse(seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("12480.50")));
        assertFalse(seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("12480.50")));
        var unchanged = new JdbcUserRepository().findById(user.getId()).orElseThrow();
        assertEquals("Changed", unchanged.getFullName());
        assertEquals(hash, unchanged.getPinForPersistence());
        assertTrue(unchanged.isAdmin());
        assertEquals(new BigDecimal("777.00"), unchanged.getBalance());
        assertEquals(1, count("transactions"));
        assertEquals(1, count("money_operations"));
    }

    @Test
    void injectedLedgerFailureRollsBackNewUserBalanceAndOperationAndRetryWorks() throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE transactions ADD CONSTRAINT reject_seed CHECK(type <> 'CASH_IN')");
        }
        assertThrows(SQLException.class, () -> seeds.seedIfMissing(
            "09990000001", "Demo User", "1234", new BigDecimal("1000.00")));
        assertEquals(0, count("users"));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE transactions DROP CONSTRAINT reject_seed");
        }
        assertTrue(seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("1000.00")));
        assertEquals(1, count("users"));
        assertEquals(1, count("transactions"));
        assertEquals(1, count("money_operations"));
    }

    @Test
    void administratorSeedCreatesOnlyMissingZeroBalanceIdentityWithHash() throws Exception {
        assertTrue(seeds.seedAdministratorIfMissing());
        var user = new JdbcUserRepository().findByMobileNumber("09990000000").orElseThrow();
        assertTrue(user.isAdmin());
        assertTrue(PinHasher.isHash(user.getPinForPersistence()));
        assertTrue(user.matchesPin("1234"));
        assertEquals(new BigDecimal("0.00"), user.getBalance());
        assertEquals(1, count("users"));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }

    @Test
    void administratorSeedPreservesAnExistingChangedStandardIdentityCompletely() throws Exception {
        seeds.seedAdministratorIfMissing();
        var seeded = new JdbcUserRepository().findByMobileNumber("09990000000").orElseThrow();
        String hash = PinHasher.hash("5678");
        try (Connection connection = DatabaseConnection.open(); var statement = connection.prepareStatement(
            "UPDATE users SET full_name='Changed administrator', pin=?, role='USER', balance=42.50")) {
            statement.setString(1, hash);
            statement.executeUpdate();
        }
        try (Connection connection = DatabaseConnection.open(); var statement = connection.prepareStatement(
            "UPDATE accounts SET balance=42.50 WHERE owner_id=? AND is_primary=TRUE")) {
            statement.setLong(1, seeded.getId());
            assertEquals(1, statement.executeUpdate());
        }
        assertFalse(seeds.seedAdministratorIfMissing());
        assertFalse(seeds.seedAdministratorIfMissing());
        var user = new JdbcUserRepository().findByMobileNumber("09990000000").orElseThrow();
        assertEquals("Changed administrator", user.getFullName());
        assertEquals(hash, user.getPinForPersistence());
        assertFalse(user.isAdmin());
        assertEquals(new BigDecimal("42.50"), user.getBalance());
        assertEquals(1, count("users"));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }

    @Test
    void concurrentSeedsCreateOneIdentityAndOpeningCredit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> seed = () -> {
            start.await();
            return seeds.seedIfMissing("09990000001", "Demo User", "1234", new BigDecimal("1000.00"));
        };
        try {
            var first = pool.submit(seed);
            var second = pool.submit(seed);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, count("users"));
            assertEquals(1, count("money_operations"));
            assertEquals(1, count("transactions"));
            assertEquals(new BigDecimal("1000.00"), new JdbcUserRepository()
                .findByMobileNumber("09990000001").orElseThrow().getBalance());
        } finally {
            pool.shutdownNow();
        }
    }

    private long count(String table) throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private BigDecimal goalBalance(List<SavingsGoal> goals, String name) {
        return goals.stream().filter(goal -> name.equals(goal.name())).map(SavingsGoal::savedAmount)
            .findFirst().orElseThrow();
    }
}
