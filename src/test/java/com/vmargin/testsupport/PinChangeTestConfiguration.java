package com.vmargin.testsupport;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.util.PinHasher;
import com.vmargin.banking.web.SessionAccounts;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class PinChangeTestConfiguration {
    private static final long USER_ID = 1L;

    @Bean
    @Primary
    IdentityRepository identityRepository() {
        return new IdentityRepository();
    }

    @Bean
    @Primary
    SessionAccounts pinChangeSessionAccounts(IdentityRepository users) {
        return new SessionAccounts(users);
    }

    @Bean
    @Primary
    LoginService pinChangeLoginService(IdentityRepository users) {
        return new LoginService(users);
    }

    public static class IdentityRepository implements UserRepository {
        private User user = new User(USER_ID, "09990000001", PinHasher.hash("1234"), "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1000.00")));

        @Override
        public User save(User savedUser) {
            user = savedUser;
            return user;
        }

        @Override
        public Optional<User> findByMobileNumber(String mobileNumber) {
            return user.getMobileNumber().equals(mobileNumber) ? Optional.of(user) : Optional.empty();
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
            if (!PinHasher.isHash(newHash)) {
                throw new IllegalArgumentException("Replacement PIN must be a BCrypt hash");
            }
            user = new User(user.getId(), user.getMobileNumber(), newHash, user.getFullName(),
                user.getBankAccount(), user.getRole());
        }
    }
}
