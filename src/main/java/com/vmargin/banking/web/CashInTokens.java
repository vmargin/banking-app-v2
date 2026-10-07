package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Owns short-lived cash-in form tokens shared by the dashboard and cash-in page. */
@Component
public final class CashInTokens {
    static final String SESSION_ATTRIBUTE = "cashInToken";

    public Token current(HttpSession session, User owner) {
        synchronized (session) {
            Token token = session.getAttribute(SESSION_ATTRIBUTE) instanceof Token current ? current : null;
            if (token == null || token.ownerId() != owner.getId() || !token.expires().isAfter(Instant.now())) {
                token = new Token(UUID.randomUUID().toString(), owner.getId(), Instant.now().plusSeconds(600));
                session.setAttribute(SESSION_ATTRIBUTE, token);
            }
            return token;
        }
    }

    public Token consume(HttpSession session, long ownerId, String suppliedToken) {
        synchronized (session) {
            Token token = session.getAttribute(SESSION_ATTRIBUTE) instanceof Token current ? current : null;
            if (token != null && token.ownerId() == ownerId && token.token().equals(suppliedToken)) {
                session.removeAttribute(SESSION_ATTRIBUTE);
            }
            return token;
        }
    }

    public void clear(HttpSession session) {
        synchronized (session) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    public record Token(String token, long ownerId, Instant expires) {
    }
}
