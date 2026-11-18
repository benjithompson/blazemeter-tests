package com.example.jmeter.copilot.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import com.example.jmeter.copilot.auth.CopilotAuthManager;
import com.example.jmeter.copilot.core.CopilotCorrelationAdvisor;
import com.example.jmeter.copilot.core.CorrelationAnalyzer;
import com.example.jmeter.copilot.core.CorrelationCandidate;
import com.example.jmeter.copilot.core.RunCapture;
import com.example.jmeter.copilot.core.SampleCapture;
import com.example.jmeter.copilot.core.ViewResultsTreeExtractor;
import com.example.jmeter.copilot.gui.panels.AuthenticationPanel;
import com.example.jmeter.copilot.gui.panels.CorrelationCandidatesPanel;
import com.example.jmeter.copilot.gui.panels.ExecutionTreePanel;

/**
 * Main dialog for JMeter Copilot Assistant.
 * Simplified to focus on:
 * 1. Loading results from View Results Tree
 * 2. AI-powered correlation analysis and JMX modification generation
 */
public final class CopilotAssistantDialog {

    private static volatile CopilotAssistantDialog instance;

    private final JDialog dialog;
    private final JTextArea log;
    private final JButton loadFromViewResultsButton;
    private final JButton clearResultsButton;
    private final JButton analyzeWithCopilotButton;
    private final JButton applyCorrelationsButton;
    private final JComboBox<String> modelSelector;
    private final CorrelationCandidatesPanel candidatesPanel;
    private final JTextArea copilotResponseArea;
    private final ExecutionTreePanel executionTreePanel;
    
    // Authentication UI
    private final JButton authButton;
    private final JLabel authStatusLabel;
    private JDialog authDialog;

    // Core components
    private final ViewResultsTreeExtractor viewResultsExtractor;
    private final CopilotAuthManager authManager;
    private final CorrelationAnalyzer correlationAnalyzer;
    private final CopilotCorrelationAdvisor copilotAdvisor;
    
    private final List<RunCapture> runCaptures = new ArrayList<>();
    
    // Track test plan changes
    private String currentTestPlanName = null;
    private TreeModelListener treeModelListener;

