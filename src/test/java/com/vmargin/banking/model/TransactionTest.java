package com.vmargin.banking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransactionTest {

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
