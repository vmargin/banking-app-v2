package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.repository.TransactionRepository;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminServiceTest {

    @Test
    void adminCanViewAllUsersAndTransactions() throws Exception {
        AdminService service = new AdminService(new AdminUserRepository(), new AdminTransactionRepository());

        assertEquals(1, service.getAllUsers(admin()).size());
        assertEquals(1, service.getAllTransactions(admin()).size());
        assertEquals(1, service.getTransactionsForUser(admin(), 1L).size());
    }

    @Test
    void regularUserCannotAccessAdminData() {
        AdminService service = new AdminService(new AdminUserRepository(), new AdminTransactionRepository());

        assertThrows(AccessDeniedException.class, () -> service.getAllUsers(regularUser()));
    }

    private User admin() {
        return user(UserRole.ADMIN);
    }

    private User regularUser() {
        return user(UserRole.USER);
    }

    private User user(UserRole role) {
        return new User(
            1L,
            "09990000000",
            "1234",
            "Admin User",
            new BankAccount("ACC-1", "Admin User", BigDecimal.ZERO),
            role
        );
    }

    private static class AdminUserRepository implements UserRepository {

        @Override
        public User save(User user) {
            return user;
        }

        @Override
        public Optional<User> findByMobileNumber(String mobileNumber) {
            return Optional.empty();
        }

        @Override
        public List<User> findAll() {
            return List.of(new User(
                1L,
                "09990000001",
                "1234",
                "Test User",
                new BankAccount("ACC-1", "Test User", BigDecimal.ZERO),
                UserRole.USER
            ));
        }
    }

    private static class AdminTransactionRepository implements TransactionRepository {

        @Override
        public Transaction save(Transaction transaction) {
            return transaction;
        }

        @Override
        public List<Transaction> findByUserId(long userId) {
            return transactions();
        }

        @Override
        public List<Transaction> findAll() {
            return transactions();
        }

        private List<Transaction> transactions() {
            return List.of(new Transaction(
                1L,
                1L,
                TransactionType.CASH_IN,
                new BigDecimal("100.00"),
                "Test transaction",
                LocalDateTime.of(2026, 9, 9, 10, 0)
            ));
        }
    }
}
