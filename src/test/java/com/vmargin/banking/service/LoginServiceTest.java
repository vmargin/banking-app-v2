package com.vmargin.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.exception.AccountLockedException;
import com.vmargin.banking.service.exception.InvalidCredentialsException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    private static final String MOBILE_NUMBER = "09171234567";
    private static final String PIN = "1234";

    private LoginService loginService;
    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = new User(
            1L,
            MOBILE_NUMBER,
            PIN,
            "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1500.00"))
        );
        loginService = new LoginService(new TestUserRepository(savedUser));
    }

    @Test
    void loginReturnsUserForCorrectCredentials() throws Exception {
        User authenticatedUser = loginService.login(MOBILE_NUMBER, PIN);

        assertSame(savedUser, authenticatedUser);
        assertEquals(0, loginService.getFailedAttempts());
        assertFalse(loginService.isLocked());
    }

    @Test
    void loginRejectsBlankCredentialsWithoutCountingAnAttempt() {
        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login("", "")
        );

        assertEquals(0, loginService.getFailedAttempts());
    }

    @Test
    void loginRejectsAnUnknownMobileNumberAndCountsAnAttempt() {
        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login("09999999999", PIN)
        );

        assertEquals(1, loginService.getFailedAttempts());
        assertEquals(2, loginService.getRemainingAttempts());
    }

    @Test
    void loginLocksAfterThreeIncorrectPins() {
        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(MOBILE_NUMBER, "0000")
        );
        assertThrows(
            InvalidCredentialsException.class,
            () -> loginService.login(MOBILE_NUMBER, "0000")
        );
        assertThrows(
            AccountLockedException.class,
            () -> loginService.login(MOBILE_NUMBER, "0000")
        );

        assertTrue(loginService.isLocked());
        assertThrows(
            AccountLockedException.class,
            () -> loginService.login(MOBILE_NUMBER, PIN)
        );
    }

    private static class TestUserRepository implements UserRepository {

        private final User user;

        TestUserRepository(User user) {
            this.user = user;
        }

        @Override
        public User save(User savedUser) {
            return savedUser;
        }

        @Override
        public Optional<User> findByMobileNumber(String mobileNumber) {
            if (user.getMobileNumber().equals(mobileNumber)) {
                return Optional.of(user);
            }
            return Optional.empty();
        }
    }
}
