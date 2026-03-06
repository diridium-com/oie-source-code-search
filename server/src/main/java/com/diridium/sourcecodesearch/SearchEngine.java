// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mirth.connect.model.Channel;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.FilterTransformerElement;
import com.mirth.connect.model.Transformer;
import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.server.controllers.ChannelController;
import com.mirth.connect.server.controllers.CodeTemplateController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;

public class SearchEngine {

    private static final Logger log = LoggerFactory.getLogger(SearchEngine.class);
    private static final long REGEX_TIMEOUT_MS = 5000;

    @FunctionalInterface
    private interface ScriptHandler {
        void handle(String groupType, String channelId, String channelName,
                    String location, String scriptContent);
    }

    private final ChannelController channelController;
    private final CodeTemplateController codeTemplateController;
    private final ScriptController scriptController;

    public SearchEngine() {
        this.channelController = ChannelController.getInstance();
        this.codeTemplateController = ControllerFactory.getFactory().createCodeTemplateController();
        this.scriptController = ScriptController.getInstance();
    }

    public int count(String query, boolean caseSensitive, boolean regex,
                     String channelIdsCsv, boolean searchChannels,
                     boolean searchCodeTemplates, boolean searchGlobalScripts,
                     boolean searchMessageTemplates, boolean searchConnectorProperties) {
        Pattern pattern = buildPattern(query, caseSensitive, regex);
        AtomicInteger counter = new AtomicInteger();

        ScriptHandler handler = (groupType, chId, chName, location, script) ->
                countMatches(pattern, counter, script);

        visitAll(handler, pattern, channelIdsCsv, searchChannels,
                searchCodeTemplates, searchGlobalScripts, searchMessageTemplates,
                searchConnectorProperties);

        return counter.get();
    }

    public List<SearchMatch> search(String query, boolean caseSensitive, boolean regex,
                                     String channelIdsCsv, boolean searchChannels,
                                     boolean searchCodeTemplates, boolean searchGlobalScripts,
                                     boolean searchMessageTemplates, boolean searchConnectorProperties) {
        Pattern pattern = buildPattern(query, caseSensitive, regex);
        List<SearchMatch> results = new ArrayList<>();

        ScriptHandler handler = (groupType, chId, chName, location, script) ->
                findMatches(pattern, results, groupType, chId, chName, location, script);

        visitAll(handler, pattern, channelIdsCsv, searchChannels,
                searchCodeTemplates, searchGlobalScripts, searchMessageTemplates,
                searchConnectorProperties);

        return results;
    }

