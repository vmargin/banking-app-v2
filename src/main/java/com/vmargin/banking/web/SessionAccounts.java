package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.repository.UserRepository;
import com.vmargin.banking.util.PinHasher;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.sql.SQLException;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class SessionAccounts {
    public static final String USER_ID = "authenticatedUserId";
    static final String CREDENTIAL_FINGERPRINT = "authenticatedCredentialFingerprint";
    private final UserRepository users;

    public SessionAccounts(UserRepository users) {
        this.users = users;
    }

    public User current(HttpSession session) throws SQLException {
        Object id = session.getAttribute(USER_ID);
        if (id == null) {
            return null;
        }
        if (!(id instanceof Long userId)
            || !(session.getAttribute(CREDENTIAL_FINGERPRINT) instanceof String expectedFingerprint)) {
            invalidate(session);
            return null;
        }
        User user = users.findById(userId).orElse(null);
        if (user == null) {
            invalidate(session);
            return null;
        }
        String pinHash = user.getPinForPersistence();
        if (!PinHasher.isHash(pinHash)
            || !MessageDigest.isEqual(expectedFingerprint.getBytes(StandardCharsets.US_ASCII),
                fingerprint(pinHash).getBytes(StandardCharsets.US_ASCII))) {
            invalidate(session);
            return null;
        }
        return view(user);
    }

    public static void bind(HttpSession session, User authenticatedUser) {
        String pinHash = authenticatedUser.getPinForPersistence();
        if (!PinHasher.isHash(pinHash)) {
            throw new IllegalArgumentException("Authenticated credentials must be stored as a PIN hash");
        }
        session.setAttribute(USER_ID, authenticatedUser.getId());
        session.setAttribute(CREDENTIAL_FINGERPRINT, fingerprint(pinHash));
    }

    public User recipient(String mobile) throws SQLException {
        return users.findByMobileNumber(mobile).map(SessionAccounts::view).orElseThrow(
            () -> new IllegalArgumentException("Recipient account was not found."));
    }

    private static User view(User user) {
        BankAccount account = user.getBankAccount();
        return new User(user.getId(), user.getMobileNumber(), "REDACTED", user.getFullName(),
            new BankAccount(account.getId(), user.getId(), account.getAccountNumber(), user.getFullName(),
                account.getAccountName(), account.getAccountType(), account.getCurrencyCode(), account.getStatus(),
                account.isPrimary(), account.getBalance()),
            user.getRole());
    }

    private static void invalidate(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
    }

    private static String fingerprint(String pinHash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(pinHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
