package com.vmargin.banking.web;

import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.TransactionHistoryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BankingWebController.class)
class BankingWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginService loginService;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private CashInService cashInService;

    @MockBean
    private TransferService transferService;

    @MockBean
    private TransactionHistoryService historyService;

    @Test
    void loginPageIsRendered() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }

    @Test
    void loginPageConstrainsMobileAndPinInput() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "pattern=\"09[0-9]{9}\""
            )))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "pattern=\"[0-9]{4}\""
            )));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedDashboardRendersTheEmptyRecordState() throws Exception {
        MockHttpSession session = authenticatedSession();
        when(historyService.getHistory((User) session.getAttribute("authenticatedUser")))
            .thenReturn(List.of());

        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"));
    }

    @Test
    void populatedDashboardRendersADataTableForTransactionRecords() throws Exception {
        MockHttpSession session = authenticatedSession();
        when(historyService.getHistory((User) session.getAttribute("authenticatedUser")))
            .thenReturn(List.of(new Transaction(
                1L,
                1L,
                TransactionType.CASH_IN,
                new BigDecimal("250.00"),
                "Cash-in test",
                LocalDateTime.of(2026, 9, 14, 13, 0)
            )));

        mockMvc.perform(get("/dashboard").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<table>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "<th scope=\"col\">Date</th>"
            )));
    }

    @Test
    void invalidLoginReturnsToLoginPage() throws Exception {
        when(loginService.login(anyString(), anyString()))
            .thenThrow(new IllegalArgumentException("Invalid credentials"));

        mockMvc.perform(post("/login")
                .param("mobile", "09990000000")
                .param("pin", "0000"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }

    @Test
    void validLoginRedirectsToDashboard() throws Exception {
        when(loginService.login(anyString(), anyString())).thenReturn(mock(User.class));

        mockMvc.perform(post("/login")
                .param("mobile", "09990000000")
                .param("pin", "1234"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void transferReviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/transfer/review"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void validTransferRequestOpensReviewWithRemainingBalance() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/transfer/review")
                .session(session)
                .param("recipient", "09990000002")
                .param("amount", "750.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/transfer/review"));

        mockMvc.perform(get("/transfer/review").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("transfer-review"))
            .andExpect(model().attribute("recipient", "09990000002"))
            .andExpect(model().attribute("amount", new BigDecimal("750.00")))
            .andExpect(model().attribute("remainingBalance", new BigDecimal("11730.50")));
    }

    @Test
    void transferConfirmationUsesTheReviewedRequestAndClearsIt() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/transfer/review")
                .session(session)
                .param("recipient", "09990000002")
                .param("amount", "750.00"));

        mockMvc.perform(post("/transfer/confirm").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));

        verify(transferService).transfer(
            (User) session.getAttribute("authenticatedUser"),
            "09990000002",
            new BigDecimal("750.00")
        );
        assertNull(session.getAttribute("pendingTransfer"));
    }

    @Test
    void selfTransferReturnsToDashboardWithoutOpeningReview() throws Exception {
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(post("/transfer/review")
                .session(session)
                .param("recipient", "09990000001")
                .param("amount", "750.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    private MockHttpSession authenticatedSession() {
        User user = new User(
            1L,
            "09990000001",
            "1234",
            "JCash Test User",
            new BankAccount("ACC-001", "JCash Test User", new BigDecimal("12480.50"))
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", user);
        return session;
    }
}
