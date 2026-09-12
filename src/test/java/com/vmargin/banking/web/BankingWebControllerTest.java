package com.vmargin.banking.web;

import com.vmargin.banking.model.User;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.TransactionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
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
}
