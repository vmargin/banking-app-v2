package com.vmargin.banking.repository;

import com.vmargin.banking.model.Transaction;

import java.sql.SQLException;
import java.util.List;

public interface TransactionRepository {

    Transaction save(Transaction transaction) throws SQLException;

    List<Transaction> findByUserId(long userId) throws SQLException;

    default List<Transaction> findAll() throws SQLException {
        throw new UnsupportedOperationException("Listing transactions is not supported");
    }
}
