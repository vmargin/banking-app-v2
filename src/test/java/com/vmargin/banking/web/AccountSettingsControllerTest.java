package com.vmargin.banking.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.exception.InvalidCredentialsException;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountSettingsController.class)
@Import(SecurityConfiguration.class)
class AccountSettingsControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private LoginService loginService;
    @MockBean
    private SessionAccounts accounts;
    private User current;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws SQLException {
        current = new User(1, "09990000001", "REDACTED", "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("1000.00")));
        session = new MockHttpSession();
        when(accounts.current(any())).thenReturn(current);
    }

    @Test
    void settingsShowsOnlyAccountDetailsAndAChangePinForm() throws Exception {
        mvc.perform(get("/settings").session(session).with(user("1")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("09990000001")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ACC-1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"currentPin\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("REDACTED"))));
    }

    @Test
    void accountLookupFailureDoesNotRenderStaleSettingsData() throws Exception {
        when(accounts.current(any())).thenThrow(new SQLException("private-account-diagnostic"));
        session.setAttribute("user", current);

        mvc.perform(get("/settings").session(session).with(user("1")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(view().name("service-unavailable"))
            .andExpect(model().attributeDoesNotExist("user"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("No account or transaction details")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-account-diagnostic"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getMobileNumber()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getFullName()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getBankAccount().getAccountId()))));

        assertFalse(session.isInvalid());
        verifyNoInteractions(loginService);
    }

    @Test
    void pinChangeAccountLookupFailureDoesNotAttemptAnUpdateOrExposeSubmittedPins() throws Exception {
        when(accounts.current(any())).thenThrow(new SQLException("private-account-diagnostic"));
        session.setAttribute("user", current);

        mvc.perform(post("/settings/pin").session(session).with(user("1")).with(csrf())
                .param("currentPin", "1234").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(view().name("service-unavailable"))
            .andExpect(model().attributeDoesNotExist("user", "success", "currentPin", "newPin", "confirmPin"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Request temporarily unavailable")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-account-diagnostic"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getMobileNumber()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getFullName()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString(current.getBankAccount().getAccountId()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1234"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("5678"))));

        assertFalse(session.isInvalid());
        verifyNoInteractions(loginService);
    }

    @Test
    void missingCsrfTokenPreventsPinUpdate() throws Exception {
        mvc.perform(post("/settings/pin").session(session).with(user("1"))
                .param("currentPin", "1234").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().isForbidden());

        verify(loginService, never()).changePin(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void wrongCurrentPinRendersSafeErrorAndKeepsSession() throws Exception {
        doThrow(new InvalidCredentialsException("Credential diagnostic"))
            .when(loginService).changePin(1, "9999", "5678", "5678");

        mvc.perform(post("/settings/pin").session(session).with(user("1")).with(csrf())
                .param("currentPin", "9999").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Current PIN could not be verified")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("9999"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Credential diagnostic"))));

        assertTrue(!session.isInvalid());
    }

    @Test
    void credentialWriteConflictDoesNotExposeDatabaseDetailsOrReportSuccess() throws Exception {
        doThrow(new SQLException("private-cas-diagnostic"))
            .when(loginService).changePin(1, "1234", "5678", "5678");

        mvc.perform(post("/settings/pin").session(session).with(user("1")).with(csrf())
                .param("currentPin", "1234").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().isOk())
            .andExpect(view().name("settings"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "PIN could not be changed. Sign in again and retry.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private-cas-diagnostic"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("PIN changed"))));

        assertTrue(!session.isInvalid());
    }

    @Test
    void successfulPinChangeInvalidatesSessionAndEveryPendingReview() throws Exception {
        session.setAttribute(BillPaymentController.PENDING_ATTRIBUTE, "pending review");

        mvc.perform(post("/settings/pin").session(session).with(user("1")).with(csrf())
                .param("currentPin", "1234").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?pinChanged=true"));

        verify(loginService).changePin(1, "1234", "5678", "5678");
        assertTrue(session.isInvalid());
    }
}
