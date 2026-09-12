package com.vmargin.banking.web;

import com.vmargin.banking.repository.JdbcCashInRepository;
import com.vmargin.banking.repository.JdbcTransactionRepository;
import com.vmargin.banking.repository.JdbcTransferRepository;
import com.vmargin.banking.repository.JdbcUserRepository;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
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
}
