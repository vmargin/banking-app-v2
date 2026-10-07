package com.vmargin.banking.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;

public interface CashInRepository {

    BigDecimal cashIn(
        long userId,
        BigDecimal amount,
        String details,
        LocalDateTime occurredAt
    ) throws SQLException;

    default BigDecimal cashIn(long userId, BigDecimal amount, String details,
                             LocalDateTime occurredAt, String reference) throws SQLException {
        return cashIn(userId, amount, details, occurredAt);
    }

    BigDecimal cashInAccount(long userId, long accountId, BigDecimal amount, String details,
                             LocalDateTime occurredAt, String reference) throws SQLException;
}
