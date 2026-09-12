package com.vmargin.banking;

import com.vmargin.banking.model.Transaction;
import com.vmargin.banking.model.TransactionType;
import com.vmargin.banking.model.User;
import com.vmargin.banking.service.CashInService;
import com.vmargin.banking.service.AdminService;
import com.vmargin.banking.service.LoginService;
import com.vmargin.banking.service.RegistrationService;
import com.vmargin.banking.service.TransactionHistoryService;
import com.vmargin.banking.service.TransferService;
import com.vmargin.banking.service.exception.AccountLockedException;
import com.vmargin.banking.service.exception.InvalidCredentialsException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class LoginFrame extends JFrame {

    private static final Color CANVAS_COLOR = new Color(247, 249, 248);
    private static final Color SURFACE_COLOR = Color.WHITE;
    private static final Color PRIMARY_COLOR = new Color(19, 78, 74);
    private static final Color PRIMARY_LIGHT_COLOR = new Color(220, 252, 231);
    private static final Color TEXT_COLOR = new Color(16, 42, 42);
    private static final Color MUTED_COLOR = new Color(94, 106, 106);
    private static final Color BORDER_COLOR = new Color(214, 223, 222);
    private static final Color ERROR_COLOR = new Color(185, 28, 28);
    private static final Color SUCCESS_COLOR = new Color(21, 128, 61);
    private static final Color SUCCESS_BACKGROUND_COLOR = new Color(240, 253, 244);
    private static final int LOGIN_WIDTH = 520;
    private static final int LOGIN_HEIGHT = 420;
    private static final int DASHBOARD_WIDTH = 700;
    private static final int DASHBOARD_HEIGHT = 540;
    private static final Dimension MINIMUM_WINDOW_SIZE = new Dimension(520, 420);
    private static final DateTimeFormatter HISTORY_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a");

    private final LoginService loginService;
    private final RegistrationService registrationService;
    private final AdminService adminService;
    private final CashInService cashInService;
    private final TransferService transferService;
    private final TransactionHistoryService historyService;
    private final JTextField mobileField = new JTextField();
    private final JPasswordField pinField = new JPasswordField();
    private final JButton loginButton = new JButton("Log in");
    private final JLabel feedbackLabel = new JLabel(" ", SwingConstants.CENTER);

    private User currentUser;
    private String dashboardNotice;

    public LoginFrame(
        LoginService loginService,
        CashInService cashInService,
        TransferService transferService,
        TransactionHistoryService historyService,
        RegistrationService registrationService,
        AdminService adminService
    ) {
        super("CASH-G Banking App");
        this.loginService = loginService;
        this.cashInService = cashInService;
        this.transferService = transferService;
        this.historyService = historyService;
        this.registrationService = registrationService;
        this.adminService = adminService;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(MINIMUM_WINDOW_SIZE);
        setResizable(true);
        configureLoginAccessibility();
        loginButton.addActionListener(event -> attemptLogin());
        showLoginScreen();
        setLocationRelativeTo(null);
    }

    private void configureLoginAccessibility() {
        mobileField.setName("mobile-number");
        mobileField.getAccessibleContext().setAccessibleName("Mobile number");
        mobileField.getAccessibleContext().setAccessibleDescription(
            "Enter the mobile number registered to this CASH-G demo account"
        );
        pinField.setName("pin");
        pinField.getAccessibleContext().setAccessibleName("PIN");
        pinField.getAccessibleContext().setAccessibleDescription(
            "Enter the account PIN"
        );
        loginButton.setName("login");
        feedbackLabel.setName("login-status");
        feedbackLabel.getAccessibleContext().setAccessibleName("Login status");
    }

    private void showLoginScreen() {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(32, 44, 32, 44));

        JLabel title = new JLabel("CASH-G", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(PRIMARY_COLOR);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Your local banking dashboard", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(MUTED_COLOR);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);

        JPanel heading = verticalPanel();
        heading.add(title);
        heading.add(Box.createVerticalStrut(6));
        heading.add(subtitle);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(SURFACE_COLOR);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(24, 20, 20, 20)
        ));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 0, 10, 14);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0;
        constraints.gridx = 0;
        constraints.gridy = 0;
        form.add(fieldLabel("Mobile number", mobileField), constraints);

        constraints.insets = new Insets(0, 0, 10, 0);
        constraints.weightx = 1;
        constraints.gridx = 1;
        form.add(mobileField, constraints);

        constraints.insets = new Insets(0, 0, 22, 14);
        constraints.weightx = 0;
        constraints.gridx = 0;
        constraints.gridy = 1;
        form.add(fieldLabel("PIN", pinField), constraints);

        constraints.insets = new Insets(0, 0, 22, 0);
        constraints.weightx = 1;
        constraints.gridx = 1;
        form.add(pinField, constraints);

        stylePrimaryButton(loginButton);
        constraints.insets = new Insets(0, 0, 12, 0);
        constraints.gridx = 1;
        constraints.gridy = 2;
        form.add(loginButton, constraints);

        JButton createAccountButton = new JButton("Create account");
        createAccountButton.setName("create-account");
        styleSecondaryButton(createAccountButton);
        createAccountButton.addActionListener(event -> showRegistrationScreen());
        constraints.insets = new Insets(0, 0, 12, 0);
        constraints.gridx = 1;
        constraints.gridy = 3;
        form.add(createAccountButton, constraints);

        feedbackLabel.setForeground(ERROR_COLOR);
        feedbackLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        form.add(feedbackLabel, constraints);

        root.add(heading, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(LOGIN_WIDTH, LOGIN_HEIGHT);
        getRootPane().setDefaultButton(loginButton);
        mobileField.requestFocusInWindow();
        refreshScreen();
    }

    private void attemptLogin() {
        String mobile = mobileField.getText().trim();
        String pin = new String(pinField.getPassword());
        setLoginControlsEnabled(false);
        showFeedback("Signing in...", MUTED_COLOR);

        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() throws Exception {
                return loginService.login(mobile, pin);
            }

            @Override
            protected void done() {
                try {
                    currentUser = get();
                    dashboardNotice = "Signed in successfully.";
                    showHomeScreen();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    restoreLoginAfterFailure("Sign-in was interrupted. Try again.");
                } catch (ExecutionException exception) {
                    handleLoginFailure(exception.getCause());
                }
            }
        }.execute();
    }

    private void handleLoginFailure(Throwable cause) {
        if (cause instanceof InvalidCredentialsException) {
            int remainingAttempts = loginService.getRemainingAttempts();
            restoreLoginAfterFailure(
                cause.getMessage() + ". " + remainingAttempts + " attempts remaining."
            );
            pinField.selectAll();
            pinField.requestFocusInWindow();
        } else if (cause instanceof AccountLockedException) {
            showFeedback(
                "Login is locked for this app session. Close and reopen CASH-G to try again.",
                ERROR_COLOR
            );
            setLoginControlsEnabled(false);
        } else if (cause instanceof java.sql.SQLException) {
            restoreLoginAfterFailure(
                "CASH-G could not reach local account data. Check the database connection."
            );
        } else if (cause instanceof IllegalStateException) {
            restoreLoginAfterFailure(
                "CASH-G is not configured for local account data. Check the run setup."
            );
        } else {
            restoreLoginAfterFailure("Sign-in failed unexpectedly. Try again.");
        }
    }

    private void restoreLoginAfterFailure(String message) {
        setLoginControlsEnabled(true);
        showFeedback(message, ERROR_COLOR);
    }

    private void showRegistrationScreen() {
        JTextField fullNameField = createTextField("registration-name", "Full name");
        JTextField mobileNumberField = createTextField(
            "registration-mobile-number",
            "Eleven-digit Philippine mobile number"
        );
        JPasswordField registrationPinField = new JPasswordField();
        registrationPinField.setName("registration-pin");
        registrationPinField.getAccessibleContext().setAccessibleName("Four-digit PIN");
        JLabel statusLabel = createStatusLabel("Registration status");
        JButton submitButton = new JButton("Create account");
        submitButton.setName("registration-submit");
        stylePrimaryButton(submitButton);
        JTextField[] fields = {fullNameField, mobileNumberField, registrationPinField};
        submitButton.addActionListener(event -> submitRegistration(
            fullNameField,
            mobileNumberField,
            registrationPinField,
            fields,
            submitButton,
            statusLabel
        ));
        showFormScreen(
            "Create account",
            "Create a local CASH-G user account with a zero starting balance.",
            new String[] {"Full name", "Mobile number", "PIN"},
            fields,
            submitButton,
            statusLabel
        );
        fullNameField.requestFocusInWindow();
    }

    private void submitRegistration(
        JTextField fullNameField,
        JTextField mobileNumberField,
        JPasswordField registrationPinField,
        JTextField[] fields,
        JButton submitButton,
        JLabel statusLabel
    ) {
        String fullName = fullNameField.getText();
        String mobileNumber = mobileNumberField.getText();
        String pin = new String(registrationPinField.getPassword());
        setFormControlsEnabled(fields, submitButton, false);
        submitButton.setText("Creating account...");
        showStatus(statusLabel, "Creating your local account...", MUTED_COLOR);
        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() throws Exception {
                return registrationService.register(fullName, mobileNumber, pin);
            }

            @Override
            protected void done() {
                try {
                    User registeredUser = get();
                    mobileField.setText(registeredUser.getMobileNumber());
                    pinField.setText("");
                    showLoginScreen();
                    showFeedback("Account created. Sign in with your new PIN.", SUCCESS_COLOR);
                    pinField.requestFocusInWindow();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    restoreFormAfterFailure(
                        fields,
                        submitButton,
                        "Create account",
                        statusLabel,
                        "Account creation was interrupted. Try again."
                    );
                } catch (ExecutionException exception) {
                    String message = exception.getCause() instanceof IllegalArgumentException
                        ? exception.getCause().getMessage()
                        : "Account could not be created. Try again.";
                    restoreFormAfterFailure(
                        fields,
                        submitButton,
                        "Create account",
                        statusLabel,
                        message
                    );
                }
            }
        }.execute();
    }

    private void showDashboard() {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(false), BorderLayout.NORTH);
        root.add(createDashboardContent(), BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(null);
        refreshScreen();
    }

    private void showHomeScreen() {
        if (currentUser != null && currentUser.isAdmin()) {
            showAdminDashboard();
        } else {
            showDashboard();
        }
    }

    private void showAdminDashboard() {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(false), BorderLayout.NORTH);

        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(26, 0, 0, 0));
        content.add(screenTitle("Administrator console"));
        content.add(Box.createVerticalStrut(6));
        content.add(screenSubtitle("Review local users and synthetic transaction records."));
        content.add(Box.createVerticalStrut(22));
        content.add(createAdminActionButton("View all users", this::showAdminUsersScreen));
        content.add(Box.createVerticalStrut(10));
        content.add(createAdminActionButton("View all transactions", this::showAdminTransactionsScreen));
        content.add(Box.createVerticalStrut(10));
        content.add(createAdminActionButton(
            "View a user's transactions",
            this::showSpecificUserTransactionsScreen
        ));
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(null);
        refreshScreen();
    }

    private JButton createAdminActionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setName(text.toLowerCase().replace(' ', '-'));
        stylePrimaryButton(button);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void showAdminUsersScreen() {
        DefaultTableModel tableModel = new DefaultTableModel(
            new String[] {"ID", "Name", "Mobile", "Role", "Balance"},
            0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JLabel statusLabel = createStatusLabel("Administrator user list status");
        showAdminTableScreen("All users", "Local CASH-G accounts, including their role and balance.",
            tableModel, statusLabel);
        new SwingWorker<List<User>, Void>() {
            @Override
            protected List<User> doInBackground() throws Exception {
                return adminService.getAllUsers(currentUser);
            }

            @Override
            protected void done() {
                try {
                    for (User user : get()) {
                        tableModel.addRow(new Object[] {
                            user.getId(), user.getFullName(), user.getMobileNumber(),
                            user.getRole(), formatCurrency(user.getBalance())
                        });
                    }
                    showStatus(statusLabel, tableModel.getRowCount() + " users loaded.", MUTED_COLOR);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStatus(statusLabel, "User loading was interrupted.", ERROR_COLOR);
                } catch (ExecutionException exception) {
                    showStatus(statusLabel, "Users could not be loaded.", ERROR_COLOR);
                }
            }
        }.execute();
    }

    private void showAdminTransactionsScreen() {
        showAdminTransactionTable("All transactions", "Activity across all local CASH-G accounts.", 0L);
    }

    private void showSpecificUserTransactionsScreen() {
        JTextField userIdField = createTextField("admin-user-id", "User ID");
        JLabel statusLabel = createStatusLabel("Administrator transaction search status");
        JButton submitButton = new JButton("View transactions");
        stylePrimaryButton(submitButton);
        submitButton.addActionListener(event -> {
            try {
                long userId = Long.parseLong(userIdField.getText().trim());
                if (userId <= 0) {
                    throw new NumberFormatException();
                }
                showAdminTransactionTable(
                    "User " + userId + " transactions",
                    "Activity for the selected local account.",
                    userId
                );
            } catch (NumberFormatException exception) {
                showStatus(statusLabel, "Enter a positive numeric user ID.", ERROR_COLOR);
            }
        });
        showFormScreen(
            "Find user transactions",
            "Enter the user ID shown in the All users table.",
            new String[] {"User ID"},
            new JTextField[] {userIdField},
            submitButton,
            statusLabel
        );
        userIdField.requestFocusInWindow();
    }

    private void showAdminTransactionTable(String title, String subtitle, long userId) {
        DefaultTableModel tableModel = createHistoryTableModel();
        JLabel statusLabel = createStatusLabel("Administrator transaction list status");
        showAdminTableScreen(title, subtitle, tableModel, statusLabel);
        new SwingWorker<List<Transaction>, Void>() {
            @Override
            protected List<Transaction> doInBackground() throws Exception {
                return userId == 0L
                    ? adminService.getAllTransactions(currentUser)
                    : adminService.getTransactionsForUser(currentUser, userId);
            }

            @Override
            protected void done() {
                try {
                    List<Transaction> transactions = get();
                    addHistoryRows(tableModel, transactions);
                    showStatus(statusLabel, transactions.size() + " transactions loaded.", MUTED_COLOR);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStatus(statusLabel, "Transaction loading was interrupted.", ERROR_COLOR);
                } catch (ExecutionException exception) {
                    showStatus(statusLabel, "Transactions could not be loaded.", ERROR_COLOR);
                }
            }
        }.execute();
    }

    private void showAdminTableScreen(
        String titleText,
        String subtitleText,
        DefaultTableModel tableModel,
        JLabel statusLabel
    ) {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(true), BorderLayout.NORTH);
        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(26, 0, 0, 0));
        content.add(screenTitle(titleText));
        content.add(Box.createVerticalStrut(6));
        content.add(screenSubtitle(subtitleText));
        content.add(Box.createVerticalStrut(14));
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(createAdminTable(tableModel));
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(null);
        refreshScreen();
    }

    private JPanel createScreenHeader(boolean includeBackButton) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel brand = new JLabel("CASH-G");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 20));
        brand.setForeground(PRIMARY_COLOR);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        if (includeBackButton) {
            String backLabel = currentUser == null
                ? "Back to login"
                : currentUser.isAdmin() ? "Back to admin console" : "Back to dashboard";
            JButton backButton = new JButton(backLabel);
            backButton.setName("back-to-dashboard");
            styleSecondaryButton(backButton);
            backButton.addActionListener(event -> {
                if (currentUser == null) {
                    showLoginScreen();
                } else {
                    showHomeScreen();
                }
            });
            actions.add(backButton);
        }

        JButton logoutButton = new JButton("Log out");
        logoutButton.setName("logout");
        styleSecondaryButton(logoutButton);
        logoutButton.addActionListener(event -> resetToLogin());
        actions.add(logoutButton);

        header.add(brand, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel createDashboardContent() {
        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));

        JLabel greeting = new JLabel("Welcome back, " + currentUser.getFullName());
        greeting.setFont(new Font("Segoe UI", Font.BOLD, 25));
        greeting.setForeground(TEXT_COLOR);
        greeting.setAlignmentX(LEFT_ALIGNMENT);

        JLabel description = new JLabel("Here is your current account overview.");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        description.setForeground(MUTED_COLOR);
        description.setAlignmentX(LEFT_ALIGNMENT);

        content.add(greeting);
        content.add(Box.createVerticalStrut(6));
        content.add(description);
        if (dashboardNotice != null) {
            content.add(Box.createVerticalStrut(14));
            content.add(createSuccessBanner(dashboardNotice));
            dashboardNotice = null;
        }
        content.add(Box.createVerticalStrut(18));
        content.add(createBalanceCard());
        content.add(Box.createVerticalStrut(20));
        content.add(sectionLabel("Quick actions"));
        content.add(Box.createVerticalStrut(10));
        content.add(createQuickActions());
        return content;
    }

    private JPanel createSuccessBanner(String message) {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(SUCCESS_BACKGROUND_COLOR);
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(187, 247, 208)),
            BorderFactory.createEmptyBorder(9, 12, 9, 12)
        ));
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        banner.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = new JLabel(message);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(SUCCESS_COLOR);
        label.getAccessibleContext().setAccessibleName("Success message");
        banner.add(label, BorderLayout.CENTER);
        return banner;
    }

    private JPanel createBalanceCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(PRIMARY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY_COLOR),
            BorderFactory.createEmptyBorder(22, 26, 22, 26)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 122));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel("AVAILABLE BALANCE");
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(PRIMARY_LIGHT_COLOR);

        JLabel balance = new JLabel(formatCurrency(currentUser.getBalance()));
        balance.setFont(new Font("Segoe UI", Font.BOLD, 30));
        balance.setForeground(Color.WHITE);
        balance.getAccessibleContext().setAccessibleName(
            "Available balance " + balance.getText()
        );

        JLabel hint = new JLabel("Updated after each successful transaction.");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hint.setForeground(PRIMARY_LIGHT_COLOR);

        card.add(label, BorderLayout.NORTH);
        card.add(balance, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createQuickActions() {
        JPanel actions = new JPanel(new GridLayout(1, 3, 12, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        actions.add(createActionButton("Cash In", "Add funds", "cash-in"));
        actions.add(createActionButton("Transfer", "Send money", "transfer"));
        actions.add(createActionButton("History", "View activity", "history"));
        return actions;
    }

    private JButton createActionButton(String title, String description, String featureName) {
        JButton button = new JButton("<html><b>" + title + "</b><br><small>"
            + description + "</small></html>");
        button.setName(featureName);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        button.getAccessibleContext().setAccessibleName(title);
        button.getAccessibleContext().setAccessibleDescription(description);
        styleSecondaryButton(button);
        button.addActionListener(event -> openQuickAction(featureName));
        return button;
    }

    private void openQuickAction(String featureName) {
        switch (featureName) {
            case "cash-in" -> showCashInScreen();
            case "transfer" -> showTransferScreen();
            case "history" -> showHistoryScreen();
            default -> throw new IllegalArgumentException("Unknown quick action");
        }
    }

    private void showCashInScreen() {
        JTextField amountField = createTextField(
            "cash-in-amount",
            "Cash-in amount in Philippine pesos"
        );
        JTextField detailsField = createTextField(
            "cash-in-details",
            "Description for this cash-in"
        );
        detailsField.setText("Cash-in");
        JLabel statusLabel = createStatusLabel("Cash-in status");
        JButton submitButton = new JButton("Add funds");
        submitButton.setName("cash-in-submit");
        stylePrimaryButton(submitButton);
        JTextField[] fields = {amountField, detailsField};
        submitButton.addActionListener(event -> submitCashIn(
            amountField,
            detailsField,
            fields,
            submitButton,
            statusLabel
        ));
        showFormScreen(
            "Cash in",
            "Add funds to your account and record the activity.",
            new String[] {"Amount (PHP)", "Details"},
            fields,
            submitButton,
            statusLabel
        );
        amountField.requestFocusInWindow();
    }

    private void submitCashIn(
        JTextField amountField,
        JTextField detailsField,
        JTextField[] fields,
        JButton submitButton,
        JLabel statusLabel
    ) {
        BigDecimal amount;
        try {
            amount = parseAmount(amountField.getText());
        } catch (NumberFormatException exception) {
            showStatus(statusLabel, "Enter a valid amount, for example 500.00.", ERROR_COLOR);
            amountField.requestFocusInWindow();
            return;
        }

        setFormControlsEnabled(fields, submitButton, false);
        submitButton.setText("Adding funds...");
        showStatus(statusLabel, "Saving your cash-in...", MUTED_COLOR);
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws Exception {
                return cashInService.cashIn(currentUser, amount, detailsField.getText());
            }

            @Override
            protected void done() {
                try {
                    BigDecimal updatedBalance = get();
                    dashboardNotice = "Cash-in successful: " + formatCurrency(amount)
                        + ". New balance: " + formatCurrency(updatedBalance) + ".";
                    showDashboard();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    restoreFormAfterFailure(
                        fields,
                        submitButton,
                        "Add funds",
                        statusLabel,
                        "Cash-in was interrupted. Try again."
                    );
                } catch (ExecutionException exception) {
                    String message = exception.getCause() instanceof IllegalArgumentException
                        ? exception.getCause().getMessage()
                        : "Cash-in could not be saved. No balance was changed.";
                    restoreFormAfterFailure(
                        fields,
                        submitButton,
                        "Add funds",
                        statusLabel,
                        message
                    );
                }
            }
        }.execute();
    }

    private void showTransferScreen() {
        showTransferScreen("", "");
    }

    private void showTransferScreen(String recipient, String amountText) {
        JTextField mobileNumberField = createTextField(
            "transfer-recipient",
            "Mobile number of the recipient CASH-G account"
        );
        mobileNumberField.setText(recipient);
        JTextField amountField = createTextField(
            "transfer-amount",
            "Transfer amount in Philippine pesos"
        );
        amountField.setText(amountText);
        JLabel statusLabel = createStatusLabel("Transfer form status");
        JButton reviewButton = new JButton("Review transfer");
        reviewButton.setName("review-transfer");
        stylePrimaryButton(reviewButton);
        JTextField[] fields = {mobileNumberField, amountField};
        reviewButton.addActionListener(event -> reviewTransfer(
            mobileNumberField,
            amountField,
            statusLabel
        ));
        showFormScreen(
            "Transfer",
            "Send money securely to another local CASH-G account.",
            new String[] {"Recipient mobile number", "Amount (PHP)"},
            fields,
            reviewButton,
            statusLabel
        );
        mobileNumberField.requestFocusInWindow();
    }

    private void reviewTransfer(
        JTextField mobileNumberField,
        JTextField amountField,
        JLabel statusLabel
    ) {
        String recipient = mobileNumberField.getText().trim();
        if (recipient.isBlank()) {
            showStatus(statusLabel, "Enter the recipient mobile number.", ERROR_COLOR);
            mobileNumberField.requestFocusInWindow();
            return;
        }

        BigDecimal amount;
        try {
            amount = parseAmount(amountField.getText());
        } catch (NumberFormatException exception) {
            showStatus(statusLabel, "Enter a valid amount, for example 500.00.", ERROR_COLOR);
            amountField.requestFocusInWindow();
            return;
        }
        showTransferReviewScreen(recipient, amount);
    }

    private void showTransferReviewScreen(String recipient, BigDecimal amount) {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(true), BorderLayout.NORTH);

        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(26, 24, 0, 24));
        JLabel title = screenTitle("Review transfer");
        JLabel subtitle = screenSubtitle(
            "Check the recipient and amount before sending. This cannot be undone in the demo."
        );
        JPanel summary = createTransferSummary(recipient, amount);
        JLabel statusLabel = createStatusLabel("Transfer confirmation status");

        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JButton editButton = new JButton("Back and edit");
        editButton.setName("edit-transfer");
        styleSecondaryButton(editButton);
        editButton.addActionListener(event -> showTransferScreen(
            recipient,
            amount.toPlainString()
        ));
        JButton confirmButton = new JButton("Confirm transfer");
        confirmButton.setName("confirm-transfer");
        stylePrimaryButton(confirmButton);
        confirmButton.addActionListener(event -> submitTransfer(
            recipient,
            amount,
            editButton,
            confirmButton,
            statusLabel
        ));
        actions.add(editButton);
        actions.add(confirmButton);

        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(20));
        content.add(summary);
        content.add(Box.createVerticalStrut(16));
        content.add(actions);
        content.add(Box.createVerticalStrut(10));
        content.add(statusLabel);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(confirmButton);
        confirmButton.requestFocusInWindow();
        refreshScreen();
    }

    private JPanel createTransferSummary(String recipient, BigDecimal amount) {
        JPanel summary = new JPanel(new GridBagLayout());
        summary.setBackground(SURFACE_COLOR);
        summary.setAlignmentX(LEFT_ALIGNMENT);
        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 124));
        summary.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(20, 22, 20, 22)
        ));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 12, 22);
        constraints.gridx = 0;
        constraints.gridy = 0;
        summary.add(fieldLabel("Recipient", null), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        summary.add(valueLabel(recipient), constraints);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.insets = new Insets(0, 0, 0, 22);
        summary.add(fieldLabel("Amount", null), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        summary.add(valueLabel(formatCurrency(amount)), constraints);
        return summary;
    }

    private void submitTransfer(
        String recipient,
        BigDecimal amount,
        JButton editButton,
        JButton confirmButton,
        JLabel statusLabel
    ) {
        editButton.setEnabled(false);
        confirmButton.setEnabled(false);
        confirmButton.setText("Sending...");
        showStatus(statusLabel, "Sending your transfer...", MUTED_COLOR);
        new SwingWorker<BigDecimal, Void>() {
            @Override
            protected BigDecimal doInBackground() throws Exception {
                return transferService.transfer(currentUser, recipient, amount);
            }

            @Override
            protected void done() {
                try {
                    BigDecimal updatedBalance = get();
                    dashboardNotice = "Transfer successful: " + formatCurrency(amount)
                        + " sent to " + recipient + ". New balance: "
                        + formatCurrency(updatedBalance) + ".";
                    showDashboard();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    restoreTransferReview(
                        editButton,
                        confirmButton,
                        statusLabel,
                        "Transfer was interrupted. Try again."
                    );
                } catch (ExecutionException exception) {
                    String message = exception.getCause() instanceof IllegalArgumentException
                        ? exception.getCause().getMessage()
                        : "Transfer could not be saved. No balance was changed.";
                    restoreTransferReview(
                        editButton,
                        confirmButton,
                        statusLabel,
                        message
                    );
                }
            }
        }.execute();
    }

    private void restoreTransferReview(
        JButton editButton,
        JButton confirmButton,
        JLabel statusLabel,
        String message
    ) {
        editButton.setEnabled(true);
        confirmButton.setEnabled(true);
        confirmButton.setText("Confirm transfer");
        showStatus(statusLabel, message, ERROR_COLOR);
        confirmButton.requestFocusInWindow();
    }

    private void showHistoryScreen() {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(true), BorderLayout.NORTH);

        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(26, 0, 0, 0));
        JLabel title = screenTitle("Transaction history");
        JLabel message = screenSubtitle("Your most recent account activity appears first.");
        JLabel statusLabel = createStatusLabel("Transaction history status");
        showStatus(statusLabel, "Loading transactions...", MUTED_COLOR);
        DefaultTableModel tableModel = createHistoryTableModel();
        JScrollPane historyTable = createHistoryTable(tableModel);

        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(message);
        content.add(Box.createVerticalStrut(14));
        content.add(statusLabel);
        content.add(Box.createVerticalStrut(10));
        content.add(historyTable);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(null);
        refreshScreen();
        loadHistory(tableModel, statusLabel);
    }

    private void loadHistory(DefaultTableModel tableModel, JLabel statusLabel) {
        new SwingWorker<List<Transaction>, Void>() {
            @Override
            protected List<Transaction> doInBackground() throws Exception {
                return historyService.getHistory(currentUser);
            }

            @Override
            protected void done() {
                try {
                    List<Transaction> transactions = get();
                    addHistoryRows(tableModel, transactions);
                    if (transactions.isEmpty()) {
                        showStatus(
                            statusLabel,
                            "No transactions yet. Completed activity will appear here.",
                            MUTED_COLOR
                        );
                    } else {
                        showStatus(
                            statusLabel,
                            transactions.size() + " transactions loaded.",
                            MUTED_COLOR
                        );
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showStatus(
                        statusLabel,
                        "Transaction loading was interrupted. Return and try again.",
                        ERROR_COLOR
                    );
                } catch (ExecutionException exception) {
                    showStatus(
                        statusLabel,
                        "Transaction history could not be loaded. Return and try again.",
                        ERROR_COLOR
                    );
                }
            }
        }.execute();
    }

    private DefaultTableModel createHistoryTableModel() {
        String[] columns = {"Type", "Amount", "Details", "When"};
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void addHistoryRows(DefaultTableModel tableModel, List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            tableModel.addRow(new Object[] {
                formatTransactionType(transaction.getType()),
                formatTransactionAmount(transaction.getType(), transaction.getAmount()),
                transaction.getDetails(),
                transaction.getOccurredAt().format(HISTORY_DATE_FORMATTER)
            });
        }
    }

    private JScrollPane createHistoryTable(DefaultTableModel tableModel) {
        JTable table = new JTable(tableModel);
        table.setName("transaction-history");
        table.getAccessibleContext().setAccessibleName("Transaction history");
        table.getAccessibleContext().setAccessibleDescription(
            "Most recent CASH-G transactions, with type, signed amount, details, and date"
        );
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(30);
        table.setForeground(TEXT_COLOR);
        table.setGridColor(BORDER_COLOR);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(SURFACE_COLOR);
        table.getTableHeader().setForeground(TEXT_COLOR);
        table.getColumnModel().getColumn(1).setCellRenderer(createAmountRenderer());
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getViewport().setBackground(SURFACE_COLOR);
        scrollPane.setPreferredSize(new Dimension(DASHBOARD_WIDTH - 60, 230));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        return scrollPane;
    }

    private JScrollPane createAdminTable(DefaultTableModel tableModel) {
        JTable table = new JTable(tableModel);
        table.setName("administrator-data-table");
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(30);
        table.setForeground(TEXT_COLOR);
        table.setGridColor(BORDER_COLOR);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(SURFACE_COLOR);
        table.getTableHeader().setForeground(TEXT_COLOR);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getViewport().setBackground(SURFACE_COLOR);
        scrollPane.setPreferredSize(new Dimension(DASHBOARD_WIDTH - 60, 230));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        return scrollPane;
    }

    private DefaultTableCellRenderer createAmountRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
            ) {
                Component component = super.getTableCellRendererComponent(
                    table,
                    value,
                    isSelected,
                    hasFocus,
                    row,
                    column
                );
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 8));
                if (!isSelected) {
                    String type = String.valueOf(table.getValueAt(row, 0));
                    component.setForeground(
                        "Transfer sent".equals(type) ? ERROR_COLOR : SUCCESS_COLOR
                    );
                }
                return component;
            }
        };
    }

    private void showFormScreen(
        String titleText,
        String subtitleText,
        String[] fieldNames,
        JTextField[] fields,
        JButton submitButton,
        JLabel statusLabel
    ) {
        JPanel root = createScreenRoot();
        root.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));
        root.add(createScreenHeader(true), BorderLayout.NORTH);

        JPanel content = verticalPanel();
        content.setBorder(BorderFactory.createEmptyBorder(26, 24, 0, 24));
        JLabel title = screenTitle(titleText);
        JLabel subtitle = screenSubtitle(subtitleText);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(SURFACE_COLOR);
        form.setAlignmentX(LEFT_ALIGNMENT);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(20, 20, 18, 20)
        ));
        addFormFields(form, fieldNames, fields, submitButton, statusLabel);

        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(20));
        content.add(form);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        ensureWindowSize(DASHBOARD_WIDTH, DASHBOARD_HEIGHT);
        getRootPane().setDefaultButton(submitButton);
        refreshScreen();
    }

    private void addFormFields(
        JPanel form,
        String[] fieldNames,
        JTextField[] fields,
        JButton submitButton,
        JLabel statusLabel
    ) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        for (int index = 0; index < fields.length; index++) {
            constraints.gridy = index;
            constraints.gridx = 0;
            constraints.weightx = 0;
            constraints.insets = new Insets(0, 0, 14, 16);
            form.add(fieldLabel(fieldNames[index], fields[index]), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            constraints.insets = new Insets(0, 0, 14, 0);
            form.add(fields[index], constraints);
        }
        constraints.gridy = fields.length;
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(2, 0, 8, 0);
        form.add(submitButton, constraints);
        constraints.gridy = fields.length + 1;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(0, 0, 0, 0);
        form.add(statusLabel, constraints);
    }

    private JPanel createScreenRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CANVAS_COLOR);
        return root;
    }

    private JPanel verticalPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JTextField createTextField(String name, String description) {
        JTextField field = new JTextField();
        field.setName(name);
        field.getAccessibleContext().setAccessibleName(description);
        field.getAccessibleContext().setAccessibleDescription(description);
        return field;
    }

    private JLabel fieldLabel(String text, JComponent field) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(TEXT_COLOR);
        if (field != null) {
            label.setLabelFor(field);
        }
        return label;
    }

    private JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(TEXT_COLOR);
        return label;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(TEXT_COLOR);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel screenTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 26));
        label.setForeground(TEXT_COLOR);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel screenSubtitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED_COLOR);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createStatusLabel(String accessibleName) {
        JLabel label = new JLabel(" ");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(MUTED_COLOR);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.getAccessibleContext().setAccessibleName(accessibleName);
        return label;
    }

    private void stylePrimaryButton(JButton button) {
        button.setUI(new BasicButtonUI());
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY_COLOR, 2),
            BorderFactory.createEmptyBorder(9, 16, 9, 16)
        ));
        button.setFocusPainted(true);
        button.setAlignmentX(CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        button.getAccessibleContext().setAccessibleName(button.getText());
    }

    private void styleSecondaryButton(JButton button) {
        button.setBackground(SURFACE_COLOR);
        button.setForeground(TEXT_COLOR);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        button.setFocusPainted(true);
        button.getAccessibleContext().setAccessibleName(button.getText());
    }

    private void resetToLogin() {
        currentUser = null;
        dashboardNotice = null;
        mobileField.setText("");
        pinField.setText("");
        setLoginControlsEnabled(true);
        feedbackLabel.setText(" ");
        showLoginScreen();
    }

    private void setLoginControlsEnabled(boolean enabled) {
        mobileField.setEnabled(enabled);
        pinField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }

    private void setFormControlsEnabled(
        JTextField[] fields,
        JButton submitButton,
        boolean enabled
    ) {
        for (JTextField field : fields) {
            field.setEnabled(enabled);
        }
        submitButton.setEnabled(enabled);
    }

    private void restoreFormAfterFailure(
        JTextField[] fields,
        JButton submitButton,
        String buttonText,
        JLabel statusLabel,
        String message
    ) {
        setFormControlsEnabled(fields, submitButton, true);
        submitButton.setText(buttonText);
        submitButton.getAccessibleContext().setAccessibleName(buttonText);
        showStatus(statusLabel, message, ERROR_COLOR);
        submitButton.requestFocusInWindow();
    }

    private void showFeedback(String message, Color color) {
        feedbackLabel.setText(message);
        feedbackLabel.setForeground(color);
        feedbackLabel.getAccessibleContext().setAccessibleDescription(message);
    }

    private void showStatus(JLabel label, String message, Color color) {
        label.setText(message);
        label.setForeground(color);
        label.getAccessibleContext().setAccessibleDescription(message);
    }

    private void refreshScreen() {
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private void ensureWindowSize(int width, int height) {
        int targetWidth = Math.max(getWidth(), width);
        int targetHeight = Math.max(getHeight(), height);
        if (targetWidth != getWidth() || targetHeight != getHeight()) {
            setSize(targetWidth, targetHeight);
        }
    }

    private BigDecimal parseAmount(String text) {
        return new BigDecimal(text.trim());
    }

    static String formatCurrency(BigDecimal amount) {
        DecimalFormat formatter = new DecimalFormat(
            "#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.US)
        );
        return "PHP " + formatter.format(amount);
    }

    static String formatTransactionAmount(TransactionType type, BigDecimal amount) {
        String sign = type == TransactionType.TRANSFER_SENT ? "- " : "+ ";
        return sign + formatCurrency(amount);
    }

    static String formatTransactionType(TransactionType type) {
        return switch (type) {
            case CASH_IN -> "Cash in";
            case TRANSFER_SENT -> "Transfer sent";
            case TRANSFER_RECEIVED -> "Transfer received";
        };
    }
}
