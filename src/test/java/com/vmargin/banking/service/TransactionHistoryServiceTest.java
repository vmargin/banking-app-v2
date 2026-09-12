package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionHistoryServiceTest {

    @Test
    void historyUsesTheAuthenticatedUsersId() throws Exception {
        RecordingTransactionRepository repository = new RecordingTransactionRepository();
        TransactionHistoryService service = new TransactionHistoryService(repository);
        User user = testUser();

        List<Transaction> history = service.getHistory(user);

        assertEquals(1L, repository.requestedUserId);
        assertEquals(1, history.size());
        assertEquals(TransactionType.CASH_IN, history.getFirst().getType());
    }

    private User testUser() {
        return new User(
            1L,
            "09990000001",
            "1234",
            "Test User",
            new BankAccount("ACC-1", "Test User", new BigDecimal("2500.00"))
        );
    }

    private static class RecordingTransactionRepository implements TransactionRepository {

        private long requestedUserId;

        @Override
        public Transaction save(Transaction transaction) {
            return transaction;
        }

        @Override
        public List<Transaction> findByUserId(long userId) {
            requestedUserId = userId;
            return List.of(new Transaction(
                1L,
                userId,
                TransactionType.CASH_IN,
                new BigDecimal("125.00"),
                "Cash-in test",
                LocalDateTime.of(2026, 9, 9, 8, 0)
            ));
        }
    }
}
