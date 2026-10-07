package com.vmargin.banking.web;

import com.vmargin.banking.repository.JdbcCashInRepository;
import com.vmargin.banking.repository.JdbcTransactionRepository;
import com.vmargin.banking.repository.JdbcTransferRepository;
import com.vmargin.banking.repository.JdbcUserRepository;
import com.vmargin.banking.repository.JdbcSavedRecipientRepository;
import com.vmargin.banking.repository.JdbcSavingsGoalRepository;
import com.vmargin.banking.repository.JdbcBillPaymentRepository;
import com.vmargin.banking.repository.JdbcAccountRepository;
import com.vmargin.banking.repository.JdbcCardRepository;
import com.vmargin.banking.repository.JdbcMoneyRequestRepository;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.SavedRecipientService;
import com.vmargin.banking.service.SavingsGoalService;
import com.vmargin.banking.service.BillPaymentService;
import com.vmargin.banking.service.AccountService;
import com.vmargin.banking.service.CardService;
import com.vmargin.banking.service.MoneyRequestService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankingWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingWebApplication.class, args);
    }

    @Bean
    public JdbcUserRepository userRepository() {
        return new JdbcUserRepository();
    }

    @Bean
    public LoginService loginService(JdbcUserRepository repository) {
        return new LoginService(repository);
    }

    @Bean
    public RegistrationService registrationService(JdbcUserRepository repository) {
        return new RegistrationService(repository);
    }

    @Bean
    public CashInService cashInService() {
        return new CashInService(new JdbcCashInRepository());
    }

    @Bean
    public TransferService transferService() {
        return new TransferService(new JdbcTransferRepository());
    }

    @Bean
    public TransactionHistoryService historyService() {
        return new TransactionHistoryService(new JdbcTransactionRepository());
    }

    @Bean
    public SavedRecipientService savedRecipientService() {
        return new SavedRecipientService(new JdbcSavedRecipientRepository());
    }

    @Bean
    public SavingsGoalService savingsGoalService() {
        return new SavingsGoalService(new JdbcSavingsGoalRepository());
    }

    @Bean
    public BillPaymentService billPaymentService() {
        return new BillPaymentService(new JdbcBillPaymentRepository());
    }

    @Bean
    public AccountService accountService() {
        return new AccountService(new JdbcAccountRepository());
    }

    @Bean
    public CardService cardService() {
        return new CardService(new JdbcCardRepository());
    }

    @Bean
    public MoneyRequestService moneyRequestService() {
        return new MoneyRequestService(new JdbcMoneyRequestRepository());
    }
}
