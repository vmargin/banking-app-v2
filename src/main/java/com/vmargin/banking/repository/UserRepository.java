package com.vmargin.banking.repository;

import com.vmargin.banking.model.User;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user) throws SQLException;

    Optional<User> findByMobileNumber(String mobileNumber) throws SQLException;

    default Optional<User> findById(long id) throws SQLException {
        throw new UnsupportedOperationException("Account lookup is not supported");
    }

    default void updatePin(long id, String expectedPin, String newHash) throws SQLException {
        throw new UnsupportedOperationException("Credential update is not supported");
    }

    default List<User> findAll() throws SQLException {
        throw new UnsupportedOperationException("Listing users is not supported");
    }
}
