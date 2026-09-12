package com.vmargin.banking;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

/**
 * Hosts the visual web UI inside the existing desktop application.
 */
public final class WebUiFrame extends JFrame {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 920;
    private final JFXPanel browserPanel = new JFXPanel();

    public WebUiFrame() {
        super("CASH-G Banking");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(1024, 720));
        add(browserPanel, BorderLayout.CENTER);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        loadLoginPage();
    }

    private void loadLoginPage() {
        Path loginPage = Path.of("web", "login.html").toAbsolutePath();
        if (!Files.isRegularFile(loginPage)) {
            JOptionPane.showMessageDialog(
                this,
                "The web UI was not found at " + loginPage,
                "CASH-G web UI unavailable",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            webView.getEngine().load(loginPage.toUri().toString());
            browserPanel.setScene(new Scene(webView));
        });
    }
}
