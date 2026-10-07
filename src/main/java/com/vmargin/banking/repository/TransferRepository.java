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

    default BigDecimal transfer(long senderId, String senderMobileNumber, String recipientMobileNumber,
                                BigDecimal amount, LocalDateTime occurredAt, String reference) throws SQLException {
        return transfer(senderId, senderMobileNumber, recipientMobileNumber, amount, occurredAt);
    }

    BigDecimal transferToAccount(long senderId, long sourceAccountId, String recipientAccountNumber, BigDecimal amount,
                                 LocalDateTime occurredAt, String reference) throws SQLException;
}