    private CopilotAssistantDialog() {
        this.viewResultsExtractor = new ViewResultsTreeExtractor();
        this.authManager = CopilotAuthManager.getInstance();
        this.correlationAnalyzer = new CorrelationAnalyzer();
        this.copilotAdvisor = new CopilotCorrelationAdvisor(authManager);

        GuiPackage guiPackage = GuiPackage.getInstance();
        Component owner = (guiPackage != null) ? guiPackage.getMainFrame() : null;
        Window ownerWindow = (owner != null) ? SwingUtilities.getWindowAncestor(owner) : null;

        this.dialog = new JDialog(ownerWindow, "JMeter Copilot Assistant - Correlation Analyzer");
        this.dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Initialize components
        this.log = new JTextArea(20, 80);
        this.log.setEditable(false);
        
        this.candidatesPanel = new CorrelationCandidatesPanel();
        this.executionTreePanel = new ExecutionTreePanel();
        
        this.copilotResponseArea = new JTextArea(8, 80);
        this.copilotResponseArea.setEditable(false);
        this.copilotResponseArea.setLineWrap(true);
        this.copilotResponseArea.setWrapStyleWord(true);

        this.loadFromViewResultsButton = new JButton("Load from View Results Tree");
        this.loadFromViewResultsButton.addActionListener(e -> loadFromViewResultsTree());
        this.loadFromViewResultsButton.setEnabled(authManager.isAuthenticated());
        this.loadFromViewResultsButton.setToolTipText("Extract results directly from View Results Tree listener in test plan");

        this.clearResultsButton = new JButton("Clear Results");
        this.clearResultsButton.addActionListener(e -> clearResults());

        // Model selector
        this.modelSelector = new JComboBox<>(CopilotCorrelationAdvisor.AVAILABLE_MODELS);
        this.modelSelector.setSelectedItem(copilotAdvisor.getModel());
        this.modelSelector.addActionListener(e -> {
            String selected = (String) modelSelector.getSelectedItem();
            if (selected != null) {
                copilotAdvisor.setModel(selected);
            }
        });
        this.modelSelector.setPreferredSize(new Dimension(150, 25));

        this.analyzeWithCopilotButton = new JButton("Analyze with Copilot");
        this.analyzeWithCopilotButton.addActionListener(e -> analyzeWithCopilot());
        this.analyzeWithCopilotButton.setEnabled(authManager.isAuthenticated());

        this.applyCorrelationsButton = new JButton("Apply Correlations to JMX");
        this.applyCorrelationsButton.addActionListener(e -> applyCorrelations());
        this.applyCorrelationsButton.setEnabled(authManager.isAuthenticated());

        // Authentication
        this.authStatusLabel = new JLabel();
        this.authButton = new JButton();
        updateAuthUI();
        this.authButton.addActionListener(e -> handleAuthButtonClick());

        // Track test plan changes
        setupTestPlanChangeListener(guiPackage);

        // Build UI
        JPanel headerPanel = createHeaderPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("1. Load Results", createResultsTab());
        tabbedPane.addTab("2. Review & Apply", createCandidatesTab());

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(headerPanel, BorderLayout.NORTH);
        root.add(tabbedPane, BorderLayout.CENTER);

        dialog.setContentPane(root);
        dialog.setPreferredSize(new Dimension(1100, 700));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        append("JMeter Copilot Assistant - AI-Powered Auto-Correlation\n");
        append("═══════════════════════════════════════════════════════\n\n");
        append("Steps:\n");
        append("1. Run your test plan manually in JMeter (make sure you have a View Results Tree listener)\n");
        append("2. Click 'Load from View Results Tree' to capture the results\n");
        append("3. Review candidates and click 'Apply Correlations to JMX'\n");
        append("4. The AI will analyze and automatically modify your JMX file!\n\n");
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel authPanel = new JPanel();
        authPanel.setLayout(new BoxLayout(authPanel, BoxLayout.X_AXIS));
        authPanel.add(authStatusLabel);
        authPanel.add(Box.createHorizontalStrut(8));
        authPanel.add(authButton);

        headerPanel.add(authPanel, BorderLayout.EAST);
        return headerPanel;
    }

    private void updateAuthUI() {
        boolean authenticated = authManager.isAuthenticated();
        if (authenticated) {
            authStatusLabel.setText("✓ Signed in to Copilot");
            authStatusLabel.setForeground(new Color(0, 128, 0));
            authButton.setText("Sign Out");
        } else {
            authStatusLabel.setText("Sign in required");
            authStatusLabel.setForeground(new Color(180, 100, 0));
            authButton.setText("Sign In");
        }
        loadFromViewResultsButton.setEnabled(authenticated);
        analyzeWithCopilotButton.setEnabled(authenticated);
        applyCorrelationsButton.setEnabled(authenticated);
        modelSelector.setEnabled(authenticated);
    }

    private void handleAuthButtonClick() {
        if (authManager.isAuthenticated()) {
            authManager.logout();
            updateAuthUI();
            append("Signed out of Copilot.\n");
        } else {
            showAuthDialog();
        }
    }

    private void showAuthDialog() {
        if (authDialog != null && authDialog.isVisible()) {
            authDialog.toFront();
            return;
        }

        authDialog = new JDialog(dialog, "Sign in to GitHub Copilot", true);
        authDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        AuthenticationPanel authPanel = new AuthenticationPanel(authManager, authenticated -> {
            updateAuthUI();
            if (authenticated) {
                append("✅ Copilot authentication successful.\n");
                SwingUtilities.invokeLater(() -> {
                    if (authDialog != null) {
                        authDialog.dispose();
                        authDialog = null;
                    }
                });
            }
        });

        authDialog.setContentPane(authPanel);
        authDialog.setPreferredSize(new Dimension(500, 250));
        authDialog.pack();
        authDialog.setLocationRelativeTo(dialog);
        authDialog.setVisible(true);
    }

    private JPanel createResultsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Load Test Results"));

