package com.vmargin.banking.web;

import com.vmargin.banking.model.BankAccount;
import com.vmargin.banking.model.DemoBiller;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.BillPaymentService;
import com.vmargin.banking.service.BillPaymentService.PreparedPayment;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(BillPaymentController.class)
@Import(SecurityConfiguration.class)
class BillPaymentControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private BillPaymentService billPayments;
    @MockBean private SessionAccounts accounts;
    private User owner;
    private MockHttpSession session;

    @BeforeEach
    void setup() throws Exception {
        owner = new User(1, "09990000001", "REDACTED", "Demo User",
            new BankAccount("ACC-1", "Demo User", new BigDecimal("100.00")));
        session = new MockHttpSession();
        when(accounts.current(any())).thenReturn(owner);
        when(billPayments.prepare("WATER", "123456789012", "20.00"))
            .thenReturn(new PreparedPayment(DemoBiller.WATER, "9012", new BigDecimal("20.00")));
    }

    @Test
    void reviewKeepsOnlyMaskedReferenceAndConfirmConsumesOneUseToken() throws Exception {
        mvc.perform(post("/bills/review").session(session).with(user("1")).with(csrf())
                .param("biller", "WATER").param("billReference", "123456789012").param("amount", "20.00"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/bills/review"));
        Object pending = session.getAttribute(BillPaymentController.PENDING_ATTRIBUTE);
        assertNotNull(pending);
        assertFalse(pending.toString().contains("123456789012"));
        mvc.perform(get("/bills/review").session(session).with(user("1")))
            .andExpect(status().isOk()).andExpect(view().name("bill-payment-review"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("9012")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("123456789012"))));

        String token = ((BillPaymentController.PendingBillPayment) pending).token();
        mvc.perform(post("/bills/confirm").session(session).with(user("1")).with(csrf()).param("token", token))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/transactions/receipt/" + token));
        verify(billPayments).pay(eq(owner), eq(owner.getBankAccount().getId()), any(PreparedPayment.class), eq(token));
        mvc.perform(post("/bills/confirm").session(session).with(user("1")).with(csrf()).param("token", token))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/payments"));
        verify(billPayments, org.mockito.Mockito.times(1)).pay(eq(owner), eq(owner.getBankAccount().getId()),
            any(PreparedPayment.class), eq(token));
    }

    @Test
    void expiredOrForeignOwnerReviewCannotConfirmAndMutationRequiresCsrf() throws Exception {
        mvc.perform(post("/bills/review").session(session).with(user("1"))
                .param("biller", "WATER").param("billReference", "123456789012").param("amount", "20.00"))
            .andExpect(status().isForbidden());
        var payment = new PreparedPayment(DemoBiller.WATER, "9012", new BigDecimal("20.00"));
        session.setAttribute(BillPaymentController.PENDING_ATTRIBUTE,
            new BillPaymentController.PendingBillPayment("expired-token", 1, owner.getBankAccount().getId(),
                owner.getBankAccount().getAccountName(), payment,
                java.time.Instant.now().minusSeconds(1)));
        mvc.perform(post("/bills/confirm").session(session).with(user("1")).with(csrf())
                .param("token", "expired-token"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/payments"));
        session.setAttribute(BillPaymentController.PENDING_ATTRIBUTE,
            new BillPaymentController.PendingBillPayment("foreign-token", 2, owner.getBankAccount().getId(),
                owner.getBankAccount().getAccountName(), payment,
                java.time.Instant.now().plusSeconds(60)));
        mvc.perform(post("/bills/confirm").session(session).with(user("1")).with(csrf())
                .param("token", "foreign-token"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/payments"));
        verify(billPayments, never()).pay(any(), anyLong(), any(), any());
    }

    @Test
    void cancelReturnsToOpenBillActionWithoutWriting() throws Exception {
        mvc.perform(post("/bills/review").session(session).with(user("1")).with(csrf())
                .param("biller", "WATER").param("billReference", "123456789012").param("amount", "20.00"))
            .andExpect(redirectedUrl("/bills/review"));
        String token = ((BillPaymentController.PendingBillPayment)
            session.getAttribute(BillPaymentController.PENDING_ATTRIBUTE)).token();

        mvc.perform(post("/bills/cancel").session(session).with(user("1")).with(csrf()).param("token", token))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/payments"));

        verify(billPayments, never()).pay(any(), anyLong(), any(), any());
        org.junit.jupiter.api.Assertions.assertNull(session.getAttribute(BillPaymentController.PENDING_ATTRIBUTE));
    }
}
