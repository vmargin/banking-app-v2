package com.vmargin.banking.repository;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionActivity;
import com.vmargin.banking.model.TransactionFilter;
import com.vmargin.banking.model.MonthlyTransactionInsights;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.TransactionCategory;
import com.vmargin.banking.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JdbcTransactionRepository implements TransactionRepository {

    private static final String SPENDING_TYPE_NAMES = Arrays.stream(TransactionType.values())
        .filter(TransactionType::isSpending)
        .map(TransactionType::name)
        .map(name -> "'" + name + "'")
        .collect(Collectors.joining(", "));

    private static final String SAVE_SQL = """
        INSERT INTO transactions (user_id, account_id, type, category, amount, details, occurred_at, reference)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String FIND_BY_USER_SQL = """
        SELECT t.id, t.user_id, t.account_id, a.account_name, a.currency_code,
               t.type, t.category, t.amount, t.details, t.occurred_at, t.reference
        FROM transactions t JOIN accounts a ON a.id = t.account_id
        WHERE t.user_id = ?
        ORDER BY t.occurred_at DESC, t.id DESC
        """;

    private static final String FIND_ALL_SQL = """
        SELECT t.id, t.user_id, t.account_id, a.account_name, a.currency_code,
               t.type, t.category, t.amount, t.details, t.occurred_at, t.reference
        FROM transactions t JOIN accounts a ON a.id = t.account_id
        ORDER BY t.occurred_at DESC, t.id DESC
        """;

    private static final String SELECT_TRANSACTION_COLUMNS = """
        SELECT t.id, t.user_id, t.account_id, a.account_name, a.currency_code,
               t.type, t.category, t.amount, t.details, t.occurred_at, t.reference
        FROM transactions t JOIN accounts a ON a.id = t.account_id
        """;

    @Override
    public Transaction save(Transaction transaction) throws SQLException {
        Objects.requireNonNull(transaction, "Transaction is required");

        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(SAVE_SQL, new String[]{"id"})
        ) {
            statement.setLong(1, transaction.getUserId());
            long accountId = MoneyWrites.primaryAccountId(connection, transaction.getUserId());
            statement.setLong(2, accountId);
            statement.setString(3, transaction.getType().name());
            statement.setString(4, transaction.getCategory().name());
            statement.setBigDecimal(5, transaction.getAmount());
            statement.setString(6, transaction.getDetails());
            statement.setTimestamp(7, Timestamp.valueOf(transaction.getOccurredAt()));
            statement.setString(8, transaction.getReference());

            statement.executeUpdate();
            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (!resultSet.next()) {
                    throw new SQLException("Transaction ID was not generated");
                }
                String accountName;
                String currencyCode;
                try (PreparedStatement account = connection.prepareStatement(
                    "SELECT account_name, currency_code FROM accounts WHERE id = ?")) {
                    account.setLong(1, accountId);
                    try (ResultSet accountRow = account.executeQuery()) {
                        if (!accountRow.next()) {
                            throw new SQLException("Transaction account was not found");
                        }
                        accountName = accountRow.getString("account_name");
                        currencyCode = accountRow.getString("currency_code");
                    }
                }
                return new Transaction(resultSet.getLong(1), transaction.getUserId(), accountId, accountName,
                    currencyCode, transaction.getType(), transaction.getAmount(), transaction.getDetails(),
                    transaction.getOccurredAt(), transaction.getReference(), transaction.getCategory());
            }
        }
    }

    @Override
    public List<Transaction> findByUserId(long userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        List<Transaction> transactions = new ArrayList<>();
        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(FIND_BY_USER_SQL)
        ) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(mapTransaction(resultSet));
                }
            }
        }
        return List.copyOf(transactions);
    }

    @Override
    public TransactionActivity findActivity(long userId, TransactionFilter filter, int page, int pageSize)
        throws SQLException {
        validateOwnerAndFilter(userId, filter);
        if (page < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Activity page values are invalid.");
        }
        FilterSql where = filterSql(userId, filter);
        long filteredCount;
        BigDecimal incomingTotal;
        BigDecimal outgoingTotal;
        long accountActivityCount;
        try (Connection connection = DatabaseConnection.open()) {
            String countSql = "SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id"
                + where.clause();
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, where.parameters());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    filteredCount = result.getLong(1);
                }
            }
            List<TransactionActivity.CurrencyTotal> currencyTotals = new ArrayList<>();
            String summarySql = """
                SELECT a.currency_code,
                    COALESCE(SUM(CASE WHEN t.type IN ('CASH_IN', 'TRANSFER_RECEIVED', 'SAVINGS_WITHDRAWAL')
                        THEN t.amount END), CAST(0 AS DECIMAL(15, 2))),
                    COALESCE(SUM(CASE WHEN t.type IN ('TRANSFER_SENT', 'SAVINGS_CONTRIBUTION', 'BILL_PAYMENT',
                        'CARD_PURCHASE') THEN t.amount END), CAST(0 AS DECIMAL(15, 2)))
                FROM transactions t JOIN accounts a ON a.id = t.account_id
                """ + where.clause() + " GROUP BY a.currency_code ORDER BY a.currency_code";
            try (PreparedStatement statement = connection.prepareStatement(summarySql)) {
                bind(statement, where.parameters());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        currencyTotals.add(new TransactionActivity.CurrencyTotal(
                            result.getString(1), result.getBigDecimal(2), result.getBigDecimal(3)));
                    }
                }
            }
            incomingTotal = currencyTotals.stream().filter(total -> "PHP".equals(total.currencyCode()))
                .map(TransactionActivity.CurrencyTotal::incoming).findFirst().orElse(BigDecimal.ZERO.setScale(2));
            outgoingTotal = currencyTotals.stream().filter(total -> "PHP".equals(total.currencyCode()))
                .map(TransactionActivity.CurrencyTotal::outgoing).findFirst().orElse(BigDecimal.ZERO.setScale(2));
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ?")) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    accountActivityCount = result.getLong(1);
                }
            }
            int effectivePage = effectivePage(page, pageSize, filteredCount);
            long offset = (long) effectivePage * pageSize;
            String activitySql = SELECT_TRANSACTION_COLUMNS + where.clause()
                + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(activitySql)) {
                int nextParameter = bind(statement, where.parameters()) + 1;
                statement.setInt(nextParameter++, pageSize);
                statement.setLong(nextParameter, offset);
                try (ResultSet result = statement.executeQuery()) {
                    List<Transaction> transactions = mapTransactions(result);
                    return new TransactionActivity(transactions, filter, effectivePage, pageSize, filteredCount,
                        accountActivityCount, incomingTotal, outgoingTotal, currencyTotals);
                }
            }
        }
    }

    @Override
    public MonthlyTransactionInsights findMonthlyInsights(long userId, YearMonth month) throws SQLException {
        if (userId <= 0 || month == null || month.getYear() < 1 || month.getYear() > 9998) {
            throw new IllegalArgumentException("The account or selected month is invalid.");
        }
        String sql = """
            SELECT t.type, COUNT(*), SUM(t.amount)
            FROM transactions t JOIN accounts a ON a.id = t.account_id
            WHERE t.user_id = ? AND a.currency_code = 'PHP' AND t.occurred_at >= ? AND t.occurred_at < ?
            GROUP BY t.type
            """;
        List<MonthlyTransactionInsights.TypeTotal> totals = new ArrayList<>();
        String categorySql = """
            SELECT t.category, COUNT(*), SUM(t.amount)
            FROM transactions t JOIN accounts a ON a.id = t.account_id
            WHERE t.user_id = ? AND a.currency_code = 'PHP' AND t.occurred_at >= ? AND t.occurred_at < ?
              AND t.type IN (%s)
            GROUP BY t.category
            """.formatted(SPENDING_TYPE_NAMES);
        List<MonthlyTransactionInsights.CategoryTotal> categoryTotals = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setTimestamp(2, Timestamp.valueOf(month.atDay(1).atStartOfDay()));
            statement.setTimestamp(3, Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    totals.add(new MonthlyTransactionInsights.TypeTotal(
                        TransactionType.valueOf(result.getString(1)), result.getLong(2), result.getBigDecimal(3)));
                }
            }
            try (PreparedStatement categories = connection.prepareStatement(categorySql)) {
                categories.setLong(1, userId);
                categories.setTimestamp(2, Timestamp.valueOf(month.atDay(1).atStartOfDay()));
                categories.setTimestamp(3, Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay()));
                try (ResultSet result = categories.executeQuery()) {
                    while (result.next()) {
                        categoryTotals.add(new MonthlyTransactionInsights.CategoryTotal(
                            TransactionCategory.valueOf(result.getString(1)), result.getLong(2),
                            result.getBigDecimal(3)));
                    }
                }
            }
        }
        return new MonthlyTransactionInsights(month, totals, categoryTotals);
    }

    @Override
    public List<Transaction> findStatement(long userId, TransactionFilter filter, int limit) throws SQLException {
        validateOwnerAndFilter(userId, filter);
        if (limit <= 0) {
            throw new IllegalArgumentException("Statement limit must be positive.");
        }
        FilterSql where = filterSql(userId, filter);
        String sql = SELECT_TRANSACTION_COLUMNS + where.clause()
            + " ORDER BY occurred_at DESC, id DESC LIMIT ?";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int nextParameter = bind(statement, where.parameters()) + 1;
            statement.setInt(nextParameter, limit);
            try (ResultSet result = statement.executeQuery()) {
                return List.copyOf(mapTransactions(result));
            }
        }
    }

    @Override
    public Optional<Transaction> findReceipt(long userId, String reference) throws SQLException {
        if (userId <= 0 || reference == null || reference.isBlank() || reference.length() > 36) {
            return Optional.empty();
        }
        String sql = SELECT_TRANSACTION_COLUMNS
            + " WHERE t.user_id = ? AND t.reference = ? ORDER BY t.occurred_at DESC, t.id DESC LIMIT 1";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, reference);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapTransaction(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Set<Long> findReadNotificationIds(long userId, List<Long> transactionIds) throws SQLException {
        List<Long> ids = validateNotificationIds(userId, transactionIds);
        if (ids.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = """
            SELECT r.transaction_id
            FROM notification_reads r
            JOIN transactions t ON t.id = r.transaction_id AND t.user_id = r.user_id
            WHERE r.user_id = ? AND r.transaction_id IN (%s)
            """.formatted(placeholders);
        Set<Long> readIds = new LinkedHashSet<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            for (int index = 0; index < ids.size(); index++) {
                statement.setLong(index + 2, ids.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    readIds.add(result.getLong(1));
                }
            }
        }
        return Set.copyOf(readIds);
    }

    @Override
    public void markNotificationsRead(long userId, List<Long> transactionIds) throws SQLException {
        List<Long> ids = validateNotificationIds(userId, transactionIds);
        if (ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = """
            INSERT INTO notification_reads (user_id, transaction_id)
            SELECT ?, t.id
            FROM transactions t
            WHERE t.user_id = ? AND t.id IN (%s)
              AND NOT EXISTS (
                  SELECT 1 FROM notification_reads r
                  WHERE r.user_id = ? AND r.transaction_id = t.id
              )
            """.formatted(placeholders);
        try (Connection connection = DatabaseConnection.open()) {
            boolean ownsTransaction = connection.getAutoCommit();
            if (ownsTransaction) {
                connection.setAutoCommit(false);
            }
            Savepoint savepoint = ownsTransaction ? null : connection.setSavepoint();
            try {
                try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    lock.setLong(1, userId);
                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            finish(connection, ownsTransaction, savepoint);
                            return;
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, userId);
                    statement.setLong(2, userId);
                    for (int index = 0; index < ids.size(); index++) {
                        statement.setLong(index + 3, ids.get(index));
                    }
                    statement.setLong(ids.size() + 3, userId);
                    statement.executeUpdate();
                }
                finish(connection, ownsTransaction, savepoint);
            } catch (SQLException | RuntimeException exception) {
                if (ownsTransaction) {
                    connection.rollback();
                } else {
                    connection.rollback(savepoint);
                }
                throw exception;
            } finally {
                if (ownsTransaction) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private List<Long> validateNotificationIds(long userId, List<Long> transactionIds) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        List<Long> ids = List.copyOf(Objects.requireNonNull(transactionIds, "Transaction IDs are required"));
        if (ids.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("Transaction IDs must be positive");
        }
        return ids.stream().distinct().toList();
    }

    private void finish(Connection connection, boolean ownsTransaction, Savepoint savepoint) throws SQLException {
        if (ownsTransaction) {
            connection.commit();
        } else {
            connection.releaseSavepoint(savepoint);
        }
    }

    private FilterSql filterSql(long userId, TransactionFilter filter) {
        StringBuilder clause = new StringBuilder(" WHERE t.user_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        if (filter.type() != null) {
            clause.append(" AND t.type = ?");
            parameters.add(filter.type().name());
        }
        if (filter.accountId() != null) {
            clause.append(" AND t.account_id = ?");
            parameters.add(filter.accountId());
        }
        if (filter.fromDate() != null) {
            clause.append(" AND t.occurred_at >= ?");
            parameters.add(Timestamp.valueOf(filter.fromDate().atStartOfDay()));
        }
        if (filter.toDate() != null) {
            clause.append(" AND t.occurred_at < ?");
            parameters.add(Timestamp.valueOf(filter.toDate().plusDays(1).atStartOfDay()));
        }
        if (!filter.searchText().isEmpty()) {
            clause.append(" AND (LOWER(t.details) LIKE ? ESCAPE '!' OR LOWER(t.reference) LIKE ? ESCAPE '!')");
            String search = "%" + escapeLike(filter.searchText().toLowerCase(java.util.Locale.ROOT)) + "%";
            parameters.add(search);
            parameters.add(search);
        }
        return new FilterSql(clause.toString(), List.copyOf(parameters));
    }

    private int bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            if (parameter instanceof String text) {
                statement.setString(index++, text);
            } else if (parameter instanceof Timestamp timestamp) {
                statement.setTimestamp(index++, timestamp);
            } else if (parameter instanceof Long userId) {
                statement.setLong(index++, userId);
            } else {
                throw new IllegalArgumentException("Unsupported transaction filter parameter.");
            }
        }
        return index - 1;
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private int effectivePage(int page, int pageSize, long filteredCount) {
        if (filteredCount == 0) {
            return 0;
        }
        long lastPage = (filteredCount - 1) / pageSize;
        return (int) Math.min(page, lastPage);
    }

    private void validateOwnerAndFilter(long userId, TransactionFilter filter) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive.");
        }
        Objects.requireNonNull(filter, "Transaction filter is required");
    }

    private List<Transaction> mapTransactions(ResultSet resultSet) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        while (resultSet.next()) {
            transactions.add(mapTransaction(resultSet));
        }
        return transactions;
    }

    private record FilterSql(String clause, List<Object> parameters) {
    }

    @Override
    public List<Transaction> findAll() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        try (
            Connection connection = DatabaseConnection.open();
            PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                transactions.add(mapTransaction(resultSet));
            }
        }
        return List.copyOf(transactions);
    }

    private Transaction mapTransaction(ResultSet resultSet) throws SQLException {
        return new Transaction(
            resultSet.getLong("id"),
            resultSet.getLong("user_id"),
            resultSet.getLong("account_id"),
            resultSet.getString("account_name"),
            resultSet.getString("currency_code"),
            TransactionType.valueOf(resultSet.getString("type")),
            resultSet.getBigDecimal("amount"),
            resultSet.getString("details"),
            resultSet.getTimestamp("occurred_at").toLocalDateTime(),
            resultSet.getString("reference"),
            TransactionCategory.valueOf(resultSet.getString("category"))
        );
    }
}
