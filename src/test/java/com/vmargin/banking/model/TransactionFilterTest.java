package com.vmargin.banking.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionFilterTest {
    @Test
    void acceptsSupportedJdbcDateRangeEndpoints() {
        TransactionFilter filter = new TransactionFilter(null, TransactionFilter.MIN_SUPPORTED_DATE,
            TransactionFilter.MAX_SUPPORTED_DATE, "");

        assertEquals(TransactionFilter.MIN_SUPPORTED_DATE, filter.fromDate());
        assertEquals(TransactionFilter.MAX_SUPPORTED_DATE, filter.toDate());
    }

    @Test
    void rejectsOutOfRangeFromAndToDates() {
        assertThrows(IllegalArgumentException.class,
            () -> new TransactionFilter(null, LocalDate.MIN, null, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new TransactionFilter(null, LocalDate.MAX, null, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new TransactionFilter(null, null, LocalDate.MAX, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new TransactionFilter(null, null, LocalDate.MIN, ""));
        assertThrows(IllegalArgumentException.class,
            () -> new TransactionFilter(null, null, LocalDate.of(9999, 1, 1), ""));
    }

    @Test
    void rejectsReversedDatesWithinTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionFilter(null,
            LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), ""));
    }
}
