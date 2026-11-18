package com.example.jmeter.copilot.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.example.jmeter.copilot.auth.CopilotAuthManager;

/**
 * Panel for Copilot authentication status and login flow.
 */
public class AuthenticationPanel extends JPanel {

    private final CopilotAuthManager authManager;
    private final JLabel statusLabel;
    private final JButton loginButton;
    private final JButton logoutButton;
    private final JTextArea instructionsArea;
    private final Consumer<Boolean> onAuthChanged;

    public AuthenticationPanel(CopilotAuthManager authManager, Consumer<Boolean> onAuthChanged) {
        this.authManager = authManager;
        this.onAuthChanged = onAuthChanged;

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("GitHub Copilot Authentication"));

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel(getStatusText());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusPanel.add(statusLabel);

        // Buttons
        loginButton = new JButton("Sign in with GitHub");
        loginButton.addActionListener(e -> startLogin());

        logoutButton = new JButton("Sign out");
        logoutButton.addActionListener(e -> logout());
        logoutButton.setEnabled(authManager.isAuthenticated());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(loginButton);
        buttonPanel.add(logoutButton);

        // Instructions area (shown during login)
        instructionsArea = new JTextArea(4, 50);
        instructionsArea.setEditable(false);
        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        instructionsArea.setVisible(false);
        instructionsArea.setBackground(new Color(255, 255, 220));
        instructionsArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(statusPanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(instructionsArea, BorderLayout.CENTER);

        refreshAuthState();
    }

    private String getStatusText() {
        if (authManager.isAuthenticated()) {
            return "✅ Authenticated with GitHub Copilot";
        } else {
            return "⚠️ Not authenticated - Sign in to use AI-powered analysis";
        }
    }

    private void refreshAuthState() {
        boolean authenticated = authManager.isAuthenticated();
        statusLabel.setText(getStatusText());
        statusLabel.setForeground(authenticated ? new Color(0, 128, 0) : new Color(180, 100, 0));
        loginButton.setEnabled(!authenticated);
        logoutButton.setEnabled(authenticated);
    }

    private void startLogin() {
        loginButton.setEnabled(false);
        instructionsArea.setVisible(true);
        instructionsArea.setText("Starting GitHub authentication...\n");

        new Thread(() -> {
            try {
                CopilotAuthManager.DeviceCodeResponse deviceCode = authManager.requestDeviceCode();
                
                SwingUtilities.invokeLater(() -> {
                    String instructions = String.format(
                            "To sign in:\n" +
                            "1. Go to: %s\n" +
                            "2. Enter code: %s\n" +
                            "3. Authorize the application\n\n" +
                            "Code copied to clipboard. Waiting for authorization...",
                            deviceCode.verificationUri,
                            deviceCode.userCode
                    );
                    instructionsArea.setText(instructions);
                    
                    // Copy code to clipboard
                    try {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(deviceCode.userCode), null);
                    } catch (Exception ex) {
                        // Ignore clipboard errors
                    }

                    // Open browser
                    try {
                        Desktop.getDesktop().browse(new java.net.URI(deviceCode.verificationUri));
                    } catch (Exception ex) {
                        // Browser open failed, user can navigate manually
                    }
                });

                // Poll for completion
                authManager.pollForAccessToken(deviceCode.deviceCode, deviceCode.interval, 
                        status -> SwingUtilities.invokeLater(() -> {
                            instructionsArea.append("\n" + status);
                        })
                ).thenAccept(success -> {
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            instructionsArea.setText("✅ Successfully authenticated with GitHub Copilot!");
                            onAuthChanged.accept(true);
                        } else {
                            instructionsArea.setText("❌ Authentication failed. Please try again.");
                        }
                        refreshAuthState();
                        
                        // Hide instructions after a delay
                        Timer timer = new Timer(3000, e -> {
                            instructionsArea.setVisible(false);
                            ((Timer) e.getSource()).stop();
                        });
                        timer.setRepeats(false);
                        timer.start();
                    });
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    instructionsArea.setText("Error: " + ex.getMessage());
                    loginButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void logout() {
        authManager.logout();
        refreshAuthState();
        onAuthChanged.accept(false);
    }
}
