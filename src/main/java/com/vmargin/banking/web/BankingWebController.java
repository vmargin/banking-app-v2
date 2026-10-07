package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.exception.RegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Owns sign-in, registration, and the HTTP authentication boundary. */
@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BankingWebController {
    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final HttpSessionSecurityContextRepository contexts;

    public BankingWebController(LoginService loginService, RegistrationService registrationService,
                                HttpSessionSecurityContextRepository contexts) {
        this.loginService = loginService;
        this.registrationService = registrationService;
        this.contexts = contexts;
    }

    @GetMapping({"/", "/login"})
    public String loginPage(@RequestParam(required = false) boolean pinChanged, Model model) {
        if (pinChanged) {
            model.addAttribute("success", "Your PIN changed. Sign in with your new PIN.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String mobile, @RequestParam String pin,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        model.addAttribute("mobile", mobile);
        try {
            User user = loginService.login(mobile, pin);
            HttpSession session = request.getSession();
            request.changeSessionId();
            clearPreviousIdentityState(session);
            SessionAccounts.bind(session, user);
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                Long.toString(user.getId()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
            );
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            session.removeAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN");
            return "redirect:/dashboard";
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", "Unable to sign in. Check your credentials or try again later.");
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName, @RequestParam String mobile,
                           @RequestParam String pin, Model model) {
        model.addAttribute("mobile", mobile);
        try {
            registrationService.register(fullName, mobile, pin);
            model.addAttribute("success", "Account created. You can sign in now.");
            return "login";
        } catch (RegistrationException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (SQLException | RuntimeException exception) {
            model.addAttribute("error", "Account creation is temporarily unavailable.");
        }
        model.addAttribute("fullName", fullName);
        return "register";
    }

    private void clearPreviousIdentityState(HttpSession session) {
        for (String attribute : List.of(
            TransferController.PENDING_ATTRIBUTE,
            SavingsController.PENDING_ATTRIBUTE,
            BillPaymentController.PENDING_ATTRIBUTE,
            MoneyRequestController.PENDING_PAYMENT,
            CashInTokens.SESSION_ATTRIBUTE,
            "cashInError", "cashInAmount", "cashInDetails",
            "transferError", "transferRecipient", "transferAmount",
            "moneyRequestNotice", "moneyRequestError"
        )) {
            session.removeAttribute(attribute);
        }
        SessionFlash.clearActions(session);
    }
}
