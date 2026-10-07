package com.vmargin.banking.model;

import java.time.LocalDate;

/** Validated filters shared by the activity page and statement export. */
public record TransactionFilter(TransactionType type, LocalDate fromDate, LocalDate toDate, String searchText,
                                Long accountId) {
    public static final int MAX_SEARCH_LENGTH = 100;
    public static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(1, 1, 1);
    public static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(9998, 12, 31);

    public TransactionFilter {
        if (accountId != null && accountId < 1) {
            throw new IllegalArgumentException("Account filter must be a positive account ID.");
        }
        if (!isSupported(fromDate) || !isSupported(toDate)) {
            throw new IllegalArgumentException("Dates must be between 0001-01-01 and 9998-12-31.");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Start date must be on or before end date.");
        }
        String normalizedSearch = searchText == null ? "" : searchText.trim();
        if (normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new IllegalArgumentException("Search text must be 100 characters or fewer.");
        }
        searchText = normalizedSearch;
    }

    public TransactionFilter(TransactionType type, LocalDate fromDate, LocalDate toDate, String searchText) {
        this(type, fromDate, toDate, searchText, null);
    }

    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, "", null);
    }

    private static boolean isSupported(LocalDate date) {
        return date == null || (date.getYear() >= MIN_SUPPORTED_DATE.getYear()
            && date.getYear() <= MAX_SUPPORTED_DATE.getYear());
    }
}
