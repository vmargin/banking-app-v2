package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.MoneyRequest;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.MoneyRequestService;
import com.vmargin.banking.service.SavedRecipientService;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owner-scoped local requests and their reviewed payments. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MoneyRequestController {
    private static final String NOTICE = "moneyRequestNotice";
    private static final String ERROR = "moneyRequestError";
    static final String PENDING_PAYMENT = "pendingMoneyRequestPayment";
    private static final int REVIEW_MINUTES = 5;

    private final SessionAccounts sessions;
    private final AccountService accounts;
    private final SavedRecipientService recipients;
    private final MoneyRequestService requests;

    public MoneyRequestController(SessionAccounts sessions, AccountService accounts,
                                  SavedRecipientService recipients, MoneyRequestService requests) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.recipients = recipients;
        this.requests = requests;
    }

    @GetMapping("/request")
    public String page(HttpSession session, Model model) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", owner);
        try {
            List<BankAccount> bankAccounts = accounts.list(owner);
            model.addAttribute("bankAccounts", bankAccounts);
            model.addAttribute("eligibleAccounts", bankAccounts.stream()
                .filter(account -> "ACTIVE".equals(account.getStatus())
                    && "PHP".equals(account.getCurrencyCode())).toList());
            model.addAttribute("accountsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("bankAccounts", List.of());
            model.addAttribute("eligibleAccounts", List.of());
            model.addAttribute("accountsAvailable", false);
        }
        try {
            model.addAttribute("savedRecipients", recipients.list(owner));
            model.addAttribute("savedRecipientsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("savedRecipients", List.of());
            model.addAttribute("savedRecipientsAvailable", false);
        }
        try {
            model.addAttribute("incomingRequests", requests.incoming(owner));
            model.addAttribute("outgoingRequests", requests.outgoing(owner));
            model.addAttribute("requestsAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("incomingRequests", List.of());
            model.addAttribute("outgoingRequests", List.of());
            model.addAttribute("requestsAvailable", false);
        }
        flash(session, model);
        return "request-money";
    }

    @PostMapping("/money-requests")
    public String create(@RequestParam long requesterAccountId, @RequestParam String payerAccountNumber,
                         @RequestParam String amount, @RequestParam(defaultValue = "") String note,
                         HttpSession session) throws SQLException {
        User owner = sessions.current(session);
        if (owner == null) {
            return "redirect:/login";
        }
        try {
            MoneyRequest request = requests.create(owner, requesterAccountId, payerAccountNumber, amount, note);
            session.setAttribute(NOTICE, "Request saved for " + request.payerName() + ". No money moved.");
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "That request could not be saved. Check the account and try again.");
        }
        return "redirect:/request";
    }

    @PostMapping("/money-requests/{requestId}/pay/review")
    public String preparePayment(@PathVariable long requestId, @RequestParam long sourceAccountId,
                                 HttpSession session) throws SQLException {
        User payer = sessions.current(session);
        if (payer == null) {
            return "redirect:/login";
        }
        try {
            MoneyRequest request = requests.pendingIncoming(payer, requestId).orElseThrow(
                () -> new IllegalArgumentException("This incoming request is no longer available."));
            BankAccount source = accounts.get(payer, sourceAccountId).filter(account ->
                "ACTIVE".equals(account.getStatus()) && request.currencyCode().equals(account.getCurrencyCode()))
                .orElseThrow(() -> new IllegalArgumentException("Choose an active account in the request currency."));
            LocalDateTime now = LocalDateTime.now();
            session.setAttribute(PENDING_PAYMENT, new PendingMoneyRequestPayment(request.id(), source.getId(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), now.plusMinutes(REVIEW_MINUTES)));
            return "redirect:/money-requests/pay/review";
        } catch (IllegalArgumentException exception) {
            session.setAttribute(ERROR, exception.getMessage());
            return "redirect:/request";
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "The request could not be prepared. Try again.");
            return "redirect:/request";
        }
    }

    @GetMapping("/money-requests/pay/review")
    public String reviewPayment(HttpSession session, Model model) throws SQLException {
        User payer = sessions.current(session);
        if (payer == null) {
            return "redirect:/login";
        }
        PendingMoneyRequestPayment pending = pendingPayment(session);
        if (pending == null || !pending.expiresAt().isAfter(LocalDateTime.now())) {
            session.removeAttribute(PENDING_PAYMENT);
            session.setAttribute(ERROR, "This review expired. Start again from the incoming request.");
            return "redirect:/request";
        }
        MoneyRequest request = requests.pendingIncoming(payer, pending.requestId()).orElse(null);
        BankAccount source = accounts.get(payer, pending.sourceAccountId()).orElse(null);
        if (request == null || source == null || !"ACTIVE".equals(source.getStatus())
            || !request.currencyCode().equals(source.getCurrencyCode())) {
            session.removeAttribute(PENDING_PAYMENT);
            session.setAttribute(ERROR, "This request or source account changed. Review it again before paying.");
            return "redirect:/request";
        }
        model.addAttribute("user", payer);
        model.addAttribute("request", request);
        model.addAttribute("sourceAccount", source);
        model.addAttribute("pendingPayment", pending);
        return "money-request-review";
    }

    @PostMapping("/money-requests/pay/confirm")
    public String confirmPayment(@RequestParam String token, HttpSession session) throws SQLException {
        User payer = sessions.current(session);
        if (payer == null) {
            return "redirect:/login";
        }
        PendingMoneyRequestPayment pending = pendingPayment(session);
        if (pending == null || !pending.token().equals(token)
            || !pending.expiresAt().isAfter(LocalDateTime.now())) {
            session.removeAttribute(PENDING_PAYMENT);
            session.setAttribute(ERROR, "This payment review expired or was already used. Start again.");
            return "redirect:/request";
        }
        try {
            requests.pay(payer, pending.requestId(), pending.sourceAccountId(), pending.paymentReference());
            session.removeAttribute(PENDING_PAYMENT);
            session.setAttribute(NOTICE, "Request paid in the local ledger. No bank or payment network was contacted.");
            return "redirect:/request";
        } catch (IllegalArgumentException exception) {
            session.removeAttribute(PENDING_PAYMENT);
            session.setAttribute(ERROR, exception.getMessage());
        } catch (SQLException exception) {
            session.setAttribute(ERROR, "Payment could not be confirmed. Check activity before retrying.");
        }
        return "redirect:/request";
    }

    @PostMapping("/money-requests/pay/cancel")
    public String cancelPayment(@RequestParam String token, HttpSession session) throws SQLException {
        if (sessions.current(session) == null) {
            return "redirect:/login";
        }
        PendingMoneyRequestPayment pending = pendingPayment(session);
        if (pending != null && pending.token().equals(token)) {
            session.removeAttribute(PENDING_PAYMENT);
        }
        session.setAttribute(NOTICE, "Payment review canceled. No money moved.");
        return "redirect:/request";
    }

    @PostMapping("/money-requests/{requestId}/decline")
    public String decline(@PathVariable long requestId, HttpSession session) throws SQLException {
        User payer = sessions.current(session);
        if (payer == null) {
            return "redirect:/login";
        }
        boolean declined = requests.decline(payer, requestId);
        session.setAttribute(declined ? NOTICE : ERROR,
            declined ? "Request declined in the local simulator." : "Request is no longer pending.");
        return "redirect:/request";
    }

    @PostMapping("/money-requests/{requestId}/cancel")
    public String cancel(@PathVariable long requestId, HttpSession session) throws SQLException {
        User requester = sessions.current(session);
        if (requester == null) {
            return "redirect:/login";
        }
        boolean canceled = requests.cancel(requester, requestId);
        session.setAttribute(canceled ? NOTICE : ERROR,
            canceled ? "Request canceled. No money moved." : "Request is no longer pending.");
        return "redirect:/request";
    }

    private PendingMoneyRequestPayment pendingPayment(HttpSession session) {
        Object value = session.getAttribute(PENDING_PAYMENT);
        return value instanceof PendingMoneyRequestPayment pending ? pending : null;
    }

    private void flash(HttpSession session, Model model) {
        Object notice = session.getAttribute(NOTICE);
        Object error = session.getAttribute(ERROR);
        if (notice instanceof String message) {
            model.addAttribute("success", message);
            session.removeAttribute(NOTICE);
        }
        if (error instanceof String message) {
            model.addAttribute("error", message);
            session.removeAttribute(ERROR);
        }
    }
}
