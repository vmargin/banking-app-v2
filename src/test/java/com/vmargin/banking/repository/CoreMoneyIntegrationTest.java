package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.exception.RegistrationException;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import com.vmargin.banking.util.PinHasher;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreMoneyIntegrationTest {
    private JdbcUserRepository users;
    private User alex;
    private User sam;

    @BeforeEach
    void setup() throws Exception {
        DatabaseConnection.useLocalH2("jdbc:h2:mem:core_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        try (Connection connection = DatabaseConnection.open()) {
            new JdbcSchemaMigrator().migrate(connection);
        }
        users = new JdbcUserRepository();
        alex = create("09990000001", "Alex", "1000.00");
        sam = create("09990000002", "Sam", "500.00");
    }

    @AfterEach
    void cleanup() throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } finally {
            DatabaseConnection.clearLocalH2();
        }
    }

    private User create(String mobile, String name, String balance) throws Exception {
        return users.save(new User(0, mobile, "1234", name,
            new BankAccount("PENDING", name, new BigDecimal(balance))));
    }

    private BigDecimal balance(User user) throws Exception {
        return users.findById(user.getId()).orElseThrow().getBalance();
    }

    private long count(String table) throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    void duplicateRegistrationPreservesExistingIdentityCredentialsAndBalance() throws Exception {
        assertThrows(SQLException.class, () -> create("09990000001", "Overwrite", "0.00"));
        User unchanged = users.findById(alex.getId()).orElseThrow();
        assertEquals("Alex", unchanged.getFullName());
        assertEquals(new BigDecimal("1000.00"), unchanged.getBalance());
        assertTrue(PinHasher.isHash(unchanged.getPinForPersistence()));
        assertTrue(unchanged.matchesPin("1234"));
    }

    @Test
    void racingRegistrationCreatesExactlyOneAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> register = () -> {
            start.await();
            try {
                new RegistrationService(users).register("Race", "09990000003", "4321");
                return true;
            } catch (RegistrationException exception) {
                return false;
            }
        };
        try {
            Future<Boolean> first = pool.submit(register);
            Future<Boolean> second = pool.submit(register);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(3, users.findAll().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cashInAndTransferPersistAndSharedReferenceLinksBothLedgerRows() throws Exception {
        new CashInService(new JdbcCashInRepository()).cashIn(alex, new BigDecimal("50.00"), "Demo cash");
        String reference = UUID.randomUUID().toString();
        // A stale snapshot with zero balance must not throw after a committed transfer.
        User stale = new User(alex.getId(), alex.getMobileNumber(), "REDACTED", "Alex",
            new BankAccount("ACC-1", "Alex", BigDecimal.ZERO));
        BigDecimal committed = new TransferService(new JdbcTransferRepository()).transfer(
            stale, sam.getMobileNumber(), new BigDecimal("100.00"), reference);
        assertEquals(new BigDecimal("950.00"), committed);
        assertEquals(new BigDecimal("950.00"), balance(alex));
        assertEquals(new BigDecimal("600.00"), balance(sam));
        assertEquals(BigDecimal.ZERO, stale.getBalance());
        var ledger = new JdbcTransactionRepository();
        assertEquals(reference, ledger.findByUserId(alex.getId()).getFirst().getReference());
        assertEquals(reference, ledger.findByUserId(sam.getId()).getFirst().getReference());
        assertEquals(3, count("transactions"));
    }

    @Test
    void insufficientFundsAndPrecisionErrorsLeaveNoOperationOrLedger() throws Exception {
        var transfers = new JdbcTransferRepository();
        assertThrows(RuntimeException.class, () -> transfers.transfer(alex.getId(), alex.getMobileNumber(),
            sam.getMobileNumber(), new BigDecimal("1001.00"), LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new JdbcCashInRepository().cashIn(alex.getId(),
            new BigDecimal("1.001"), "Invalid", LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new JdbcCashInRepository().cashIn(alex.getId(),
            new BigDecimal("10000000000000"), "Invalid", LocalDateTime.now()));
        assertEquals(new BigDecimal("1000.00"), balance(alex));
        assertEquals(new BigDecimal("500.00"), balance(sam));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }

    @Test
    void ledgerFailureRollsBackBothBalancesAndOperationClaim() throws Exception {
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE transactions ADD CONSTRAINT reject_incoming "
                + "CHECK(type <> 'TRANSFER_RECEIVED')");
        }
        assertThrows(SQLException.class, () -> new JdbcTransferRepository().transfer(alex.getId(),
            alex.getMobileNumber(), sam.getMobileNumber(), new BigDecimal("100.00"), LocalDateTime.now()));
        assertEquals(new BigDecimal("1000.00"), balance(alex));
        assertEquals(new BigDecimal("500.00"), balance(sam));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }

    @Test
    void concurrentOppositeTransfersCompleteAndPreserveTotal() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<BigDecimal> forward = () -> {
            start.await();
            return new JdbcTransferRepository().transfer(alex.getId(), alex.getMobileNumber(), sam.getMobileNumber(),
                new BigDecimal("100.00"), LocalDateTime.now());
        };
        Callable<BigDecimal> backward = () -> {
            start.await();
            return new JdbcTransferRepository().transfer(sam.getId(), sam.getMobileNumber(), alex.getMobileNumber(),
                new BigDecimal("50.00"), LocalDateTime.now());
        };
        try {
            Future<BigDecimal> first = pool.submit(forward);
            Future<BigDecimal> second = pool.submit(backward);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(new BigDecimal("950.00"), balance(alex));
            assertEquals(new BigDecimal("550.00"), balance(sam));
            assertEquals(4, count("transactions"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentReplayMovesMoneyExactlyOnce() throws Exception {
        String reference = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> confirm = () -> {
            start.await();
            try {
                new JdbcTransferRepository().transfer(alex.getId(), alex.getMobileNumber(), sam.getMobileNumber(),
                    new BigDecimal("100.00"), LocalDateTime.now(), reference);
                return true;
            } catch (SQLException exception) {
                assertEquals("23505", exception.getSQLState());
                return false;
            }
        };
        try {
            Future<Boolean> first = pool.submit(confirm);
            Future<Boolean> second = pool.submit(confirm);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("900.00"), balance(alex));
            assertEquals(new BigDecimal("600.00"), balance(sam));
            assertEquals(2, count("transactions"));
            assertEquals(1, count("money_operations"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cashInReplayCannotCreditTwice() throws Exception {
        String reference = UUID.randomUUID().toString();
        var cash = new JdbcCashInRepository();
        cash.cashIn(alex.getId(), new BigDecimal("50.00"), "Demo", LocalDateTime.now(), reference);
        assertThrows(SQLException.class, () -> cash.cashIn(alex.getId(), new BigDecimal("50.00"),
            "Demo", LocalDateTime.now(), reference));
        assertEquals(new BigDecimal("1050.00"), balance(alex));
        assertEquals(1, count("transactions"));
    }

    private void writeLegacyPin(long userId, String pin) throws Exception {
        try (Connection connection = DatabaseConnection.open();
             var statement = connection.prepareStatement("UPDATE users SET pin = ? WHERE id = ?")) {
            statement.setString(1, pin);
            statement.setLong(2, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test
    void simulatedBillPaymentDebitsOnceAndPersistsOnlyMaskedReference() throws Exception {
        var service = new com.vmargin.banking.service.BillPaymentService(new JdbcBillPaymentRepository());
        var payment = service.prepare("ELECTRICITY", "123456789012", "125.50");
        String reference = UUID.randomUUID().toString();
        service.pay(alex, payment, reference);
        assertEquals(new BigDecimal("874.50"), balance(alex));
        assertThrows(SQLException.class, () -> service.pay(alex, payment, reference));
        assertEquals(new BigDecimal("874.50"), balance(alex));
        assertEquals(1, count("transactions"));
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT type, details, reference FROM transactions")) {
            assertTrue(result.next());
            assertEquals(TransactionType.BILL_PAYMENT.name(), result.getString("type"));
            String details = result.getString("details");
            assertTrue(details.contains("9012"));
            assertFalse(details.contains("123456789012"));
            assertEquals(reference, result.getString("reference"));
        }
    }

    @Test
    void billPaymentInsufficientFundsAndLedgerFailureRollbackDebitAndClaim() throws Exception {
        var repository = new JdbcBillPaymentRepository();
        assertThrows(RuntimeException.class, () -> repository.pay(alex.getId(), DemoBiller.WATER, "1234",
            new BigDecimal("1000.01"), LocalDateTime.now(), UUID.randomUUID().toString()));
        assertEquals(new BigDecimal("1000.00"), balance(alex));
        assertEquals(0, count("money_operations"));
        try (Connection connection = DatabaseConnection.open(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE transactions ADD CONSTRAINT reject_bill CHECK(type <> 'BILL_PAYMENT')");
        }
        assertThrows(SQLException.class, () -> repository.pay(alex.getId(), DemoBiller.WATER, "1234",
            new BigDecimal("10.00"), LocalDateTime.now(), UUID.randomUUID().toString()));
        assertEquals(new BigDecimal("1000.00"), balance(alex));
        assertEquals(0, count("transactions"));
        assertEquals(0, count("money_operations"));
    }

    @Test
    void migrationRerunPreservesHistoricalBalanceRowsAndChangedHash() throws Exception {
        String changedHash = PinHasher.hash("5678");
        users.updatePin(alex.getId(), alex.getPinForPersistence(), changedHash);
        new JdbcCashInRepository().cashIn(alex.getId(), new BigDecimal("25.00"), "History", LocalDateTime.now());
        try (Connection connection = DatabaseConnection.open()) {
            new JdbcSchemaMigrator().migrate(connection);
            new JdbcSchemaMigrator().migrate(connection);
        }
        assertEquals(new BigDecimal("1025.00"), balance(alex));
        assertEquals(changedHash, users.findById(alex.getId()).orElseThrow().getPinForPersistence());
        assertEquals(1, count("transactions"));
        assertEquals(JdbcSchemaMigrator.LATEST_VERSION, count("schema_migrations"));
    }

    @Test
    void pinUpdateRequiresHashAndRejectsStaleCredentialSnapshot() throws Exception {
        String originalPin = alex.getPinForPersistence();
        String replacementHash = PinHasher.hash("5678");

        users.updatePin(alex.getId(), originalPin, replacementHash);

        assertThrows(IllegalArgumentException.class,
            () -> users.updatePin(alex.getId(), replacementHash, "1234"));
        assertThrows(SQLException.class,
            () -> users.updatePin(alex.getId(), originalPin, PinHasher.hash("9012")));
        assertEquals(replacementHash, users.findById(alex.getId()).orElseThrow().getPinForPersistence());
    }

    @Test
    void legacyPlaintextMigratesOnlyAfterSuccessfulAuthentication() throws Exception {
        writeLegacyPin(alex.getId(), "1234");
        var login = new LoginService(users);
        assertThrows(RuntimeException.class, () -> login.login(alex.getMobileNumber(), "9999"));
        assertEquals("1234", users.findById(alex.getId()).orElseThrow().getPinForPersistence());
        User authenticated = login.login(alex.getMobileNumber(), "1234");
        String hash = users.findById(alex.getId()).orElseThrow().getPinForPersistence();
        assertTrue(PinHasher.isHash(hash));
        assertTrue(PinHasher.matches("1234", hash));
        assertFalse(PinHasher.matches("9999", hash));
        assertEquals(hash, authenticated.getPinForPersistence());
    }

    @Test
    void lockedAccountDoesNotBlockAnotherAndRecoversAfterExpiry() throws Exception {
        Instant time = Instant.parse("2026-10-06T00:00:00Z");
        var clock = org.mockito.Mockito.mock(Clock.class);
        org.mockito.Mockito.when(clock.instant()).thenReturn(time);
        var login = new LoginService(users, clock);
        for (int count = 0; count < 3; count++) {
            assertThrows(RuntimeException.class, () -> login.login(alex.getMobileNumber(), "9999"));
        }
        assertThrows(RuntimeException.class, () -> login.login(alex.getMobileNumber(), "1234"));
        assertEquals(sam.getId(), login.login(sam.getMobileNumber(), "1234").getId());
        org.mockito.Mockito.when(clock.instant()).thenReturn(time.plusSeconds(301));
        assertEquals(alex.getId(), login.login(alex.getMobileNumber(), "1234").getId());
    }
}
