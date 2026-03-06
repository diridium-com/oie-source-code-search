// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.converters.ObjectXMLSerializer;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-modal search dialog with results displayed in a JTree.
 */
public class SourceCodeSearchDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeSearchDialog.class);
    private static final int RESULT_WARNING_THRESHOLD = 5000;

    private JTextField txtQuery;
    private JCheckBox chkCaseSensitive;
    private JCheckBox chkRegex;
    private JCheckBox chkChannels;
    private JCheckBox chkCodeTemplates;
    private JCheckBox chkGlobalScripts;
    private JCheckBox chkMessageTemplates;
    private JCheckBox chkConnectorProperties;
    private JCheckBox chkSelectedOnly;
    private JButton btnSearch;
    private JButton btnExport;
    private JLabel lblStatus;
    private JTree resultTree;
    private DefaultMutableTreeNode rootNode;

    private List<String> selectedChannelIds;
    private List<SearchMatch> lastResults;
    private String lastQuery;
    private boolean lastCaseSensitive;
    private boolean lastRegex;
    private boolean lastSearchChannels;
    private boolean lastSearchCodeTemplates;
    private boolean lastSearchGlobalScripts;
    private boolean lastSearchMessageTemplates;
    private boolean lastSearchConnectorProperties;
    private SourceCodeSearchServletInterface servlet;

    public SourceCodeSearchDialog(JFrame parent, List<String> selectedChannelIds) {
        super(parent, "Source Code Search", false); // non-modal
        this.selectedChannelIds = selectedChannelIds;

        ObjectXMLSerializer.getInstance().allowTypes(Collections.emptyList(),
                Collections.singletonList(SearchMatch.class.getPackage().getName() + ".**"),
                Collections.emptyList());

        initComponents();

        setSize(800, 600);
        setLocationRelativeTo(parent);

        // Escape key closes dialog
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    public void updateSelectedChannels(List<String> selectedChannelIds) {
        this.selectedChannelIds = selectedChannelIds;
        chkSelectedOnly.setEnabled(selectedChannelIds != null && !selectedChannelIds.isEmpty());
    }

    /**
     * Configure the dialog for channel-scoped search (from the channel editor).
     * Locks Channels on and "Selected Channels Only" checked and greyed out.
     */
    public void setChannelScoped(boolean scoped) {
        chkChannels.setSelected(true);
        chkChannels.setEnabled(!scoped);
        chkSelectedOnly.setSelected(scoped);
        chkSelectedOnly.setEnabled(!scoped);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // Top panel: search controls
        JPanel searchPanel = new JPanel(new MigLayout("insets 5, fillx", "[][grow][]", ""));

        searchPanel.add(new JLabel("Search:"));
        txtQuery = new JTextField();
        txtQuery.addActionListener(e -> performSearch());
        searchPanel.add(txtQuery, "growx");

        btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> performSearch());
        searchPanel.add(btnSearch, "wrap");

        // Options row
        chkCaseSensitive = new JCheckBox("Case Sensitive");
        chkRegex = new JCheckBox("Regex");
        searchPanel.add(chkCaseSensitive, "skip 1, split 2");
        searchPanel.add(chkRegex, "wrap");

        // Scope row
        chkChannels = new JCheckBox("Channels", true);
        chkCodeTemplates = new JCheckBox("Code Templates", true);
        chkGlobalScripts = new JCheckBox("Global Scripts", true);
        chkMessageTemplates = new JCheckBox("Message Templates");
        chkConnectorProperties = new JCheckBox("Connector Properties", true);
        chkSelectedOnly = new JCheckBox("Selected Channels Only");
        chkSelectedOnly.setEnabled(selectedChannelIds != null && !selectedChannelIds.isEmpty());

        searchPanel.add(chkChannels, "skip 1, split 6");
        searchPanel.add(chkCodeTemplates);
        searchPanel.add(chkGlobalScripts);
        searchPanel.add(chkMessageTemplates);
        searchPanel.add(chkConnectorProperties);
        searchPanel.add(chkSelectedOnly, "wrap");

        add(searchPanel, BorderLayout.NORTH);

        // Center: result tree
        rootNode = new DefaultMutableTreeNode("Search Results");
        resultTree = new JTree(rootNode);
        resultTree.setRootVisible(false);
        resultTree.setShowsRootHandles(true);
        JScrollPane scrollPane = new JScrollPane(resultTree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom: status bar and close button
        JPanel bottomPanel = new JPanel(new BorderLayout());
        lblStatus = new JLabel(" ");
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(lblStatus);
        bottomPanel.add(statusPanel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnExport = new JButton("Export");
        btnExport.setEnabled(false);
        btnExport.addActionListener(e -> exportResults());
        buttonPanel.add(btnExport);
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        buttonPanel.add(btnClose);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void performSearch() {
        String query = txtQuery.getText();
        if (query == null || query.isEmpty()) {
            return;
        }

        // Capture all UI state on the EDT before launching background work
        final boolean caseSensitive = chkCaseSensitive.isSelected();
        final boolean regex = chkRegex.isSelected();
        final boolean searchChannels = chkChannels.isSelected();
        final boolean searchCodeTemplates = chkCodeTemplates.isSelected();
        final boolean searchGlobalScripts = chkGlobalScripts.isSelected();
        final boolean searchMessageTemplates = chkMessageTemplates.isSelected();
        final boolean searchConnectorProperties = chkConnectorProperties.isSelected();

        String channelIdsCsv = "";
        if (chkSelectedOnly.isSelected() && selectedChannelIds != null && !selectedChannelIds.isEmpty()) {
            channelIdsCsv = String.join(",", selectedChannelIds);
        }
        final String finalChannelIdsCsv = channelIdsCsv;

        btnSearch.setEnabled(false);
        btnExport.setEnabled(false);
        lastResults = null;
        lblStatus.setText("Counting matches...");

        // Clear previous results
        rootNode.removeAllChildren();
        ((DefaultTreeModel) resultTree.getModel()).reload();

        // Phase 1: Count matches
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                ensureServlet();
                return servlet.count(query, caseSensitive, regex,
                        finalChannelIdsCsv, searchChannels, searchCodeTemplates, searchGlobalScripts,
                        searchMessageTemplates, searchConnectorProperties);
            }

            @Override
            protected void done() {
                try {
                    int matchCount = get();

                    if (matchCount == 0) {
                        lblStatus.setText("No matches found.");
                        btnSearch.setEnabled(true);
                        return;
                    }

                    // Warn if above threshold
                    if (matchCount >= RESULT_WARNING_THRESHOLD) {
                        int choice = JOptionPane.showConfirmDialog(
                                SourceCodeSearchDialog.this,
                                "Found " + matchCount + " matches. This may take a moment to display.\n"
                                        + "Continue, or refine your search?",
                                "Large Result Set",
                                JOptionPane.OK_CANCEL_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                        if (choice != JOptionPane.OK_OPTION) {
                            lblStatus.setText(matchCount + " matches found. Search cancelled.");
                            btnSearch.setEnabled(true);
                            return;
                        }
                    }

                    // Phase 2: Fetch full results
                    fetchResults(query, caseSensitive, regex, finalChannelIdsCsv,
                            searchChannels, searchCodeTemplates, searchGlobalScripts,
                            searchMessageTemplates, searchConnectorProperties);
                } catch (Exception e) {
                    log.error("Count failed", e);
                    lblStatus.setText("Search failed: " + e.getMessage());
                    PlatformUI.MIRTH_FRAME.alertThrowable(SourceCodeSearchDialog.this, e);
                    btnSearch.setEnabled(true);
                }
            }
        }.execute();
    }

    private void fetchResults(String query, boolean caseSensitive, boolean regex,
                               String channelIdsCsv, boolean searchChannels,
                               boolean searchCodeTemplates, boolean searchGlobalScripts,
                               boolean searchMessageTemplates, boolean searchConnectorProperties) {
        lblStatus.setText("Searching...");

        new SwingWorker<List<SearchMatch>, Void>() {
            @Override
            protected List<SearchMatch> doInBackground() throws Exception {
                return servlet.search(query, caseSensitive, regex,
                        channelIdsCsv, searchChannels, searchCodeTemplates, searchGlobalScripts,
                        searchMessageTemplates, searchConnectorProperties);
            }

            @Override
            protected void done() {
                try {
                    List<SearchMatch> matches = get();
                    lastResults = matches;
                    lastQuery = query;
                    lastCaseSensitive = caseSensitive;
                    lastRegex = regex;
                    lastSearchChannels = searchChannels;
                    lastSearchCodeTemplates = searchCodeTemplates;
                    lastSearchGlobalScripts = searchGlobalScripts;
                    lastSearchMessageTemplates = searchMessageTemplates;
                    lastSearchConnectorProperties = searchConnectorProperties;
                    int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    String patternStr = regex ? query : Pattern.quote(query);
                    Pattern highlightPattern = Pattern.compile(patternStr, flags);
                    buildResultTree(matches, highlightPattern);
                    btnExport.setEnabled(!matches.isEmpty());
                    lblStatus.setText(matches.size() + " match(es) found.");
                } catch (Exception e) {
                    log.error("Search failed", e);
                    lblStatus.setText("Search failed: " + e.getMessage());
                    PlatformUI.MIRTH_FRAME.alertThrowable(SourceCodeSearchDialog.this, e);
                } finally {
                    btnSearch.setEnabled(true);
                }
            }
        }.execute();
    }

    private void ensureServlet() {
        if (servlet == null) {
            servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(SourceCodeSearchServletInterface.class);
        }
    }

    private void buildResultTree(List<SearchMatch> matches, Pattern highlightPattern) {
        rootNode.removeAllChildren();

        DefaultMutableTreeNode channelsNode = null;
        DefaultMutableTreeNode codeTemplatesNode = null;
        DefaultMutableTreeNode globalScriptsNode = null;

        for (SearchMatch match : matches) {
            String lineLabel = highlightMatch(
                    "Line " + match.getLineNumber() + ": " + match.getLineText(),
                    match.getLineText(), highlightPattern);

            switch (match.getGroupType()) {
                case "CHANNEL": {
                    if (channelsNode == null) {
                        channelsNode = new DefaultMutableTreeNode("Channels");
                        rootNode.add(channelsNode);
                    }
                    DefaultMutableTreeNode chNode = findOrCreateChild(channelsNode, match.getChannelName());
                    DefaultMutableTreeNode locNode = findOrCreateChild(chNode, match.getLocation());
                    locNode.add(new DefaultMutableTreeNode(lineLabel));
                    break;
                }
                case "CODE_TEMPLATE": {
                    if (codeTemplatesNode == null) {
                        codeTemplatesNode = new DefaultMutableTreeNode("Code Templates");
                        rootNode.add(codeTemplatesNode);
                    }
                    DefaultMutableTreeNode tmplNode = findOrCreateChild(codeTemplatesNode, match.getChannelName());
                    tmplNode.add(new DefaultMutableTreeNode(lineLabel));
                    break;
                }
                case "GLOBAL_SCRIPT": {
                    if (globalScriptsNode == null) {
                        globalScriptsNode = new DefaultMutableTreeNode("Global Scripts");
                        rootNode.add(globalScriptsNode);
                    }
                    DefaultMutableTreeNode scriptNode = findOrCreateChild(globalScriptsNode, match.getChannelName());
                    scriptNode.add(new DefaultMutableTreeNode(lineLabel));
                    break;
                }
            }
        }

        DefaultTreeModel model = (DefaultTreeModel) resultTree.getModel();
        model.reload();
        expandAll();
    }

    private DefaultMutableTreeNode findOrCreateChild(DefaultMutableTreeNode parent, String name) {
        Enumeration<?> children = parent.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) children.nextElement();
            if (name.equals(child.getUserObject())) {
                return child;
            }
        }
        DefaultMutableTreeNode newChild = new DefaultMutableTreeNode(name);
        parent.add(newChild);
        return newChild;
    }

    private String highlightMatch(String fullLabel, String lineText, Pattern pattern) {
        if (lineText == null || lineText.isEmpty()) {
            return fullLabel;
        }
        Matcher matcher = pattern.matcher(lineText);
        if (!matcher.find()) {
            return fullLabel;
        }

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        matcher.reset();
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                break;
            }
            sb.append(escapeHtml(lineText.substring(lastEnd, matcher.start())));
            sb.append("<span style=\"background-color: #FFFF00;\">");
            sb.append(escapeHtml(matcher.group()));
            sb.append("</span>");
            lastEnd = matcher.end();
        }
        sb.append(escapeHtml(lineText.substring(lastEnd)));

        String prefix = fullLabel.substring(0, fullLabel.length() - lineText.length());
        return "<html>" + escapeHtml(prefix) + sb + "</html>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private void expandAll() {
        for (int i = 0; i < resultTree.getRowCount(); i++) {
            resultTree.expandRow(i);
        }
    }

    private void exportResults() {
        if (lastResults == null || lastResults.isEmpty()) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter jsonFilter = new FileNameExtensionFilter("JSON files (*.json)", "json");
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("CSV files (*.csv)", "csv");
        fileChooser.addChoosableFileFilter(jsonFilter);
        fileChooser.addChoosableFileFilter(csvFilter);
        fileChooser.setFileFilter(jsonFilter);
        fileChooser.setSelectedFile(new File("search-results.json"));

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = fileChooser.getSelectedFile();
        String filterDesc = fileChooser.getFileFilter().getDescription();
        boolean csv = filterDesc.contains("CSV");

        // Append extension if missing
        if (csv && !file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getAbsolutePath() + ".csv");
        } else if (!csv && !file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        try {
            if (csv) {
                exportCsv(file);
            } else {
                exportJson(file);
            }
            lblStatus.setText("Exported " + lastResults.size() + " result(s) to " + file.getName());
        } catch (IOException e) {
            log.error("Export failed", e);
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportJson(File file) throws IOException {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("query", lastQuery);
        export.put("caseSensitive", lastCaseSensitive);
        export.put("regex", lastRegex);
        export.put("searchChannels", lastSearchChannels);
        export.put("searchCodeTemplates", lastSearchCodeTemplates);
        export.put("searchGlobalScripts", lastSearchGlobalScripts);
        export.put("searchMessageTemplates", lastSearchMessageTemplates);
        export.put("searchConnectorProperties", lastSearchConnectorProperties);
        export.put("resultCount", lastResults.size());
        export.put("results", lastResults);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(file, export);
    }

    private void exportCsv(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writeCsvRow(writer, "groupType", "channelId", "channelName", "location", "lineNumber", "lineText");
            for (SearchMatch match : lastResults) {
                writeCsvRow(writer,
                        match.getGroupType(),
                        match.getChannelId(),
                        match.getChannelName(),
                        match.getLocation(),
                        String.valueOf(match.getLineNumber()),
                        match.getLineText());
            }
        }
    }

    private void writeCsvRow(BufferedWriter writer, String... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values[i]));
        }
        writer.newLine();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
