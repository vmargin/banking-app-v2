package com.vmargin.banking.repository;

import com.vmargin.banking.model.MoneyRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MoneyRequestRepository {
    List<MoneyRequest> findIncoming(long payerId) throws SQLException;

    List<MoneyRequest> findOutgoing(long requesterId) throws SQLException;

    Optional<MoneyRequest> findPendingIncoming(long payerId, long requestId) throws SQLException;

    MoneyRequest create(long requesterId, long requesterAccountId, String payerAccountNumber,
                        BigDecimal amount, String note, String reference, LocalDateTime createdAt)
        throws SQLException;

    BigDecimal pay(long payerId, long requestId, long sourceAccountId, String paymentReference,
                   LocalDateTime occurredAt) throws SQLException;

    boolean decline(long payerId, long requestId, LocalDateTime updatedAt) throws SQLException;

    boolean cancel(long requesterId, long requestId, LocalDateTime updatedAt) throws SQLException;
}
