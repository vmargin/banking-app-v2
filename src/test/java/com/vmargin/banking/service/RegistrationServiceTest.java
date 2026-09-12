package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.model.UserRole;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.RegistrationException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {

    @Test
    void registrationCreatesAStandardUserAccount() throws Exception {
        RegistrationService service = new RegistrationService(new InMemoryUserRepository());

        User created = service.register("New User", "09123456789", "5678");

        assertEquals("New User", created.getFullName());
        assertEquals(UserRole.USER, created.getRole());
        assertEquals(BigDecimal.ZERO, created.getBalance());
    }

    @Test
    void registrationRejectsAnInvalidMobileNumber() {
        RegistrationService service = new RegistrationService(new InMemoryUserRepository());

        assertThrows(
            RegistrationException.class,
            () -> service.register("New User", "09123", "5678")
        );
    }

    private static class InMemoryUserRepository implements UserRepository {

        @Override
        public User save(User user) {
            return new User(
                7L,
                user.getMobileNumber(),
                user.getPinForPersistence(),
                user.getFullName(),
                new BankAccount("ACC-7", user.getFullName(), user.getBalance()),
                user.getRole()
            );
        }

        @Override
        public Optional<User> findByMobileNumber(String mobileNumber) {
            return Optional.empty();
        }
    }
}
