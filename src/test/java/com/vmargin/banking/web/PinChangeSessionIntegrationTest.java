package com.vmargin.banking.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.BillPaymentService;
import com.vmargin.banking.service.BillPaymentService.PreparedPayment;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import com.vmargin.testsupport.PinChangeTestConfiguration;

@WebMvcTest(controllers = {AccountSettingsController.class, BillPaymentController.class})
@Import({SecurityConfiguration.class, PinChangeTestConfiguration.class})
class PinChangeSessionIntegrationTest {
    private static final long USER_ID = 1L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PinChangeTestConfiguration.IdentityRepository users;
    @Autowired
    private SessionAccounts accounts;
    @MockBean
    private BillPaymentService billPayments;

    @Test
    void staleSessionCannotConfirmItsPendingBillAfterAnotherSessionChangesPin() throws Exception {
        User authenticated = users.findById(USER_ID).orElseThrow();
        MockHttpSession changingSession = new MockHttpSession();
        MockHttpSession staleSession = new MockHttpSession();
        SessionAccounts.bind(changingSession, authenticated);
        SessionAccounts.bind(staleSession, authenticated);
        String pendingToken = "review-token-from-stale-session";
        staleSession.setAttribute(BillPaymentController.PENDING_ATTRIBUTE,
            new BillPaymentController.PendingBillPayment(pendingToken, USER_ID,
                authenticated.getBankAccount().getId(), authenticated.getBankAccount().getAccountName(),
                new PreparedPayment(DemoBiller.WATER, "1234", new BigDecimal("20.00")),
                Instant.now().plusSeconds(300)));

        mvc.perform(post("/settings/pin").session(changingSession).with(user("1")).with(csrf())
                .param("currentPin", "1234").param("newPin", "5678").param("confirmPin", "5678"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?pinChanged=true"));
        assertTrue(changingSession.isInvalid());

        mvc.perform(post("/bills/confirm").session(staleSession).with(user("1")).with(csrf())
                .param("token", pendingToken))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        assertTrue(staleSession.isInvalid());
        verify(billPayments, never()).pay(any(User.class), any(PreparedPayment.class), anyString());
    }

}
