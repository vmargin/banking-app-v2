package com.vmargin.banking.repository;

import com.vmargin.banking.model.SavingsGoal;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface SavingsGoalRepository {
    List<SavingsGoal> findByOwner(long ownerId) throws SQLException;
    SavingsGoal findByOwnerAndId(long ownerId, long goalId) throws SQLException;
    SavingsGoal create(long ownerId, String name, BigDecimal targetAmount) throws SQLException;
    SavingsGoal createDemoIfMissing(long ownerId, String name, BigDecimal targetAmount,
                                    BigDecimal initialSavedAmount, LocalDateTime occurredAt,
                                    String operationReference) throws SQLException;
    void move(long ownerId, long goalId, BigDecimal amount, boolean contribution,
              LocalDateTime occurredAt, String reference) throws SQLException;

    void moveFromAccount(long ownerId, long accountId, long goalId, BigDecimal amount,
                         boolean contribution, LocalDateTime occurredAt, String reference) throws SQLException;
}
