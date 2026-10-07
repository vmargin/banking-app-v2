package com.vmargin.banking.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.util.PinHasher;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SessionAccountsTest {
    private static final long USER_ID = 1L;
    private static final String MOBILE = "09171234567";
    private static final String PIN = "1234";

    @Test
    void pinChangeInvalidatesEverySessionBoundToThePreviousHash() throws Exception {
        MutableUserRepository users = new MutableUserRepository(hashedUser(PIN));
        SessionAccounts accounts = new SessionAccounts(users);
        String oldHash = users.user.getPinForPersistence();
        MockHttpSession first = boundSession(users.user);
        MockHttpSession second = boundSession(users.user);

        assertEquals("REDACTED", accounts.current(first).getPinForPersistence());
        assertNotEquals(oldHash, first.getAttribute(SessionAccounts.CREDENTIAL_FINGERPRINT));
        users.updatePin(USER_ID, oldHash, PinHasher.hash("5678"));

        assertNull(accounts.current(first));
        assertNull(accounts.current(second));
        assertTrue(first.isInvalid());
        assertTrue(second.isInvalid());
    }

    @Test
    void missingCredentialBindingAndDeletedAccountsFailClosed() throws Exception {
        MutableUserRepository users = new MutableUserRepository(hashedUser(PIN));
        SessionAccounts accounts = new SessionAccounts(users);
        MockHttpSession missingBinding = new MockHttpSession();
        missingBinding.setAttribute(SessionAccounts.USER_ID, USER_ID);

        assertNull(accounts.current(missingBinding));
        assertTrue(missingBinding.isInvalid());

        MockHttpSession deletedAccount = boundSession(users.user);
        users.user = null;
        assertNull(accounts.current(deletedAccount));
        assertTrue(deletedAccount.isInvalid());
    }

    @Test
    void legacyLoginBindsTheHashThatWasActuallyWritten() throws Exception {
        MutableUserRepository users = new MutableUserRepository(plainUser(PIN));
        SessionAccounts accounts = new SessionAccounts(users);
        User authenticated = new LoginService(users).login(MOBILE, PIN);
        MockHttpSession session = boundSession(authenticated);

        assertTrue(PinHasher.isHash(authenticated.getPinForPersistence()));
        assertEquals(authenticated.getPinForPersistence(), users.user.getPinForPersistence());
        assertEquals(USER_ID, accounts.current(session).getId());
    }

    @Test
    void bindingRejectsUnhashedAuthenticationResults() {
        assertThrows(IllegalArgumentException.class, () -> SessionAccounts.bind(new MockHttpSession(), plainUser(PIN)));
    }

    private MockHttpSession boundSession(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionAccounts.bind(session, user);
        return session;
    }

    private static User hashedUser(String pin) {
        return new User(USER_ID, MOBILE, PinHasher.hash(pin), "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1500.00")));
    }

    private static User plainUser(String pin) {
        return new User(USER_ID, MOBILE, pin, "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1500.00")));
    }

    private static final class MutableUserRepository implements UserRepository {
        private User user;

        private MutableUserRepository(User user) {
            this.user = user;
        }

        @Override
        public User save(User savedUser) {
            user = savedUser;
            return user;
        }

        @Override
        public Optional<User> findByMobileNumber(String mobileNumber) {
            return user != null && user.getMobileNumber().equals(mobileNumber) ? Optional.of(user) : Optional.empty();
        }

        @Override
        public Optional<User> findById(long id) {
            return user != null && user.getId() == id ? Optional.of(user) : Optional.empty();
        }

        @Override
        public void updatePin(long id, String expectedPin, String newHash) throws SQLException {
            if (user == null || user.getId() != id || !user.getPinForPersistence().equals(expectedPin)) {
                throw new SQLException("Credential changed; retry authentication");
            }
            if (!PinHasher.isHash(newHash)) {
                throw new IllegalArgumentException("Replacement PIN must be a BCrypt hash");
            }
            user = new User(user.getId(), user.getMobileNumber(), newHash, user.getFullName(),
                user.getBankAccount(), user.getRole());
        }
    }
}
