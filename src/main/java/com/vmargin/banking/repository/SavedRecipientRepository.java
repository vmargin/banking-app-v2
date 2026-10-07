package com.vmargin.banking.repository;

import com.vmargin.banking.model.SavedRecipient;
import java.sql.SQLException;
import java.util.List;

public interface SavedRecipientRepository {
    List<SavedRecipient> findByOwner(long ownerId) throws SQLException;
    void save(long ownerId, String ownerMobile, String recipientMobile, String label) throws SQLException;
    boolean delete(long ownerId, long recipientId) throws SQLException;
}
