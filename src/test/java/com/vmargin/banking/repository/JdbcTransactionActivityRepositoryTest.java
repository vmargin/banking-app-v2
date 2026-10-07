package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionCategory;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.User;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.JdbcSchemaMigrator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTransactionActivityRepositoryTest {
    private JdbcTransactionRepository transactions;
    private User owner;
    private User other;
    private final String sharedReference = UUID.randomUUID().toString();

    @BeforeEach
    void setup() throws Exception {
        DatabaseConnection.useLocalH2("jdbc:h2:mem:activity_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        try (Connection connection = DatabaseConnection.open()) {
            new JdbcSchemaMigrator().migrate(connection);
        }
        JdbcUserRepository users = new JdbcUserRepository();
        owner = create(users, "09990000021", "Owner");
        other = create(users, "09990000022", "Other");
        transactions = new JdbcTransactionRepository();
        seedActivity();
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
    void inclusiveDateFiltersAreOwnerScopedAndKeepPageCountSeparateFromAccountCount() throws Exception {
        TransactionFilter filter = new TransactionFilter(null,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "");

        TransactionActivity firstPage = transactions.findActivity(owner.getId(), filter, 0, 1);
        TransactionActivity laterPage = transactions.findActivity(owner.getId(), filter, 50, 1);

        assertEquals(2, firstPage.filteredCount());
        assertEquals(4, firstPage.accountActivityCount());
        assertEquals(1, firstPage.transactions().size());
        assertEquals(0, firstPage.page());
        assertEquals(1, firstPage.firstEntry());
        assertEquals(1, firstPage.lastEntry());
        assertEquals("30.00", firstPage.outgoingTotal().toPlainString());
        assertEquals("100.00", firstPage.incomingTotal().toPlainString());
        assertEquals(1, laterPage.page());
        assertEquals(TransactionType.CASH_IN, laterPage.transactions().getFirst().getType());

        TransactionActivity sentOnly = transactions.findActivity(owner.getId(),
            new TransactionFilter(TransactionType.TRANSFER_SENT, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), ""), 0, 10);
        assertEquals(1, sentOnly.filteredCount());
        assertEquals("30.00", sentOnly.outgoingTotal().toPlainString());
        assertEquals("0.00", sentOnly.incomingTotal().toPlainString());
        assertEquals(List.of(), transactions.findByUserId(999999));
    }

    @Test
    void sameTimestampOrderingIsStableAndSearchTreatsWildcardsLiterally() throws Exception {
        TransactionFilter allOnSecond = new TransactionFilter(null, LocalDate.of(2026, 9, 2),
            LocalDate.of(2026, 9, 2), "");
        TransactionActivity firstPage = transactions.findActivity(owner.getId(), allOnSecond, 0, 1);

        assertEquals(2, firstPage.filteredCount());
        assertEquals("Newer id", firstPage.transactions().getFirst().getDetails());

        TransactionActivity wildcardSearch = transactions.findActivity(owner.getId(),
            new TransactionFilter(null, null, null, "%"), 0, 10);
        assertEquals(1, wildcardSearch.filteredCount());
        assertEquals("Coffee 100%", wildcardSearch.transactions().getFirst().getDetails());
        assertTrue(transactions.findStatement(owner.getId(), new TransactionFilter(null,
            LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2), "%"), 10)
            .stream().allMatch(transaction -> transaction.getUserId() == owner.getId()));
    }

    @Test
    void receiptLookupFindsEachTransferSideOnlyForAnOwnerWithThatReference() throws Exception {
        var senderReceipt = transactions.findReceipt(owner.getId(), sharedReference);
        var receiverReceipt = transactions.findReceipt(other.getId(), sharedReference);

        assertTrue(senderReceipt.isPresent());
        assertTrue(receiverReceipt.isPresent());
        assertEquals(TransactionType.TRANSFER_SENT, senderReceipt.orElseThrow().getType());
        assertEquals(TransactionType.TRANSFER_RECEIVED, receiverReceipt.orElseThrow().getType());
        assertEquals(senderReceipt.orElseThrow().getReference(), receiverReceipt.orElseThrow().getReference());
        assertFalse(transactions.findReceipt(999999, sharedReference).isPresent());
    }

    @Test
    void notificationReadStatePersistsAndIsScopedToTheOwningAccount() throws Exception {
        long ownerTransactionId = transactions.findByUserId(owner.getId()).getFirst().getId();
        long otherTransactionId = transactions.findByUserId(other.getId()).getFirst().getId();

        assertEquals(Set.of(), transactions.findReadNotificationIds(owner.getId(),
            List.of(ownerTransactionId, otherTransactionId)));
        transactions.markNotificationsRead(owner.getId(), List.of(ownerTransactionId, otherTransactionId));
        transactions.markNotificationsRead(owner.getId(), List.of(ownerTransactionId));

        assertEquals(Set.of(ownerTransactionId), transactions.findReadNotificationIds(owner.getId(),
            List.of(ownerTransactionId, otherTransactionId)));
        assertEquals(Set.of(), transactions.findReadNotificationIds(other.getId(), List.of(ownerTransactionId)));
    }

    @Test
    void monthlyInsightsAggregateOnlyOwnersSavedTypesAndSeparateSavingsMovements() throws Exception {
        LocalDateTime monthEnd = LocalDateTime.of(2026, 9, 30, 23, 59);
        transactions.save(transaction(owner, TransactionType.BILL_PAYMENT, "7.00", "Demo power bill",
            monthEnd, UUID.randomUUID().toString()));
        transactions.save(transaction(owner, TransactionType.SAVINGS_CONTRIBUTION, "20.00", "Rainy day",
            monthEnd, UUID.randomUUID().toString()));
        transactions.save(transaction(owner, TransactionType.SAVINGS_WITHDRAWAL, "8.00", "Rainy day",
            monthEnd, UUID.randomUUID().toString()));
        transactions.save(transaction(owner, TransactionType.CASH_IN, "3.00", "Next month",
            LocalDateTime.of(2026, 10, 1, 0, 0), UUID.randomUUID().toString()));

        MonthlyTransactionInsights insights = transactions.findMonthlyInsights(owner.getId(), YearMonth.of(2026, 9));

        assertEquals(7, insights.typeTotals().size());
        assertEquals(7, insights.transactionCount());
        assertEquals("105.00", amount(insights, TransactionType.CASH_IN));
        assertEquals("0.00", amount(insights, TransactionType.TRANSFER_RECEIVED));
        assertEquals("34.00", amount(insights, TransactionType.TRANSFER_SENT));
        assertEquals("7.00", amount(insights, TransactionType.BILL_PAYMENT));
        assertEquals("20.00", amount(insights, TransactionType.SAVINGS_CONTRIBUTION));
        assertEquals("8.00", amount(insights, TransactionType.SAVINGS_WITHDRAWAL));
        assertEquals("105.00", insights.moneyInRecorded().toPlainString());
        assertEquals("7.00", insights.moneyOutRecorded().toPlainString());
        assertEquals("20.00", insights.savingsContributed().toPlainString());
        assertEquals("8.00", insights.savingsReturned().toPlainString());
    }

    @Test
    void monthlySpendingGroupsPersistedOwnerCategoriesAndExcludesTransfers() throws Exception {
        LocalDateTime inMonth = LocalDateTime.of(2026, 9, 15, 12, 0);
        transactions.save(transaction(owner, TransactionType.CARD_PURCHASE, "235.00", "Coffee",
            inMonth, UUID.randomUUID().toString(), TransactionCategory.FOOD_AND_DINING));
        transactions.save(transaction(owner, TransactionType.CARD_PURCHASE, "320.00", "Ride share",
            inMonth, UUID.randomUUID().toString(), TransactionCategory.TRANSPORTATION));
        transactions.save(transaction(owner, TransactionType.CARD_PURCHASE, "549.00", "Streaming",
            inMonth, UUID.randomUUID().toString(), TransactionCategory.ENTERTAINMENT));
        transactions.save(transaction(owner, TransactionType.BILL_PAYMENT, "100.00", "Power bill",
            inMonth, UUID.randomUUID().toString()));

        MonthlyTransactionInsights insights = transactions.findMonthlyInsights(owner.getId(), YearMonth.of(2026, 9));

        assertEquals("1204.00", insights.moneyOutRecorded().toPlainString());
        assertEquals("235.00", categoryAmount(insights, TransactionCategory.FOOD_AND_DINING));
        assertEquals("320.00", categoryAmount(insights, TransactionCategory.TRANSPORTATION));
        assertEquals("549.00", categoryAmount(insights, TransactionCategory.ENTERTAINMENT));
        assertEquals("100.00", categoryAmount(insights, TransactionCategory.BILLS_AND_UTILITIES));
        assertEquals(19, insights.spendingPercentage(insights.categoryTotals().stream()
            .filter(total -> total.category() == TransactionCategory.FOOD_AND_DINING).findFirst().orElseThrow()));
    }

    private User create(JdbcUserRepository users, String mobile, String name) throws Exception {
        return users.save(new User(0, mobile, "1234", name,
            new BankAccount("PENDING", name, BigDecimal.ZERO)));
    }

    private void seedActivity() throws Exception {
        LocalDateTime firstDayStart = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime firstDayEnd = LocalDateTime.of(2026, 9, 1, 23, 59, 59, 999_000_000);
        LocalDateTime secondDay = LocalDateTime.of(2026, 9, 2, 15, 0);
        transactions.save(transaction(owner, TransactionType.CASH_IN, "100.00", "Start funds",
            firstDayStart, UUID.randomUUID().toString()));
        transactions.save(transaction(owner, TransactionType.TRANSFER_SENT, "30.00", "Transfer out",
            firstDayEnd, sharedReference));
        transactions.save(transaction(owner, TransactionType.CASH_IN, "5.00", "Coffee 100%",
            secondDay, UUID.randomUUID().toString()));
        transactions.save(transaction(owner, TransactionType.TRANSFER_SENT, "4.00", "Newer id",
            secondDay, UUID.randomUUID().toString()));
        transactions.save(transaction(other, TransactionType.TRANSFER_RECEIVED, "30.00", "Transfer in",
            firstDayEnd, sharedReference));
    }

    private Transaction transaction(User user, TransactionType type, String amount, String details,
                                    LocalDateTime time, String reference) {
        return new Transaction(0, user.getId(), type, new BigDecimal(amount), details, time, reference);
    }

    private Transaction transaction(User user, TransactionType type, String amount, String details,
                                    LocalDateTime time, String reference, TransactionCategory category) {
        return new Transaction(0, user.getId(), type, new BigDecimal(amount), details, time, reference, category);
    }

    private String amount(MonthlyTransactionInsights insights, TransactionType type) {
        return insights.typeTotals().stream().filter(total -> total.type() == type)
            .findFirst().orElseThrow().amount().toPlainString();
    }

    private String categoryAmount(MonthlyTransactionInsights insights, TransactionCategory category) {
        return insights.categoryTotals().stream().filter(total -> total.category() == category)
            .findFirst().orElseThrow().amount().toPlainString();
    }
}
