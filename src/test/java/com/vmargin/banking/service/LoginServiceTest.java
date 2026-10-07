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
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    private static final String MOBILE_NUMBER = "09171234567";
    private static final String PIN = "1234";

    private LoginService loginService;
    private User savedUser;
    private TestUserRepository userRepository;

    @BeforeEach
    void setUp() {
        savedUser = new User(
            1L,
            MOBILE_NUMBER,
            com.vmargin.banking.util.PinHasher.hash(PIN),
            "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1500.00"))
        );
        userRepository = new TestUserRepository(savedUser);
        loginService = new LoginService(userRepository);
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

    @Test
    void changePinRequiresMatchingNewEntriesAndAChangedValue() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> loginService.changePin(1, PIN, "5678", "5679"));
        assertThrows(IllegalArgumentException.class,
            () -> loginService.changePin(1, PIN, PIN, PIN));
        assertThrows(IllegalArgumentException.class,
            () -> loginService.changePin(1, PIN, "56x8", "56x8"));
        assertEquals(savedUser.getPinForPersistence(),
            userRepository.findById(savedUser.getId()).orElseThrow().getPinForPersistence());
    }

    @Test
    void changePinVerifiesCurrentPinStoresHashAndAllowsOnlyTheNewPin() throws Exception {
        loginService.changePin(1, PIN, "5678", "5678");

        String storedPin = userRepository.findById(1).orElseThrow().getPinForPersistence();
        assertTrue(com.vmargin.banking.util.PinHasher.isHash(storedPin));
        assertTrue(com.vmargin.banking.util.PinHasher.matches("5678", storedPin));
        assertFalse(com.vmargin.banking.util.PinHasher.matches(PIN, storedPin));
        assertThrows(InvalidCredentialsException.class, () -> loginService.login(MOBILE_NUMBER, PIN));
        assertEquals(1L, loginService.login(MOBILE_NUMBER, "5678").getId());
    }

    @Test
    void failedCurrentPinChangesShareTheLoginAttemptLimit() {
        assertThrows(InvalidCredentialsException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(InvalidCredentialsException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(AccountLockedException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(AccountLockedException.class,
            () -> loginService.changePin(1, PIN, "5678", "5678"));
        assertEquals(3, loginService.getFailedAttempts());
    }

    @Test
    void malformedCurrentPinIsRejectedBeforeHashVerificationAndCountsAsAnAttempt() {
        assertThrows(InvalidCredentialsException.class,
            () -> loginService.changePin(1, "12345678901234567890", "5678", "5678"));
        assertEquals(1, loginService.getFailedAttempts());
        assertEquals(savedUser.getPinForPersistence(),
            userRepository.findById(savedUser.getId()).orElseThrow().getPinForPersistence());
    }

    @Test
    void loginFailuresAlsoConsumeTheSettingsPinAttemptLimit() {
        assertThrows(InvalidCredentialsException.class, () -> loginService.login(MOBILE_NUMBER, "9999"));
        assertThrows(InvalidCredentialsException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(AccountLockedException.class, () -> loginService.login(MOBILE_NUMBER, "9999"));
        assertThrows(AccountLockedException.class,
            () -> loginService.changePin(1, PIN, "5678", "5678"));
    }

    @Test
    void settingsFailuresAlsoConsumeTheLoginPinAttemptLimit() {
        assertThrows(InvalidCredentialsException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(InvalidCredentialsException.class, () -> loginService.login(MOBILE_NUMBER, "9999"));
        assertThrows(AccountLockedException.class,
            () -> loginService.changePin(1, "9999", "5678", "5678"));
        assertThrows(AccountLockedException.class, () -> loginService.login(MOBILE_NUMBER, PIN));
    }

    private static class TestUserRepository implements UserRepository {

        private User user;

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

        @Override
        public Optional<User> findById(long id) {
            return user.getId() == id ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updatePin(long id, String expectedPin, String newHash) throws SQLException {
            if (user.getId() != id || !user.getPinForPersistence().equals(expectedPin)) {
                throw new SQLException("Credential changed; retry authentication");
            }
            user = new User(user.getId(), user.getMobileNumber(), newHash, user.getFullName(),
                user.getBankAccount(), user.getRole());
        }
    }
}
