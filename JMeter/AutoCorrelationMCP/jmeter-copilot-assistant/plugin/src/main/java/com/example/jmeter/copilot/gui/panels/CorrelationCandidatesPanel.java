package com.example.jmeter.copilot.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import com.example.jmeter.copilot.core.CorrelationCandidate;

/**
 * Panel displaying correlation candidates with checkboxes for selection.
 */
public class CorrelationCandidatesPanel extends JPanel {

    private final JTable table;
    private final CandidateTableModel tableModel;
    private final JLabel summaryLabel;
    private final JButton selectAllRequired;
    private final JButton deselectAll;

    public CorrelationCandidatesPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Correlation Candidates"));

        tableModel = new CandidateTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Configure columns
        table.getColumnModel().getColumn(0).setPreferredWidth(40);   // Include checkbox
        table.getColumnModel().getColumn(1).setPreferredWidth(150);  // Parameter
        table.getColumnModel().getColumn(2).setPreferredWidth(80);   // Location
        table.getColumnModel().getColumn(3).setPreferredWidth(150);  // Request
        table.getColumnModel().getColumn(4).setPreferredWidth(80);   // Confidence
        table.getColumnModel().getColumn(5).setPreferredWidth(150);  // Source
        table.getColumnModel().getColumn(6).setPreferredWidth(250);  // Reason

        // Color code confidence column
        table.getColumnModel().getColumn(4).setCellRenderer(new ConfidenceRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(900, 300));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectAllRequired = new JButton("Select All Required");
        selectAllRequired.addActionListener(e -> selectByConfidence(CorrelationCandidate.Confidence.HIGH));
        
        JButton selectRecommended = new JButton("Select Recommended");
        selectRecommended.addActionListener(e -> {
            selectByConfidence(CorrelationCandidate.Confidence.HIGH);
            selectByConfidence(CorrelationCandidate.Confidence.MEDIUM);
        });
        
        deselectAll = new JButton("Deselect All");
        deselectAll.addActionListener(e -> {
            for (CorrelationCandidate c : tableModel.getCandidates()) {
                c.setSelected(false);
            }
            tableModel.fireTableDataChanged();
            updateSummary();
        });

        buttonPanel.add(selectAllRequired);
        buttonPanel.add(selectRecommended);
        buttonPanel.add(deselectAll);

        // Summary label
        summaryLabel = new JLabel("No candidates found yet. Run analysis first.");

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buttonPanel, BorderLayout.CENTER);
        topPanel.add(summaryLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setCandidates(List<CorrelationCandidate> candidates) {
        tableModel.setCandidates(candidates);
        updateSummary();
    }

    public List<CorrelationCandidate> getSelectedCandidates() {
        List<CorrelationCandidate> selected = new ArrayList<>();
        for (CorrelationCandidate c : tableModel.getCandidates()) {
            if (c.isSelected()) {
                selected.add(c);
            }
        }
        return selected;
    }

    public int getCandidateCount() {
        return tableModel.getCandidates().size();
    }

    private void selectByConfidence(CorrelationCandidate.Confidence confidence) {
        for (CorrelationCandidate c : tableModel.getCandidates()) {
            if (c.getConfidence() == confidence) {
                c.setSelected(true);
            }
        }
        tableModel.fireTableDataChanged();
        updateSummary();
    }

    private void updateSummary() {
        List<CorrelationCandidate> candidates = tableModel.getCandidates();
        int total = candidates.size();
        int selected = 0;
        int high = 0, medium = 0, low = 0;

        for (CorrelationCandidate c : candidates) {
            if (c.isSelected()) selected++;
            switch (c.getConfidence()) {
                case HIGH: high++; break;
                case MEDIUM: medium++; break;
                case LOW: low++; break;
            }
        }

        summaryLabel.setText(String.format(
                "Total: %d candidates | Selected: %d | High confidence: %d | Medium: %d | Low: %d",
                total, selected, high, medium, low));
    }

    // Table Model
    private static class CandidateTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Include", "Parameter", "Location", "Request", "Confidence", "Source", "Reason"};
        private List<CorrelationCandidate> candidates = new ArrayList<>();

        public void setCandidates(List<CorrelationCandidate> candidates) {
            this.candidates = candidates != null ? candidates : new ArrayList<>();
            fireTableDataChanged();
        }

        public List<CorrelationCandidate> getCandidates() {
            return candidates;
        }

        @Override
        public int getRowCount() {
            return candidates.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Only checkbox is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CorrelationCandidate c = candidates.get(rowIndex);
            switch (columnIndex) {
                case 0: return c.isSelected();
                case 1: return c.getParameterName();
                case 2: return c.getLocation().toString();
                case 3: return c.getSampleLabel();
                case 4: return c.getConfidence().toString();
                case 5: return c.getSourceLabel() != null ? c.getSourceLabel() : "(not found)";
                case 6: return c.getReason();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                candidates.get(rowIndex).setSelected((Boolean) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }

    // Custom renderer for confidence column
    private static class ConfidenceRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, 
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (!isSelected) {
                String conf = value.toString();
                switch (conf) {
                    case "HIGH":
                        c.setBackground(new Color(255, 200, 200)); // Light red
                        c.setForeground(Color.BLACK);
                        break;
                    case "MEDIUM":
                        c.setBackground(new Color(255, 255, 200)); // Light yellow
                        c.setForeground(Color.BLACK);
                        break;
                    case "LOW":
                        c.setBackground(new Color(200, 255, 200)); // Light green
                        c.setForeground(Color.BLACK);
                        break;
                    default:
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                }
            }
            return c;
        }
    }
}
