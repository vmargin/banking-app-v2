package com.vmargin.banking.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Owner-scoped ledger totals for one calendar month, grouped by recorded operation type. */
public record MonthlyTransactionInsights(YearMonth month, List<TypeTotal> typeTotals,
                                         List<CategoryTotal> categoryTotals) {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public MonthlyTransactionInsights(YearMonth month, List<TypeTotal> typeTotals) {
        this(month, typeTotals, categoriesFromTypes(typeTotals));
    }

    public MonthlyTransactionInsights {
        Objects.requireNonNull(month, "Month is required");
        if (month.getYear() < 1 || month.getYear() > 9998) {
            throw new IllegalArgumentException("The selected month is outside the supported range.");
        }
        Objects.requireNonNull(typeTotals, "Transaction totals are required");
        Objects.requireNonNull(categoryTotals, "Category totals are required");

        EnumMap<TransactionType, TypeTotal> byType = new EnumMap<>(TransactionType.class);
        for (TypeTotal total : typeTotals) {
            Objects.requireNonNull(total, "Transaction total is required");
            if (byType.putIfAbsent(total.type(), total) != null) {
                throw new IllegalArgumentException("A transaction type can appear only once.");
            }
        }
        List<TypeTotal> orderedTotals = new ArrayList<>(TransactionType.values().length);
        for (TransactionType type : TransactionType.values()) {
            orderedTotals.add(byType.getOrDefault(type, new TypeTotal(type, 0, ZERO)));
        }
        typeTotals = List.copyOf(orderedTotals);

        EnumMap<TransactionCategory, CategoryTotal> byCategory = new EnumMap<>(TransactionCategory.class);
        for (CategoryTotal total : categoryTotals) {
            Objects.requireNonNull(total, "Category total is required");
            if (byCategory.putIfAbsent(total.category(), total) != null) {
                throw new IllegalArgumentException("A spending category can appear only once.");
            }
        }
        List<CategoryTotal> orderedCategories = new ArrayList<>(TransactionCategory.values().length);
        for (TransactionCategory category : TransactionCategory.values()) {
            if (category.isSpending()) {
                orderedCategories.add(byCategory.getOrDefault(category, new CategoryTotal(category, 0, ZERO)));
            }
        }
        categoryTotals = List.copyOf(orderedCategories);
    }

    public BigDecimal moneyInRecorded() {
        return amountByType(TransactionType::isIncome);
    }

    public BigDecimal moneyOutRecorded() {
        return amountByType(TransactionType::isSpending);
    }

    public BigDecimal savingsContributed() {
        return amount(TransactionType.SAVINGS_CONTRIBUTION);
    }

    public BigDecimal savingsReturned() {
        return amount(TransactionType.SAVINGS_WITHDRAWAL);
    }

    public long transactionCount() {
        return typeTotals.stream().mapToLong(TypeTotal::count).sum();
    }

    public boolean isEmpty() {
        return transactionCount() == 0;
    }

    public int spendingPercentage(CategoryTotal total) {
        Objects.requireNonNull(total, "Category total is required");
        BigDecimal outflow = moneyOutRecorded();
        if (outflow.signum() == 0 || total.amount().signum() == 0) {
            return 0;
        }
        return total.amount().multiply(BigDecimal.valueOf(100))
            .divide(outflow, 0, java.math.RoundingMode.DOWN).intValueExact();
    }

    public static MonthlyTransactionInsights empty(YearMonth month) {
        return new MonthlyTransactionInsights(month, List.of());
    }

    private static List<CategoryTotal> categoriesFromTypes(List<TypeTotal> totals) {
        Objects.requireNonNull(totals, "Transaction totals are required");
        EnumMap<TransactionCategory, CategoryTotal> byCategory = new EnumMap<>(TransactionCategory.class);
        for (TypeTotal total : totals) {
            if (total.type().isSpending()) {
                TransactionCategory category = TransactionCategory.forType(total.type());
                CategoryTotal previous = byCategory.get(category);
                long count = total.count() + (previous == null ? 0 : previous.count());
                BigDecimal amount = total.amount().add(previous == null ? ZERO : previous.amount());
                byCategory.put(category, new CategoryTotal(category, count, amount));
            }
        }
        return List.copyOf(byCategory.values());
    }

    private BigDecimal amount(TransactionType type) {
        return typeTotals.stream()
            .filter(total -> total.type() == type)
            .map(TypeTotal::amount)
            .findFirst()
            .orElse(ZERO);
    }

    private BigDecimal amountByType(java.util.function.Predicate<TransactionType> predicate) {
        return typeTotals.stream()
            .filter(total -> predicate.test(total.type()))
            .map(TypeTotal::amount)
            .reduce(ZERO, BigDecimal::add);
    }

    public record TypeTotal(TransactionType type, long count, BigDecimal amount) {
        public TypeTotal {
            Objects.requireNonNull(type, "Transaction type is required");
            Objects.requireNonNull(amount, "Transaction amount is required");
            if (count < 0 || amount.signum() < 0) {
                throw new IllegalArgumentException("Transaction totals cannot be negative.");
            }
        }
    }

    public record CategoryTotal(TransactionCategory category, long count, BigDecimal amount) {
        public CategoryTotal {
            Objects.requireNonNull(category, "Spending category is required");
            Objects.requireNonNull(amount, "Category amount is required");
            if (!category.isSpending() || count < 0 || amount.signum() < 0) {
                throw new IllegalArgumentException("Spending category totals are invalid.");
            }
        }
    }
}
