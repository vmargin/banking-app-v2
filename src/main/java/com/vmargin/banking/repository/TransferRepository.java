package com.vmargin.banking.repository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;

public interface TransferRepository {

    BigDecimal transfer(
        long senderId,
        String senderMobileNumber,
        String recipientMobileNumber,
        BigDecimal amount,
        LocalDateTime occurredAt
    ) throws SQLException;
}