        JPanel loadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        loadPanel.add(new JLabel("Load results from View Results Tree listener:"));
        loadPanel.add(loadFromViewResultsButton);
        loadPanel.add(Box.createHorizontalStrut(20));
        loadPanel.add(clearResultsButton);

        controlPanel.add(loadPanel);

        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));

        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.add(controlPanel, BorderLayout.NORTH);
        leftPanel.add(logScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, executionTreePanel);
        splitPane.setResizeWeight(0.6);
        splitPane.setDividerLocation(600);

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createCandidatesTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel copilotPanel = new JPanel(new BorderLayout(8, 8));
        copilotPanel.setBorder(BorderFactory.createTitledBorder("Copilot AI Analysis"));

        JScrollPane copilotScroll = new JScrollPane(copilotResponseArea);

        JPanel copilotButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        copilotButtonPanel.add(new JLabel("Model:"));
        copilotButtonPanel.add(modelSelector);
        copilotButtonPanel.add(Box.createHorizontalStrut(10));
        copilotButtonPanel.add(analyzeWithCopilotButton);

        copilotPanel.add(copilotButtonPanel, BorderLayout.NORTH);
        copilotPanel.add(copilotScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, candidatesPanel, copilotPanel);
        splitPane.setResizeWeight(0.6);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(applyCorrelationsButton);

        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    public static void showDialog() {
        SwingUtilities.invokeLater(() -> {
            if (instance == null) {
                instance = new CopilotAssistantDialog();
            }
            instance.dialog.setVisible(true);
        });
    }

    /**
     * Load test results from View Results Tree listener(s) in the test plan.
     */
    private void loadFromViewResultsTree() {
        append("\nSearching for View Results Tree listeners with data...\n");
        
        new Thread(() -> {
            try {
                List<org.apache.jmeter.samplers.SampleResult> samples = viewResultsExtractor.extractFromAllViewResultsTrees();
                
                if (samples.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        append("No data found in View Results Tree listeners.\n\n");
                        append("Make sure you have:\n");
                        append("  1. A View Results Tree listener in your test plan\n");
                        append("  2. Run the test at least once\n\n");
                        JOptionPane.showMessageDialog(dialog,
                            "No data found in View Results Tree listeners.\n\n" +
                            "Make sure you have a View Results Tree listener in your test plan\n" +
                            "and have run the test at least once.",
                            "No Data Found", JOptionPane.INFORMATION_MESSAGE);
                    });
                    return;
                }
                
                RunCapture capture = viewResultsExtractor.convertToRunCapture(samples, 1);
                
                SwingUtilities.invokeLater(() -> {
                    runCaptures.clear();
                    executionTreePanel.clear();
                    candidatesPanel.setCandidates(new ArrayList<>());
                    
                    append("Found " + samples.size() + " samples in View Results Tree.\n\n");
                    
                    executionTreePanel.startRun(1, 1);
                    
                    for (SampleCapture sample : capture.getSamples()) {
                        boolean success = sample.getResponseCode() >= 200 && sample.getResponseCode() < 400;
                        executionTreePanel.addSamplerResult(sample.getLabel(), sample.getResponseCode(), 0, success);
                    }
                    
                    boolean runSuccess = capture.getSamples().stream()
                            .allMatch(s -> s.getResponseCode() >= 200 && s.getResponseCode() < 400);
                    executionTreePanel.finishRun(runSuccess);
                    
                    runCaptures.add(capture);
                    
                    // Show captured data summary
                    append("Captured data:\n");
                    for (SampleCapture sample : capture.getSamples()) {
                        int reqBodyLen = sample.getRequestBody() != null ? sample.getRequestBody().length() : 0;
                        int respBodyLen = sample.getResponseBody() != null ? sample.getResponseBody().length() : 0;
                        
                        append("  ✓ " + sample.getLabel() + " [" + sample.getMethod() + " " + sample.getResponseCode() + "]\n");
                        append("      Req: " + sample.getRequestHeaders().size() + " headers, " +
                               sample.getRequestParameters().size() + " params, " + reqBodyLen + " bytes\n");
                        append("      Resp: " + sample.getResponseHeaders().size() + " headers, " + respBodyLen + " bytes\n");
                    }
                    
                    append("\n--- Analyzing for correlation candidates ---\n");
                    
                    List<CorrelationCandidate> candidates = correlationAnalyzer.findFailedRequestCandidates(capture);
                    
                    candidatesPanel.setCandidates(candidates);
                    if (candidates.isEmpty()) {
                        append("No automatic candidates found.\n");
                        append("Switch to 'Review & Apply' tab and click 'Analyze with Copilot' for AI analysis.\n");
                    } else {
                        append("Found " + candidates.size() + " correlation candidates.\n");
                        append("Switch to 'Review & Apply' tab to review.\n");
                    }
                });
                
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    appendError(ex);
                    JOptionPane.showMessageDialog(dialog, 
                            "Error extracting data: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "view-results-extractor").start();
    }

    private void clearResults() {
        runCaptures.clear();
        executionTreePanel.clear();
        candidatesPanel.setCandidates(new ArrayList<>());
        copilotResponseArea.setText("");
        log.setText("");
        append("Results cleared.\n");
    }

    private void setupTestPlanChangeListener(GuiPackage guiPackage) {
        if (guiPackage == null) return;
        
        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        if (treeModel == null) return;

        currentTestPlanName = getTestPlanName(treeModel);

        treeModelListener = new TreeModelListener() {
            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                checkForTestPlanChange(guiPackage);
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                if (e.getTreePath().getPathCount() == 1) {
                    checkForTestPlanChange(guiPackage);
                }
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                if (e.getTreePath().getPathCount() == 1) {
                    checkForTestPlanChange(guiPackage);
                }
            }

            @Override
            public void treeNodesChanged(TreeModelEvent e) {
                if (e.getTreePath().getPathCount() <= 2) {
                    checkForTestPlanChange(guiPackage);
                }
            }
        };

        treeModel.addTreeModelListener(treeModelListener);

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (treeModel != null && treeModelListener != null) {
                    treeModel.removeTreeModelListener(treeModelListener);
                }
            }
        });
    }

    private void checkForTestPlanChange(GuiPackage guiPackage) {
        if (guiPackage == null) return;
        
        JMeterTreeModel treeModel = guiPackage.getTreeModel();
        String newTestPlanName = getTestPlanName(treeModel);

        if (newTestPlanName != null && !newTestPlanName.equals(currentTestPlanName)) {
            currentTestPlanName = newTestPlanName;
            SwingUtilities.invokeLater(() -> {
                if (!runCaptures.isEmpty() || candidatesPanel.getCandidateCount() > 0) {
                    append("\n⚠️ Test plan changed. Previous results cleared.\n\n");
                    clearResults();
                }
            });
        }
    }

    private String getTestPlanName(JMeterTreeModel treeModel) {
        if (treeModel == null) return null;
        JMeterTreeNode root = (JMeterTreeNode) treeModel.getRoot();
        if (root != null && root.getChildCount() > 0) {
            JMeterTreeNode testPlanNode = (JMeterTreeNode) root.getChildAt(0);
            if (testPlanNode != null && testPlanNode.getTestElement() != null) {
                return testPlanNode.getTestElement().getName();
            }
        }
        return null;
    }

    private void analyzeWithCopilot() {
        List<CorrelationCandidate> candidates = candidatesPanel.getSelectedCandidates();
        if (candidates.isEmpty() && runCaptures.size() >= 1) {
            candidates = correlationAnalyzer.findFailedRequestCandidates(runCaptures.get(0));
        }

        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(dialog,
                    "No candidates to analyze. Load results first.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        analyzeWithCopilotButton.setEnabled(false);
        String model = (String) modelSelector.getSelectedItem();
        copilotResponseArea.setText("Analyzing " + candidates.size() + " candidates with " + model + "...\n");

        List<CorrelationCandidate> finalCandidates = candidates;
        new Thread(() -> {
            try {
                String analysis = copilotAdvisor.analyzeCorrelationCandidates(finalCandidates);
                SwingUtilities.invokeLater(() -> {
                    copilotResponseArea.setText(analysis);
                    copilotResponseArea.setCaretPosition(0);
                    analyzeWithCopilotButton.setEnabled(authManager.isAuthenticated());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    copilotResponseArea.setText("❌ Error: " + ex.getMessage() + "\n\nTry signing out and back in.");
                    analyzeWithCopilotButton.setEnabled(authManager.isAuthenticated());
                });
            }
        }, "copilot-analysis").start();
    }

    private void applyCorrelations() {
        List<CorrelationCandidate> selected = candidatesPanel.getSelectedCandidates();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(dialog,
                    "No candidates selected. Check the 'Include' boxes for parameters you want to correlate.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Get the JMX file path
        GuiPackage guiPackage = GuiPackage.getInstance();
        String jmxPath = (guiPackage != null) ? guiPackage.getTestPlanFile() : null;
        
        if (jmxPath == null || jmxPath.isEmpty()) {
            JOptionPane.showMessageDialog(dialog,
                    "Please save the test plan first before applying correlations.",
                    "Save Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File jmxFile = new File(jmxPath);
        if (!jmxFile.exists()) {
            JOptionPane.showMessageDialog(dialog,
                    "JMX file not found: " + jmxPath + "\n\nPlease save the test plan first.",
                    "File Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Generate output filename
        String baseName = jmxFile.getName();
        String outputName;
        if (baseName.toLowerCase().endsWith(".jmx")) {
            outputName = baseName.substring(0, baseName.length() - 4) + "_correlated.jmx";
        } else {
            outputName = baseName + "_correlated.jmx";
        }
        File outputFile = new File(jmxFile.getParentFile(), outputName);

        // Confirm
        int confirm = JOptionPane.showConfirmDialog(dialog,
                "Generate AI-correlated test plan:\n\n" +
                "Input: " + jmxFile.getName() + "\n" +
                "Output: " + outputName + "\n" +
                "Candidates: " + selected.size() + "\n\n" +
                "The original file will NOT be modified.\n" +
                "A new file with correlations will be created.\n\n" +
                "Continue?",
                "Generate Correlated JMX",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        // Show progress dialog
        JDialog progressDialog = new JDialog(dialog, "Generating Correlated JMX", true);
        progressDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        JTextArea resultArea = new JTextArea(30, 100);
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setPreferredSize(new Dimension(1000, 600));
        
        final boolean[] processing = {true};
        final StringBuilder[] jmxOutput = {new StringBuilder()};
        final String finalJmxPath = jmxPath;
        final File finalOutputFile = outputFile;
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            processing[0] = false;
            progressDialog.dispose();
        });
        
        JButton closeButton = new JButton("Close");
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> progressDialog.dispose());
        
        JButton openButton = new JButton("Open Correlated File");
        openButton.setEnabled(false);
        openButton.addActionListener(e -> {
            progressDialog.dispose();
            // Show instructions to open the new file
            int open = JOptionPane.showConfirmDialog(dialog,
                "Correlated JMX file created:\n" + finalOutputFile.getAbsolutePath() + "\n\n" +
                "Would you like to open it now?\n\n" +
                "(This will close the current test plan)",
                "Open Correlated File?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            
            if (open == JOptionPane.YES_OPTION) {
                append("Opening: " + finalOutputFile.getName() + "\n");
                // The user will need to manually open - provide path
                JOptionPane.showMessageDialog(dialog,
                    "Please open the file manually:\n\n" +
                    "File > Open\n" +
                    finalOutputFile.getAbsolutePath(),
                    "Open File",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(openButton);
        buttonPanel.add(closeButton);
        
        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        progressDialog.setContentPane(contentPanel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(dialog);

        List<CorrelationCandidate> finalSelected = selected;
        
        new Thread(() -> {
            try {
                // Read the original JMX content
                String jmxContent = new String(java.nio.file.Files.readAllBytes(
                    new File(finalJmxPath).toPath()), java.nio.charset.StandardCharsets.UTF_8);
                
                // STEP 1: Copy the original file to the new _correlated.jmx file first
                // This gives us a starting point that the AI will modify
                java.nio.file.Files.copy(
                    new File(finalJmxPath).toPath(), 
                    finalOutputFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                SwingUtilities.invokeLater(() -> {
                    resultArea.setText("🤖 AI Auto-Correlation\n");
                    resultArea.append("═══════════════════════════════════════════════════════════════\n\n");
                    resultArea.append("Model: " + copilotAdvisor.getModel() + "\n");
                    resultArea.append("Input: " + finalJmxPath + "\n");
                    resultArea.append("Output: " + finalOutputFile.getAbsolutePath() + "\n");
                    resultArea.append("Candidates: " + finalSelected.size() + "\n\n");
                    resultArea.append("✓ Created copy: " + finalOutputFile.getName() + "\n");
                    resultArea.append("✓ AI is now modifying the copy with correlations...\n");
                    resultArea.append("═══════════════════════════════════════════════════════════════\n\n");
                });
                
                if (!processing[0]) return;
                
                // STEP 2: Call AI to generate the modified JMX content
                // The AI has the original content as context and outputs the modified version
                copilotAdvisor.generateCorrelatedJmx(finalSelected, jmxContent, chunk -> {
                    if (!processing[0]) return;
                    jmxOutput[0].append(chunk);
                    SwingUtilities.invokeLater(() -> {
                        resultArea.append(chunk);
                        // Auto-scroll to bottom
                        resultArea.setCaretPosition(resultArea.getDocument().getLength());
                    });
                });
                
                if (!processing[0]) return;
                
                // Get the generated content
                String generatedJmx = jmxOutput[0].toString().trim();
                
                // Clean up if wrapped in markdown code blocks
                if (generatedJmx.startsWith("```")) {
                    int start = generatedJmx.indexOf('\n') + 1;
                    int end = generatedJmx.lastIndexOf("```");
                    if (end > start) {
                        generatedJmx = generatedJmx.substring(start, end).trim();
                    }
                }
                
                // Validate it looks like JMX
                if (!generatedJmx.contains("<?xml") || !generatedJmx.contains("<jmeterTestPlan")) {
                    SwingUtilities.invokeLater(() -> {
                        resultArea.append("\n\n═══════════════════════════════════════════════════════════════\n");
                        resultArea.append("⚠️ Warning: Output may not be valid JMX.\n");
                        resultArea.append("The AI response doesn't appear to be a complete JMX file.\n");
                        cancelButton.setVisible(false);
                        closeButton.setEnabled(true);
                    });
                    return;
                }
                
                // Write to output file
                final String finalGeneratedJmx = generatedJmx;
                java.nio.file.Files.write(finalOutputFile.toPath(), 
                    generatedJmx.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
                SwingUtilities.invokeLater(() -> {
                    resultArea.append("\n\n═══════════════════════════════════════════════════════════════\n");
                    resultArea.append("✅ SUCCESS!\n\n");
                    resultArea.append("Correlated JMX file created:\n");
                    resultArea.append(finalOutputFile.getAbsolutePath() + "\n\n");
                    resultArea.append("File size: " + finalGeneratedJmx.length() + " bytes\n\n");
                    resultArea.append("Click 'Open Correlated File' to open it in JMeter.\n");
                    resultArea.setCaretPosition(0);
                    
                    cancelButton.setVisible(false);
                    closeButton.setEnabled(true);
                    openButton.setEnabled(true);
                });
                
            } catch (Exception ex) {
                if (!processing[0]) return;
                final String errorMsg = ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    resultArea.append("\n\n═══════════════════════════════════════════════════════════════\n");
                    resultArea.append("❌ Error: " + errorMsg + "\n");
                    cancelButton.setVisible(false);
                    closeButton.setEnabled(true);
                });
                ex.printStackTrace();
            }
        }, "generate-correlated-jmx").start();

        progressDialog.setVisible(true);
    }

    private void append(String msg) {
        SwingUtilities.invokeLater(() -> {
            log.append(msg);
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void appendError(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n❌ ERROR: ").append(ex.getMessage()).append("\n");
        
        Throwable cause = ex.getCause();
        if (cause != null) {
            sb.append("  Caused by: ").append(cause.getMessage()).append("\n");
        }
        
        append(sb.toString());
        ex.printStackTrace();
    }
}