    private Pattern buildPattern(String query, boolean caseSensitive, boolean regex) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        String patternStr = regex ? query : Pattern.quote(query);
        try {
            return Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex: " + e.getDescription(), e);
        }
    }

    // ========================
    // Unified traversal
    // ========================

    private void visitAll(ScriptHandler handler, Pattern pattern, String channelIdsCsv,
                          boolean searchChannels, boolean searchCodeTemplates,
                          boolean searchGlobalScripts, boolean searchMessageTemplates,
                          boolean searchConnectorProperties) {
        if (searchGlobalScripts) {
            visitGlobalScripts(handler);
        }
        if (searchCodeTemplates) {
            visitCodeTemplates(handler);
        }
        if (searchChannels || searchMessageTemplates || searchConnectorProperties) {
            visitChannels(handler, channelIdsCsv, searchChannels, searchMessageTemplates,
                    searchConnectorProperties);
        }
    }

    // ========================
    // Channel traversal
    // ========================

    private void visitChannels(ScriptHandler handler, String channelIdsCsv,
                               boolean searchScripts, boolean searchMessageTemplates,
                               boolean searchConnectorProperties) {
        try {
            for (Channel channel : getChannels(channelIdsCsv)) {
                visitChannel(handler, channel, searchScripts, searchMessageTemplates,
                        searchConnectorProperties);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve channels", e);
        }
    }

    private void visitChannel(ScriptHandler handler, Channel channel,
                              boolean searchScripts, boolean searchMessageTemplates,
                              boolean searchConnectorProperties) {
        String chId = channel.getId();
        String chName = channel.getName();

        if (searchScripts) {
            handler.handle("CHANNEL", chId, chName, "Preprocessing Script", channel.getPreprocessingScript());
            handler.handle("CHANNEL", chId, chName, "Postprocessing Script", channel.getPostprocessingScript());
            handler.handle("CHANNEL", chId, chName, "Deploy Script", channel.getDeployScript());
            handler.handle("CHANNEL", chId, chName, "Undeploy Script", channel.getUndeployScript());
        }

        Connector source = channel.getSourceConnector();
        if (source != null) {
            visitConnector(handler, source, "Source", chId, chName, searchScripts,
                    searchMessageTemplates, searchConnectorProperties);
        }

        if (channel.getDestinationConnectors() != null) {
            for (Connector dest : channel.getDestinationConnectors()) {
                String destLabel = "Dest " + dest.getMetaDataId() + ": " + dest.getName();
                visitConnector(handler, dest, destLabel, chId, chName, searchScripts,
                        searchMessageTemplates, searchConnectorProperties);
            }
        }
    }

    private void visitConnector(ScriptHandler handler, Connector connector, String connectorLabel,
                                String chId, String chName,
                                boolean searchScripts, boolean searchMessageTemplates,
                                boolean searchConnectorProperties) {
        Transformer transformer = connector.getTransformer();
        if (transformer != null) {
            if (searchMessageTemplates) {
                handler.handle("CHANNEL", chId, chName,
                        connectorLabel + " > Inbound Template", transformer.getInboundTemplate());
                handler.handle("CHANNEL", chId, chName,
                        connectorLabel + " > Outbound Template", transformer.getOutboundTemplate());
            }
            if (searchScripts) {
                visitFilterTransformerElements(handler, transformer.getElements(),
                        connectorLabel + " > Transformer", chId, chName);
            }
        }

        Filter filter = connector.getFilter();
        if (filter != null && searchScripts) {
            visitFilterTransformerElements(handler, filter.getElements(),
                    connectorLabel + " > Filter", chId, chName);
        }

        Transformer responseTransformer = connector.getResponseTransformer();
        if (responseTransformer != null) {
            if (searchMessageTemplates) {
                handler.handle("CHANNEL", chId, chName,
                        connectorLabel + " > Response Inbound Template", responseTransformer.getInboundTemplate());
                handler.handle("CHANNEL", chId, chName,
                        connectorLabel + " > Response Outbound Template", responseTransformer.getOutboundTemplate());
            }
            if (searchScripts) {
                visitFilterTransformerElements(handler, responseTransformer.getElements(),
                        connectorLabel + " > Response Transformer", chId, chName);
            }
        }

        if (searchConnectorProperties && connector.getProperties() != null) {
            try {
                String propertiesXml = ObjectXMLSerializer.getInstance().serialize(connector.getProperties());
                handler.handle("CHANNEL", chId, chName,
                        connectorLabel + " > Connector Properties", propertiesXml);
            } catch (Exception e) {
                log.debug("Could not serialize connector properties for {} in channel {}", connectorLabel, chName, e);
            }
        }
    }

    private void visitFilterTransformerElements(ScriptHandler handler,
                                                 List<? extends FilterTransformerElement> elements,
                                                 String parentPath, String chId, String chName) {
        if (elements == null) {
            return;
        }
        for (FilterTransformerElement element : elements) {
            String stepLabel = buildStepLabel(element, parentPath);
            try {
                handler.handle("CHANNEL", chId, chName, stepLabel, element.getScript(false));
            } catch (Exception e) {
                log.debug("Could not get script from element {} in channel {}", stepLabel, chName, e);
            }
        }
    }

    private String buildStepLabel(FilterTransformerElement element, String parentPath) {
        StringBuilder label = new StringBuilder(parentPath)
                .append(" > Step ").append(element.getSequenceNumber());
        String stepType = element.getType();
        if (stepType != null && !stepType.isEmpty()) {
            label.append(" (").append(stepType).append(")");
        }
        String name = element.getName();
        if (name != null && !name.isEmpty()) {
            label.append(": ").append(name);
        }
        return label.toString();
    }

    // ========================
    // Code template traversal
    // ========================

    private void visitCodeTemplates(ScriptHandler handler) {
        try {
            Map<String, String> templateLibraryMap = buildTemplateLibraryMap();

            List<CodeTemplate> templates = codeTemplateController.getCodeTemplates(null);
            if (templates == null) {
                return;
            }
            for (CodeTemplate template : templates) {
                String code = template.getCode();
                if (code != null) {
                    String libraryName = templateLibraryMap.get(template.getId());
                    String location = libraryName != null
                            ? libraryName + " > " + template.getName()
                            : template.getName();
                    handler.handle("CODE_TEMPLATE", template.getId(), template.getName(), location, code);
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve code templates", e);
        }
    }

    private Map<String, String> buildTemplateLibraryMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<CodeTemplateLibrary> libraries = codeTemplateController.getLibraries(null, true);
            if (libraries != null) {
                for (CodeTemplateLibrary library : libraries) {
                    if (library.getCodeTemplates() != null) {
                        for (CodeTemplate tmpl : library.getCodeTemplates()) {
                            map.put(tmpl.getId(), library.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve code template libraries", e);
        }
        return map;
    }

    // ========================
    // Global script traversal
    // ========================

    private void visitGlobalScripts(ScriptHandler handler) {
        try {
            Map<String, String> globalScripts = scriptController.getGlobalScripts();
            if (globalScripts == null) {
                return;
            }
            for (Map.Entry<String, String> entry : globalScripts.entrySet()) {
                handler.handle("GLOBAL_SCRIPT", null, entry.getKey(), entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to retrieve global scripts", e);
        }
    }

    // ========================
    // Core matching
    // ========================

    private void findMatches(Pattern pattern, List<SearchMatch> results,
                             String groupType, String channelId, String channelName,
                             String location, String scriptContent) {
        if (scriptContent == null || scriptContent.isEmpty()) {
            return;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REGEX_TIMEOUT_MS);
        String[] lines = scriptContent.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (System.nanoTime() > deadline) {
                log.warn("Regex timeout searching {} in {}", location, channelName);
                results.add(new SearchMatch(groupType, channelId, channelName,
                        location, 0, "[Search timed out in this script]"));
                return;
            }
            if (pattern.matcher(lines[i]).find()) {
                results.add(new SearchMatch(groupType, channelId, channelName,
                        location, i + 1, lines[i].trim()));
            }
        }
    }

    private void countMatches(Pattern pattern, AtomicInteger counter, String scriptContent) {
        if (scriptContent == null || scriptContent.isEmpty()) {
            return;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REGEX_TIMEOUT_MS);
        for (String line : scriptContent.split("\n", -1)) {
            if (System.nanoTime() > deadline) {
                log.warn("Regex timeout during count");
                return;
            }
            if (pattern.matcher(line).find()) {
                counter.incrementAndGet();
            }
        }
    }

    private Set<String> parseChannelIds(String channelIdsCsv) {
        if (channelIdsCsv == null || channelIdsCsv.isEmpty()) {
            return null;
        }
        Set<String> channelIds = new HashSet<>();
        for (String id : channelIdsCsv.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                channelIds.add(trimmed);
            }
        }
        return channelIds;
    }

    private List<Channel> getChannels(String channelIdsCsv) {
        return channelController.getChannels(parseChannelIds(channelIdsCsv));
    }
}
