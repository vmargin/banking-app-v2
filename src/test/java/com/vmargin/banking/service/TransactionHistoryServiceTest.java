package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Test
    void activityUsesAuthenticatedOwnerAndCapsRequestedPageSize() throws Exception {
        RecordingTransactionRepository repository = new RecordingTransactionRepository();
        TransactionHistoryService service = new TransactionHistoryService(repository);

        TransactionActivity activity = service.getActivity(testUser(), TransactionFilter.empty(), 2, 500);

        assertEquals(1L, repository.requestedUserId);
        assertEquals(2, repository.requestedPage);
        assertEquals(TransactionHistoryService.MAX_PAGE_SIZE, repository.requestedPageSize);
        assertEquals(2, activity.page());
    }

    @Test
    void receiptAndStatementUseAuthenticatedOwner() throws Exception {
        RecordingTransactionRepository repository = new RecordingTransactionRepository();
        TransactionHistoryService service = new TransactionHistoryService(repository);

        service.getStatement(testUser(), TransactionFilter.empty());
        service.getReceipt(testUser(), "reference-1");

        assertEquals(1L, repository.requestedUserId);
        assertEquals(1L, repository.receiptOwnerId);
    }

    @Test
    void notificationReadStateUsesOnlyTheAuthenticatedOwnersRecentEntries() throws Exception {
        RecordingTransactionRepository repository = new RecordingTransactionRepository();
        repository.readNotificationIds = Set.of(8L);
        TransactionHistoryService service = new TransactionHistoryService(repository);
        List<Transaction> notices = List.of(
            new Transaction(8, 1, TransactionType.CASH_IN, new BigDecimal("20.00"), "Practice cash-in",
                LocalDateTime.of(2026, 9, 1, 12, 0)),
            new Transaction(9, 1, TransactionType.TRANSFER_SENT, new BigDecimal("5.00"), "Practice transfer",
                LocalDateTime.of(2026, 9, 2, 12, 0)));

        Set<Long> readIds = service.getReadNotificationIds(testUser(), notices);
        service.markRecentNotificationsRead(testUser(), notices);

        assertEquals(Set.of(8L), readIds);
        assertEquals(1L, repository.requestedUserId);
        assertEquals(List.of(8L, 9L), repository.notificationIds);
    }

    @Test
    void notificationReadStateRejectsAnotherAccountsTransaction() {
        TransactionHistoryService service = new TransactionHistoryService(new RecordingTransactionRepository());
        Transaction foreignNotice = new Transaction(8, 2, TransactionType.CASH_IN,
            new BigDecimal("20.00"), "Practice cash-in", LocalDateTime.of(2026, 9, 1, 12, 0));

        assertThrows(IllegalArgumentException.class,
            () -> service.markRecentNotificationsRead(testUser(), List.of(foreignNotice)));
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
        private long receiptOwnerId;
        private int requestedPage;
        private int requestedPageSize;
        private List<Long> notificationIds = List.of();
        private Set<Long> readNotificationIds = Set.of();

        @Override
        public Set<Long> findReadNotificationIds(long userId, List<Long> transactionIds) {
            requestedUserId = userId;
            notificationIds = transactionIds;
            return readNotificationIds;
        }

        @Override
        public void markNotificationsRead(long userId, List<Long> transactionIds) {
            requestedUserId = userId;
            notificationIds = transactionIds;
        }

        @Override
        public TransactionActivity findActivity(long userId, TransactionFilter filter, int page, int pageSize) {
            requestedUserId = userId;
            requestedPage = page;
            requestedPageSize = pageSize;
            return new TransactionActivity(List.of(), filter, page, pageSize, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO);
        }

        @Override
        public List<Transaction> findStatement(long userId, TransactionFilter filter, int limit) {
            requestedUserId = userId;
            return List.of();
        }

        @Override
        public Optional<Transaction> findReceipt(long userId, String reference) {
            receiptOwnerId = userId;
            return Optional.empty();
        }

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
