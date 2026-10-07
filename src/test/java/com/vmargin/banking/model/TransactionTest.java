package com.vmargin.banking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransactionTest {
    @Test
    void savingsLedgerTypesUseCashMovementDirection() {
        assertFalse(TransactionType.SAVINGS_CONTRIBUTION.isIncoming());
        assertTrue(TransactionType.SAVINGS_WITHDRAWAL.isIncoming());
        assertFalse(TransactionType.BILL_PAYMENT.isIncoming());
    }


    @Test
    void acceptsAValidCashInTransaction() {
        Transaction transaction = new Transaction(
            0L,
            1L,
            TransactionType.CASH_IN,
            new BigDecimal("500.00"),
            "Initial cash in",
            LocalDateTime.now()
        );

        assertEquals(TransactionType.CASH_IN, transaction.getType());
        assertEquals(new BigDecimal("500.00"), transaction.getAmount());
        assertEquals(TransactionCategory.INCOME, transaction.getCategory());
    }

    @Test
    void rejectsACategoryThatDoesNotMatchTheLedgerType() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(0L, 1L, TransactionType.CASH_IN,
            new BigDecimal("10.00"), "Cash in", LocalDateTime.now(), null, TransactionCategory.SHOPPING));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Transaction(
                0L,
                1L,
                TransactionType.CASH_IN,
                BigDecimal.ZERO,
                "Cash in",
                LocalDateTime.now()
            )
        );
    }

    @Test
    void rejectsExcessiveAmountPrecision() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Transaction(
                0L,
                1L,
                TransactionType.CASH_IN,
                new BigDecimal("10.123"),
                "Cash in",
                LocalDateTime.now()
            )
        );
    }

    @Test
    void rejectsMissingDetails() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Transaction(
                0L,
                1L,
                TransactionType.CASH_IN,
                new BigDecimal("10.00"),
                "",
                LocalDateTime.now()
            )
        );
    }
}
