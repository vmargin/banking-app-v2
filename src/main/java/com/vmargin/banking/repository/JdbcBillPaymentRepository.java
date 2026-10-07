package com.vmargin.banking.repository;

import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.service.exception.InsufficientBalanceException;
import com.vmargin.banking.util.DatabaseConnection;
import com.vmargin.banking.util.MoneyValidation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

public class JdbcBillPaymentRepository implements BillPaymentRepository {
    @Override
    public void pay(long ownerId, DemoBiller biller, String referenceLastFour, BigDecimal amount,
                    LocalDateTime occurredAt, String operationReference) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            pay(connection, ownerId, MoneyWrites.primaryAccountId(connection, ownerId), biller,
                referenceLastFour, amount, occurredAt, operationReference);
        }
    }

    @Override
    public void payFromAccount(long ownerId, long accountId, DemoBiller biller, String referenceLastFour,
                               BigDecimal amount, LocalDateTime occurredAt, String operationReference)
        throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            pay(connection, ownerId, accountId, biller, referenceLastFour, amount, occurredAt, operationReference);
        }
    }

    private void pay(Connection connection, long ownerId, long accountId, DemoBiller biller,
                     String referenceLastFour, BigDecimal amount, LocalDateTime occurredAt,
                     String operationReference) throws SQLException {
        Objects.requireNonNull(biller, "Demo biller is required");
        MoneyValidation.amount(amount);
        if (referenceLastFour == null || !referenceLastFour.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Bill reference mask is invalid.");
        }
        String details = "Simulated bill payment · " + biller.displayLabel()
            + " · reference ending " + referenceLastFour + " · no provider contacted";
        connection.setAutoCommit(false);
        try {
            MoneyWrites.claim(connection, ownerId, operationReference, "BILL_PAYMENT");
            MoneyWrites.AccountState account = MoneyWrites.lockAccount(connection, accountId);
            if (account.ownerId() != ownerId || !"PHP".equals(account.currencyCode())) {
                throw new IllegalArgumentException("Choose an eligible PHP account for this demo bill payment.");
            }
            BigDecimal nextBalance = account.balance().subtract(amount);
            if (nextBalance.signum() < 0) {
                throw new InsufficientBalanceException("Insufficient available balance for this demo payment.");
            }
            MoneyWrites.balanceAccount(connection, account, nextBalance);
            MoneyWrites.ledger(connection, ownerId, accountId, TransactionType.BILL_PAYMENT, amount,
                details, occurredAt, operationReference);
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
