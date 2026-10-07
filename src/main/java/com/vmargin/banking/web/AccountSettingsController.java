package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.exception.AccountLockedException;
import com.vmargin.banking.service.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountSettingsController {
    private final LoginService loginService;
    private final SessionAccounts accounts;

    public AccountSettingsController(LoginService loginService, SessionAccounts accounts) {
        this.loginService = loginService;
        this.accounts = accounts;
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "settings";
    }

    @PostMapping("/settings/pin")
    public String changePin(@RequestParam String currentPin, @RequestParam String newPin,
                            @RequestParam String confirmPin, HttpSession session, Model model) throws SQLException {
        User user = accounts.current(session);
        if (user == null) {
            return "redirect:/login";
        }
        try {
            loginService.changePin(user.getId(), currentPin, newPin, confirmPin);
            SecurityContextHolder.clearContext();
            session.invalidate();
            return "redirect:/login?pinChanged=true";
        } catch (IllegalArgumentException exception) {
            return showSettings(user, exception.getMessage(), model);
        } catch (AccountLockedException | InvalidCredentialsException exception) {
            return showSettings(user, "Current PIN could not be verified. Try again or sign in later.", model);
        } catch (SQLException exception) {
            return showSettings(user, "PIN could not be changed. Sign in again and retry.", model);
        }
    }

    private String showSettings(User user, String message, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("error", message);
        return "settings";
    }
}
