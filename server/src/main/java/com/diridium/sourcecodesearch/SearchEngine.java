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
import java.util.function.Predicate;
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

/**
 * Traverses server content and collects matching lines.
 *
 * <p>Every entry point takes a {@code channelFilter}: a predicate over channel
 * id that is null when the caller's role places no channel restrictions on
 * them, mirroring the engine's own convention of a null
 * {@code ChannelAuthorizer} meaning "unrestricted". When it is non-null the
 * traversal excludes channels the caller cannot see, excludes code templates
 * whose library is not in scope for any visible channel, and skips global
 * scripts entirely, since those are server-wide rather than channel-scoped.</p>
 *
 * <p>This class holds no per-request state; the servlet shares one instance
 * across concurrent requests, so anything mutable must be passed through the
 * traversal rather than stored in a field.</p>
 */
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

    public SearchResults count(String query, boolean caseSensitive, boolean regex,
                               String channelIdsCsv, boolean searchChannels,
                               boolean searchCodeTemplates, boolean searchGlobalScripts,
                               boolean searchMessageTemplates, boolean searchConnectorProperties,
                               Predicate<String> channelFilter) {
        Pattern pattern = buildPattern(query, caseSensitive, regex);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger skippedChannels = new AtomicInteger();

        ScriptHandler handler = (groupType, chId, chName, location, script) ->
                countMatches(pattern, counter, script);

        visitAll(handler, channelIdsCsv, searchChannels,
                searchCodeTemplates, searchGlobalScripts, searchMessageTemplates,
                searchConnectorProperties, channelFilter, skippedChannels);

        return new SearchResults(null, counter.get(), skippedChannels.get(), channelFilter != null);
    }

    public SearchResults search(String query, boolean caseSensitive, boolean regex,
                                String channelIdsCsv, boolean searchChannels,
                                boolean searchCodeTemplates, boolean searchGlobalScripts,
                                boolean searchMessageTemplates, boolean searchConnectorProperties,
                                Predicate<String> channelFilter) {
        Pattern pattern = buildPattern(query, caseSensitive, regex);
        List<SearchMatch> results = new ArrayList<>();
        AtomicInteger skippedChannels = new AtomicInteger();

        ScriptHandler handler = (groupType, chId, chName, location, script) ->
                findMatches(pattern, results, groupType, chId, chName, location, script);

        visitAll(handler, channelIdsCsv, searchChannels,
                searchCodeTemplates, searchGlobalScripts, searchMessageTemplates,
                searchConnectorProperties, channelFilter, skippedChannels);

        return new SearchResults(results, results.size(), skippedChannels.get(), channelFilter != null);
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

    private void visitAll(ScriptHandler handler, String channelIdsCsv,
                          boolean searchChannels, boolean searchCodeTemplates,
                          boolean searchGlobalScripts, boolean searchMessageTemplates,
                          boolean searchConnectorProperties,
                          Predicate<String> channelFilter, AtomicInteger skippedChannels) {
        // Global scripts are server-wide and have no channel to authorize against,
        // so a channel-restricted caller does not see them at all.
        if (searchGlobalScripts && channelFilter == null) {
            visitGlobalScripts(handler);
        }
        if (searchCodeTemplates) {
            visitCodeTemplates(handler, channelFilter);
        }
        if (searchChannels || searchMessageTemplates || searchConnectorProperties) {
            visitChannels(handler, channelIdsCsv, searchChannels, searchMessageTemplates,
                    searchConnectorProperties, channelFilter, skippedChannels);
        }
    }

    // ========================
    // Channel traversal
    // ========================

    private void visitChannels(ScriptHandler handler, String channelIdsCsv,
                               boolean searchScripts, boolean searchMessageTemplates,
                               boolean searchConnectorProperties,
                               Predicate<String> channelFilter, AtomicInteger skippedChannels) {
        try {
            for (Channel channel : getChannels(channelIdsCsv)) {
                if (channelFilter != null && !channelFilter.test(channel.getId())) {
                    skippedChannels.incrementAndGet();
                    continue;
                }
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

    private void visitCodeTemplates(ScriptHandler handler, Predicate<String> channelFilter) {
        try {
            LibraryIndex index = buildLibraryIndex(channelFilter);

            List<CodeTemplate> templates = codeTemplateController.getCodeTemplates(null);
            if (templates == null) {
                return;
            }
            for (CodeTemplate template : templates) {
                // A restricted caller sees a template only if its library is in scope for at
                // least one channel they can access. Templates belonging to no library are
                // excluded, since there is no channel to authorize them against.
                if (index.visibleTemplateIds() != null
                        && !index.visibleTemplateIds().contains(template.getId())) {
                    continue;
                }
                String code = template.getCode();
                if (code != null) {
                    String libraryName = index.libraryNames().get(template.getId());
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

    /**
     * Template id to library name, plus the set of template ids a restricted
     * caller may see. {@code visibleTemplateIds} is null when the caller has no
     * channel restrictions, meaning every template is visible.
     */
    private record LibraryIndex(Map<String, String> libraryNames, Set<String> visibleTemplateIds) {}

    private LibraryIndex buildLibraryIndex(Predicate<String> channelFilter) {
        Map<String, String> names = new HashMap<>();
        Set<String> visible = channelFilter == null ? null : new HashSet<>();
        Set<String> allowedChannelIds = channelFilter == null ? null : allowedChannelIds(channelFilter);

        try {
            List<CodeTemplateLibrary> libraries = codeTemplateController.getLibraries(null, true);
            if (libraries != null) {
                for (CodeTemplateLibrary library : libraries) {
                    if (library.getCodeTemplates() == null) {
                        continue;
                    }
                    boolean libraryVisible = visible == null
                            || isLibraryInScope(library, allowedChannelIds);
                    for (CodeTemplate tmpl : library.getCodeTemplates()) {
                        names.put(tmpl.getId(), library.getName());
                        if (visible != null && libraryVisible) {
                            visible.add(tmpl.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to retrieve code template libraries", e);
        }
        return new LibraryIndex(names, visible);
    }

    /**
     * True if the library applies to at least one channel the caller can access.
     * Mirrors the engine's library scoping: a channel is covered when it is
     * explicitly enabled, or when the library includes new channels and the
     * channel has not been explicitly disabled.
     */
    static boolean isLibraryInScope(CodeTemplateLibrary library, Set<String> allowedChannelIds) {
        Set<String> enabled = library.getEnabledChannelIds();
        Set<String> disabled = library.getDisabledChannelIds();

        for (String channelId : allowedChannelIds) {
            if (enabled != null && enabled.contains(channelId)) {
                return true;
            }
            if (library.isIncludeNewChannels() && (disabled == null || !disabled.contains(channelId))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> allowedChannelIds(Predicate<String> channelFilter) {
        Set<String> allowed = new HashSet<>();
        try {
            Set<String> all = channelController.getChannelIds();
            if (all != null) {
                for (String channelId : all) {
                    if (channelFilter.test(channelId)) {
                        allowed.add(channelId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to enumerate channel ids for code template scoping", e);
        }
        return allowed;
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
