package com.vmargin.banking.repository;

import com.vmargin.banking.model.BankAccount;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    List<BankAccount> findByOwner(long ownerId) throws SQLException;

    Optional<BankAccount> findByIdAndOwner(long ownerId, long accountId) throws SQLException;

    Optional<BankAccount> findByAccountNumber(String accountNumber) throws SQLException;

    long findPrimaryId(long ownerId) throws SQLException;

    BankAccount create(long ownerId, String holderName, String accountName, String accountType,
                       String currencyCode, BigDecimal openingBalance) throws SQLException;

    BankAccount openDemoAccountIfMissing(long ownerId, String holderName, String accountName,
                                         String accountType, String currencyCode,
                                         BigDecimal openingBalance) throws SQLException;
}
