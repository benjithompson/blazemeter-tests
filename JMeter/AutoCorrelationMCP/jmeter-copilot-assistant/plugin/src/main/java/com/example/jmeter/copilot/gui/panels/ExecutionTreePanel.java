package com.example.jmeter.copilot.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * A tree view panel that displays test execution progress with pass/fail status.
 */
public class ExecutionTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private DefaultMutableTreeNode currentRunNode;

    public ExecutionTreePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Test Execution Progress"));

        rootNode = new DefaultMutableTreeNode("Test Runs");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new SamplerTreeCellRenderer());

        JScrollPane scrollPane = new JScrollPane(tree);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Clear all runs from the tree.
     */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            rootNode.removeAllChildren();
            treeModel.reload();
            currentRunNode = null;
        });
    }

    /**
     * Start a new run in the tree.
     */
    public void startRun(int runNumber, int totalRuns) {
        SwingUtilities.invokeLater(() -> {
            currentRunNode = new DefaultMutableTreeNode(
                    new RunInfo("Run " + runNumber + " of " + totalRuns, RunStatus.RUNNING));
            rootNode.add(currentRunNode);
            treeModel.reload();
            tree.expandPath(new TreePath(currentRunNode.getPath()));
        });
    }

    /**
     * Mark the current run as complete.
     */
    public void finishRun(boolean success) {
        SwingUtilities.invokeLater(() -> {
            if (currentRunNode != null) {
                RunInfo info = (RunInfo) currentRunNode.getUserObject();
                info.status = success ? RunStatus.PASSED : RunStatus.FAILED;
                treeModel.nodeChanged(currentRunNode);
            }
        });
    }

    /**
     * Add a sampler result to the current run.
     */
    public void addSamplerResult(String label, int responseCode, long responseTime, boolean success) {
        SwingUtilities.invokeLater(() -> {
            if (currentRunNode == null) return;

            SamplerInfo samplerInfo = new SamplerInfo(label, responseCode, responseTime, success);
            DefaultMutableTreeNode samplerNode = new DefaultMutableTreeNode(samplerInfo);
            currentRunNode.add(samplerNode);
            treeModel.nodesWereInserted(currentRunNode, new int[]{currentRunNode.getChildCount() - 1});
            
            // Auto-scroll to show the new node
            tree.scrollPathToVisible(new TreePath(samplerNode.getPath()));
        });
    }

    /**
     * Add a "running" placeholder for a sampler.
     */
    public void addSamplerStarted(String label) {
        SwingUtilities.invokeLater(() -> {
            if (currentRunNode == null) return;

            SamplerInfo samplerInfo = new SamplerInfo(label, -1, -1, false);
            samplerInfo.running = true;
            DefaultMutableTreeNode samplerNode = new DefaultMutableTreeNode(samplerInfo);
            currentRunNode.add(samplerNode);
            treeModel.nodesWereInserted(currentRunNode, new int[]{currentRunNode.getChildCount() - 1});
            tree.scrollPathToVisible(new TreePath(samplerNode.getPath()));
        });
    }

    // Data classes for tree nodes
    private enum RunStatus {
        RUNNING, PASSED, FAILED
    }

    private static class RunInfo {
        String name;
        RunStatus status;

        RunInfo(String name, RunStatus status) {
            this.name = name;
            this.status = status;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class SamplerInfo {
        String label;
        int responseCode;
        long responseTime;
        boolean success;
        boolean running;

        SamplerInfo(String label, int responseCode, long responseTime, boolean success) {
            this.label = label;
            this.responseCode = responseCode;
            this.responseTime = responseTime;
            this.success = success;
            this.running = false;
        }

        @Override
        public String toString() {
            if (running) {
                return label + " (running...)";
            }
            return label + " [" + responseCode + "] - " + responseTime + "ms";
        }
    }

    // Custom renderer for the tree
    private static class SamplerTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final Color PASS_COLOR = new Color(0, 128, 0);
        private static final Color FAIL_COLOR = new Color(200, 0, 0);
        private static final Color RUNNING_COLOR = new Color(100, 100, 100);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();

                if (userObject instanceof RunInfo) {
                    RunInfo info = (RunInfo) userObject;
                    setFont(getFont().deriveFont(Font.BOLD));
                    switch (info.status) {
                        case RUNNING:
                            setText("⋯ " + info.name);
                            setForeground(RUNNING_COLOR);
                            break;
                        case PASSED:
                            setText("✓ " + info.name);
                            setForeground(PASS_COLOR);
                            break;
                        case FAILED:
                            setText("✗ " + info.name);
                            setForeground(FAIL_COLOR);
                            break;
                    }
                } else if (userObject instanceof SamplerInfo) {
                    SamplerInfo info = (SamplerInfo) userObject;
                    setFont(getFont().deriveFont(Font.PLAIN));
                    if (info.running) {
                        setText("⋯ " + info.label + " (running...)");
                        setForeground(RUNNING_COLOR);
                    } else if (info.success) {
                        setText("✓ " + info.toString());
                        setForeground(PASS_COLOR);
                    } else {
                        setText("✗ " + info.toString());
                        setForeground(FAIL_COLOR);
                    }
                }
            }

            return this;
        }
    }
}
