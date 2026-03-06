// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.server.api.MirthServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceCodeSearchServlet extends MirthServlet implements SourceCodeSearchServletInterface {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeSearchServlet.class);
    private static final SearchEngine searchEngine = new SearchEngine();

    public SourceCodeSearchServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_NAME);
    }

    @Override
    public int count(String query, boolean caseSensitive, boolean regex,
                     String channelIds, boolean searchChannels,
                     boolean searchCodeTemplates, boolean searchGlobalScripts,
                     boolean searchMessageTemplates, boolean searchConnectorProperties)
            throws ClientException {
        try {
            return searchEngine.count(query, caseSensitive, regex, channelIds,
                    searchChannels, searchCodeTemplates, searchGlobalScripts,
                    searchMessageTemplates, searchConnectorProperties);
        } catch (IllegalArgumentException e) {
            throw new ClientException(e.getMessage());
        } catch (Exception e) {
            log.error("Count failed for query: {}", query, e);
            throw new ClientException(e);
        }
    }

    @Override
    public List<SearchMatch> search(String query, boolean caseSensitive, boolean regex,
                                     String channelIds, boolean searchChannels,
                                     boolean searchCodeTemplates, boolean searchGlobalScripts,
                                     boolean searchMessageTemplates, boolean searchConnectorProperties)
            throws ClientException {
        try {
            return searchEngine.search(query, caseSensitive, regex, channelIds,
                    searchChannels, searchCodeTemplates, searchGlobalScripts,
                    searchMessageTemplates, searchConnectorProperties);
        } catch (IllegalArgumentException e) {
            throw new ClientException(e.getMessage());
        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            throw new ClientException(e);
        }
    }
}
