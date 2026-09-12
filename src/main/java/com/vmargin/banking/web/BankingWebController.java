package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
public class BankingWebController {

    private static final String USER_SESSION_KEY = "authenticatedUser";
    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final CashInService cashInService;
    private final TransferService transferService;
    private final TransactionHistoryService historyService;

    public BankingWebController(
        LoginService loginService,
        RegistrationService registrationService,
        CashInService cashInService,
        TransferService transferService,
        TransactionHistoryService historyService
    ) {
        this.loginService = loginService;
        this.registrationService = registrationService;
        this.cashInService = cashInService;
        this.transferService = transferService;
        this.historyService = historyService;
    }

    @GetMapping({"/", "/login"})
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam String mobile,
        @RequestParam String pin,
        HttpSession session,
        Model model
    ) {
        try {
            session.setAttribute(USER_SESSION_KEY, loginService.login(mobile, pin));
            return "redirect:/dashboard";
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
        @RequestParam String fullName,
        @RequestParam String mobile,
        @RequestParam String pin,
        Model model
    ) {
        try {
            registrationService.register(fullName, mobile, pin);
            model.addAttribute("success", "Account created. You can sign in now.");
            return "login";
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", exception.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        Object actionError = session.getAttribute("actionError");
        if (actionError instanceof String message) {
            model.addAttribute("error", message);
            session.removeAttribute("actionError");
        }
        try {
            model.addAttribute("transactions", historyService.getHistory(user));
        } catch (SQLException exception) {
            model.addAttribute("error", "Transaction history is temporarily unavailable.");
        }
        return "dashboard";
    }

    @PostMapping("/cash-in")
    public String cashIn(
        @RequestParam BigDecimal amount,
        @RequestParam String details,
        HttpSession session
    ) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            cashInService.cashIn(user, amount, details);
            user.getBankAccount().deposit(amount);
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute("actionError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/transfer")
    public String transfer(
        @RequestParam String recipient,
        @RequestParam BigDecimal amount,
        HttpSession session
    ) {
        User user = currentUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            transferService.transfer(user, recipient, amount);
            user.getBankAccount().withdraw(amount);
        } catch (SQLException | RuntimeException exception) {
            session.setAttribute("actionError", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private User currentUser(HttpSession session) {
        Object user = session.getAttribute(USER_SESSION_KEY);
        return user instanceof User authenticatedUser ? authenticatedUser : null;
    }
}
