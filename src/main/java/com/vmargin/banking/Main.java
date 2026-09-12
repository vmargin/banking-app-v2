package com.vmargin.banking;

import com.vmargin.banking.repository.JdbcUserRepository;
import com.vmargin.banking.repository.JdbcCashInRepository;
import com.vmargin.banking.repository.JdbcTransactionRepository;
import com.vmargin.banking.repository.JdbcTransferRepository;
import com.vmargin.banking.service.AdminService;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        JdbcUserRepository userRepository = new JdbcUserRepository();
        JdbcTransactionRepository transactionRepository = new JdbcTransactionRepository();
        LoginService loginService = new LoginService(userRepository);
        CashInService cashInService = new CashInService(new JdbcCashInRepository());
        TransferService transferService = new TransferService(new JdbcTransferRepository());
        TransactionHistoryService historyService = new TransactionHistoryService(transactionRepository);
        RegistrationService registrationService = new RegistrationService(userRepository);
        AdminService adminService = new AdminService(userRepository, transactionRepository);
        configureLookAndFeel();
        SwingUtilities.invokeLater(
            () -> launchUi(
                args,
                loginService,
                cashInService,
                transferService,
                historyService,
                registrationService,
                adminService
            )
        );
    }

    private static void launchUi(
        String[] args,
        LoginService loginService,
        CashInService cashInService,
        TransferService transferService,
        TransactionHistoryService historyService,
        RegistrationService registrationService,
        AdminService adminService
    ) {
        if (args.length > 0 && "--swing".equalsIgnoreCase(args[0])) {
            new LoginFrame(
                loginService,
                cashInService,
                transferService,
                historyService,
                registrationService,
                adminService
            ).setVisible(true);
            return;
        }
        new WebUiFrame().setVisible(true);
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException exception) {
            // The default Swing look and feel is an acceptable fallback.
        }
    }
}
