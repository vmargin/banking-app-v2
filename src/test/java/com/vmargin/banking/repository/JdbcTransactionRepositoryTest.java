package com.vmargin.banking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class JdbcTransactionRepositoryTest {

    @Test
    void savesAndFindsTransactionsForOneUser() throws SQLException {
        Assumptions.assumeTrue(
            System.getenv("BANKING_DB_USER") != null
                && System.getenv("BANKING_DB_PASSWORD") != null,
            "Database credentials are required"
        );

        Transaction transaction = new Transaction(
            0L,
            1L,
            TransactionType.CASH_IN,
            new BigDecimal("125.00"),
            "Feature 2 repository test",
            LocalDateTime.now()
        );

        TransactionRepository repository = new JdbcTransactionRepository();
        Transaction saved = repository.save(transaction);
        List<Transaction> results = repository.findByUserId(1L);

        assertEquals(TransactionType.CASH_IN, saved.getType());
        assertEquals(new BigDecimal("125.00"), saved.getAmount());
        assertFalse(results.isEmpty());
        assertEquals(1L, results.get(0).getUserId());
    }

    @Test
    void returnsNoTransactionsForAUserWithoutHistory() throws SQLException {
        Assumptions.assumeTrue(
            System.getenv("BANKING_DB_USER") != null
                && System.getenv("BANKING_DB_PASSWORD") != null,
            "Database credentials are required"
        );

        List<Transaction> results = new JdbcTransactionRepository().findByUserId(999999L);

        assertEquals(List.of(), results);
    }
}
