package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Owner-scoped, filtered activity and its summary values. */
public record TransactionActivity(
    List<Transaction> transactions,
    TransactionFilter filter,
    int page,
    int pageSize,
    long filteredCount,
    long accountActivityCount,
    BigDecimal incomingTotal,
    BigDecimal outgoingTotal,
    List<CurrencyTotal> currencyTotals
) {
    public TransactionActivity {
        transactions = List.copyOf(Objects.requireNonNull(transactions, "Transactions are required"));
        Objects.requireNonNull(filter, "Transaction filter is required");
        Objects.requireNonNull(incomingTotal, "Incoming total is required");
        Objects.requireNonNull(outgoingTotal, "Outgoing total is required");
        currencyTotals = List.copyOf(Objects.requireNonNull(currencyTotals, "Currency totals are required"));
        if (page < 0 || pageSize <= 0 || filteredCount < 0 || accountActivityCount < 0) {
            throw new IllegalArgumentException("Activity page values are invalid.");
        }
    }

    public TransactionActivity(List<Transaction> transactions, TransactionFilter filter, int page, int pageSize,
                               long filteredCount, long accountActivityCount, BigDecimal incomingTotal,
                               BigDecimal outgoingTotal) {
        this(transactions, filter, page, pageSize, filteredCount, accountActivityCount, incomingTotal,
            outgoingTotal, List.of(new CurrencyTotal("PHP", incomingTotal, outgoingTotal)));
    }

    public long firstEntry() {
        return transactions.isEmpty() ? 0 : (long) page * pageSize + 1;
    }

    public long lastEntry() {
        return Math.min(filteredCount, (long) page * pageSize + transactions.size());
    }

    public int totalPages() {
        return filteredCount == 0 ? 0 : (int) (1 + (filteredCount - 1) / pageSize);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return (long) page + 1 < totalPages();
    }

    public record CurrencyTotal(String currencyCode, BigDecimal incoming, BigDecimal outgoing) {
        public CurrencyTotal {
            if (!"PHP".equals(currencyCode) && !"USD".equals(currencyCode)) {
                throw new IllegalArgumentException("Unsupported activity currency");
            }
            Objects.requireNonNull(incoming, "Incoming total is required");
            Objects.requireNonNull(outgoing, "Outgoing total is required");
            if (incoming.signum() < 0 || outgoing.signum() < 0) {
                throw new IllegalArgumentException("Activity totals cannot be negative");
            }
        }
    }
}
