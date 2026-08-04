// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.util.function.Predicate;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ChannelAuthorizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SourceCodeSearchServlet extends MirthServlet implements SourceCodeSearchServletInterface {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeSearchServlet.class);
    private static final SearchEngine searchEngine = new SearchEngine();

    public SourceCodeSearchServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_NAME);
    }

    @Override
    public SearchResults count(String query, boolean caseSensitive, boolean regex,
                               String channelIds, boolean searchChannels,
                               boolean searchCodeTemplates, boolean searchGlobalScripts,
                               boolean searchMessageTemplates, boolean searchConnectorProperties)
            throws ClientException {
        try {
            return searchEngine.count(query, caseSensitive, regex, channelIds,
                    searchChannels, searchCodeTemplates, searchGlobalScripts,
                    searchMessageTemplates, searchConnectorProperties, channelFilter());
        } catch (IllegalArgumentException e) {
            throw new ClientException(e.getMessage());
        } catch (Exception e) {
            log.error("Count failed for query: {}", query, e);
            throw new ClientException(e);
        }
    }

    @Override
    public SearchResults search(String query, boolean caseSensitive, boolean regex,
                                String channelIds, boolean searchChannels,
                                boolean searchCodeTemplates, boolean searchGlobalScripts,
                                boolean searchMessageTemplates, boolean searchConnectorProperties)
            throws ClientException {
        try {
            return searchEngine.search(query, caseSensitive, regex, channelIds,
                    searchChannels, searchCodeTemplates, searchGlobalScripts,
                    searchMessageTemplates, searchConnectorProperties, channelFilter());
        } catch (IllegalArgumentException e) {
            throw new ClientException(e.getMessage());
        } catch (Exception e) {
            log.error("Search failed for query: {}", query, e);
            throw new ClientException(e);
        }
    }

    /**
     * Builds the per-channel authorization predicate for the calling user, or
     * null when their role places no channel restrictions on them.
     *
     * <p>The operation-level permission check has already run by the time this
     * is called, but that check is all-or-nothing. Roles that limit a user to a
     * subset of channels are enforced separately, through the authorization
     * controller's {@code ChannelAuthorizer}, and it is on each servlet to
     * apply it. Both operations must use this: leaving {@code count}
     * unfiltered would let a caller probe for the presence of a string in
     * channels they cannot see, without ever receiving a matching line.</p>
     *
     * <p>A restriction with no authorizer denies everything. That combination
     * means the controller reported restrictions but produced no predicate, and
     * guessing "allow" there would defeat the restriction entirely.</p>
     */
    private Predicate<String> channelFilter() {
        if (!doesUserHaveChannelRestrictions()) {
            return null;
        }
        ChannelAuthorizer authorizer = getChannelAuthorizer();
        if (authorizer == null) {
            log.warn("Channel restrictions reported with no authorizer; denying all channels");
            return channelId -> false;
        }
        return authorizer::isChannelAuthorized;
    }
}
