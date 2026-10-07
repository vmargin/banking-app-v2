package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSavedRecipientRepositoryTest {
    private JdbcSavedRecipientRepository repository;
    private User ownerA;
    private User ownerB;
    private User recipient;

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:recipients_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        DatabaseConnection.useLocalH2(url);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            new JdbcSchemaMigrator().migrate(connection);
        }
        JdbcUserRepository users = new JdbcUserRepository();
        ownerA = create(users, "09990000001", "Owner A");
        ownerB = create(users, "09990000002", "Owner B");
        recipient = create(users, "09990000003", "Recipient");
        repository = new JdbcSavedRecipientRepository();
    }

    @AfterEach
    void cleanup() {
        DatabaseConnection.clearLocalH2();
    }

    @Test
    void savesListsAndDeletesOnlyRowsBelongingToTheOwnerAndRejectsConcurrentDuplicateKey() throws Exception {
        repository.save(ownerA.getId(), ownerA.getMobileNumber(), recipient.getBankAccount().getAccountNumber(),
            "Work");
        assertEquals(1, repository.findByOwner(ownerA.getId()).size());
        assertTrue(repository.findByOwner(ownerB.getId()).isEmpty());
        assertThrows(JdbcSavedRecipientRepository.DuplicateSavedRecipientException.class,
            () -> repository.save(ownerA.getId(), ownerA.getMobileNumber(),
                recipient.getBankAccount().getAccountNumber(), "Other label"));
        long id = repository.findByOwner(ownerA.getId()).getFirst().id();
        assertFalse(repository.delete(ownerB.getId(), id));
        assertEquals(1, repository.findByOwner(ownerA.getId()).size());
        assertTrue(repository.delete(ownerA.getId(), id));
        assertTrue(repository.findByOwner(ownerA.getId()).isEmpty());
    }

    @Test
    void rejectsMissingRecipientAndWrongOwnerIdentity() {
        assertThrows(java.sql.SQLException.class,
            () -> repository.save(ownerA.getId(), ownerA.getMobileNumber(), "000000000000", "Missing"));
        assertThrows(java.sql.SQLException.class,
            () -> repository.save(ownerB.getId(), ownerA.getMobileNumber(),
                recipient.getBankAccount().getAccountNumber(), "Wrong owner"));
    }

    @Test
    void databaseRejectsInvalidLabelAndEnforcesOwnerAccountUniqueness() throws Exception {
        repository.save(ownerA.getId(), ownerA.getMobileNumber(), recipient.getBankAccount().getAccountNumber(),
            "Valid");
        assertThrows(JdbcSavedRecipientRepository.DuplicateSavedRecipientException.class,
            () -> repository.save(ownerA.getId(), ownerA.getMobileNumber(),
                recipient.getBankAccount().getAccountNumber(), "Another label"));
        try (var connection = DatabaseConnection.open();
             var statement = connection.prepareStatement(
                 "INSERT INTO saved_recipients(owner_id,recipient_mobile,recipient_account_number,label) "
                     + "VALUES (?,?,?,'   ')")) {
            statement.setString(1, ownerB.getMobileNumber());
            statement.setString(2, recipient.getMobileNumber());
            statement.setString(3, recipient.getBankAccount().getAccountNumber());
            assertThrows(java.sql.SQLException.class, statement::executeUpdate);
        }
    }

    @Test
    void concurrentDuplicateSavesLeaveExactlyOneRecipient() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit((java.util.concurrent.Callable<Void>) () -> {
                saveAfter(start, "First");
                return null;
            });
            var second = pool.submit((java.util.concurrent.Callable<Void>) () -> {
                saveAfter(start, "Second");
                return null;
            });
            start.countDown();
            int successes = 0;
            int duplicates = 0;
            for (var result : java.util.List.of(first, second)) {
                try {
                    result.get();
                    successes++;
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (exception.getCause() instanceof JdbcSavedRecipientRepository.DuplicateSavedRecipientException) {
                        duplicates++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertEquals(1, successes);
            assertEquals(1, duplicates);
            assertEquals(1, repository.findByOwner(ownerA.getId()).size());
        }
    }

    private void saveAfter(CountDownLatch start, String label) throws java.sql.SQLException {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new java.sql.SQLException("Interrupted before duplicate save", exception);
        }
        repository.save(ownerA.getId(), ownerA.getMobileNumber(), recipient.getBankAccount().getAccountNumber(),
            label);
    }

    private User create(JdbcUserRepository users, String mobile, String name) throws Exception {
        return users.save(new User(0, mobile, "1234", name,
            new BankAccount("PENDING", name, BigDecimal.ZERO.setScale(2))));
    }
}
