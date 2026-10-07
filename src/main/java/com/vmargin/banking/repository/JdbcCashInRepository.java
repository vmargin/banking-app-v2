package com.vmargin.banking.repository;

import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;

public class JdbcCashInRepository implements CashInRepository {
    @Override
    public BigDecimal cashIn(long userId, BigDecimal amount, String details, LocalDateTime occurredAt)
        throws SQLException {
        return cashIn(userId, amount, details, occurredAt, UUID.randomUUID().toString());
    }

    @Override
    public BigDecimal cashIn(long userId, BigDecimal amount, String details,
                             LocalDateTime occurredAt, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            return cashInAccount(connection, userId, MoneyWrites.primaryAccountId(connection, userId),
                amount, details, occurredAt, reference);
        }
    }

    @Override
    public BigDecimal cashInAccount(long userId, long accountId, BigDecimal amount, String details,
                                   LocalDateTime occurredAt, String reference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            return cashInAccount(connection, userId, accountId, amount, details, occurredAt, reference);
        }
    }

    private BigDecimal cashInAccount(Connection connection, long userId, long accountId, BigDecimal amount,
                                     String details, LocalDateTime occurredAt, String reference)
        throws SQLException {
        MoneyValidation.amount(amount);
        MoneyValidation.details(details);
        connection.setAutoCommit(false);
        try {
            MoneyWrites.claim(connection, userId, reference, "CASH_IN");
            MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
            if (account.ownerId() != userId || !"PHP".equals(account.currencyCode())) {
                throw new IllegalArgumentException("Choose an eligible PHP account on this customer profile");
            }
            BigDecimal balance = account.balance().add(amount);
            MoneyWrites.balanceAccount(connection, account, balance);
            MoneyWrites.ledger(connection, userId, accountId, TransactionType.CASH_IN, amount,
                details, occurredAt, reference);
            connection.commit();
            return balance;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
