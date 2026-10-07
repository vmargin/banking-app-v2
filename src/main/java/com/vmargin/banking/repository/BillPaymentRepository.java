package com.vmargin.banking.repository;

import com.vmargin.banking.model.DemoBiller;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;

public interface BillPaymentRepository {
    void pay(long ownerId, DemoBiller biller, String referenceLastFour, BigDecimal amount,
             LocalDateTime occurredAt, String operationReference) throws SQLException;

    void payFromAccount(long ownerId, long accountId, DemoBiller biller, String referenceLastFour,
                        BigDecimal amount, LocalDateTime occurredAt, String operationReference) throws SQLException;
}
