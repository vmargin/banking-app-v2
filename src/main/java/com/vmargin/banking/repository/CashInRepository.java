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
}
