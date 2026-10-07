package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankCard;
import com.vmargin.banking.model.CardReplacementReason;
import com.vmargin.banking.model.CardReplacementRequest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface CardRepository {
    List<BankCard> findByOwner(long ownerId) throws SQLException;

    boolean setFrozen(long ownerId, long cardId, boolean frozen) throws SQLException;

    default boolean setOnlineEnabled(long ownerId, long cardId, boolean enabled) throws SQLException {
        return false;
    }

    default boolean setDailyLimit(long ownerId, long cardId, BigDecimal dailyLimit) throws SQLException {
        return false;
    }

    BankCard createDemoIfMissing(long ownerId, long accountId, String accountName,
                                 String cardName, String brand, String lastFour,
                                 BigDecimal dailyLimit) throws SQLException;

    List<CardReplacementRequest> findReplacementRequests(long ownerId) throws SQLException;

    CardReplacementRequest requestReplacement(long ownerId, long cardId, CardReplacementReason reason,
                                              String reference, LocalDateTime createdAt) throws SQLException;

    boolean cancelReplacementRequest(long ownerId, long cardId, LocalDateTime updatedAt) throws SQLException;
}
