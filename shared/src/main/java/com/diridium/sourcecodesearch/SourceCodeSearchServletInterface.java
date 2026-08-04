// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation.ExecuteType;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Path("/extensions/oie-source-code-search")
@Tag(name = "OIE Source Code Search")
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public interface SourceCodeSearchServletInterface extends BaseServletInterface {

    String PLUGIN_NAME = "OIE Source Code Search";

    /**
     * Permission published to the engine by {@code SourceCodeSearchServicePlugin}
     * and required for both operations below.
     *
     * <p>This is deliberately the plugin's own permission rather than a core one
     * such as {@code Permissions.CHANNELS_VIEW}. Extension servlet operations are
     * delivered to the authorization controller as the composite name
     * {@code "<pluginName>#<operationName>"}, which can never match a core
     * permission map keyed by bare engine operation names, so naming a core
     * permission here would have no effect at all.</p>
     */
    String PERMISSION_SEARCH = "Search Source Code";

    /** Client task names gated by {@link #PERMISSION_SEARCH}. */
    String TASK_SEARCH = "sourceCodeSearch";
    String TASK_SEARCH_CHANNEL = "sourceCodeSearchChannel";

    @GET
    @Path("/count")
    @Operation(summary = "Count matches across channels, code templates, and global scripts")
    @ApiResponse(content = {
            @Content(mediaType = MediaType.APPLICATION_JSON)})
    @MirthOperation(name = "count", display = "Count search matches",
            permission = PERMISSION_SEARCH, type = ExecuteType.ASYNC, auditable = true)
    SearchResults count(
            @Param("query") @Parameter(description = "Search string", required = true)
            @QueryParam("query") String query,
            @Param("caseSensitive") @Parameter(description = "Case sensitive search")
            @QueryParam("caseSensitive") boolean caseSensitive,
            @Param("regex") @Parameter(description = "Use regex matching")
            @QueryParam("regex") boolean regex,
            @Param("channelFilter")
            @Parameter(description = "Comma-separated channel IDs to search (empty = all)")
            @QueryParam("channelIds") String channelIds,
            @Param("searchChannels") @Parameter(description = "Search channel scripts")
            @QueryParam("searchChannels") boolean searchChannels,
            @Param("searchCodeTemplates") @Parameter(description = "Search code templates")
            @QueryParam("searchCodeTemplates") boolean searchCodeTemplates,
            @Param("searchGlobalScripts") @Parameter(description = "Search global scripts")
            @QueryParam("searchGlobalScripts") boolean searchGlobalScripts,
            @Param("searchMessageTemplates") @Parameter(description = "Search message templates")
            @QueryParam("searchMessageTemplates") boolean searchMessageTemplates,
            @Param("searchConnectorProperties") @Parameter(description = "Search connector properties (URLs, settings, etc.)")
            @QueryParam("searchConnectorProperties") boolean searchConnectorProperties,
            @Param("searchNames") @Parameter(description = "Search artifact names and descriptions")
            @QueryParam("searchNames") boolean searchNames
    ) throws ClientException;

    @GET
    @Path("/search")
    @Operation(summary = "Search across channels, code templates, and global scripts")
    @ApiResponse(content = {
            @Content(mediaType = MediaType.APPLICATION_XML),
            @Content(mediaType = MediaType.APPLICATION_JSON)})
    @MirthOperation(name = "search", display = "Search channel scripts and code templates",
            permission = PERMISSION_SEARCH, type = ExecuteType.ASYNC, auditable = true)
    SearchResults search(
            @Param("query") @Parameter(description = "Search string", required = true)
            @QueryParam("query") String query,
            @Param("caseSensitive") @Parameter(description = "Case sensitive search")
            @QueryParam("caseSensitive") boolean caseSensitive,
            @Param("regex") @Parameter(description = "Use regex matching")
            @QueryParam("regex") boolean regex,
            @Param("channelFilter")
            @Parameter(description = "Comma-separated channel IDs to search (empty = all)")
            @QueryParam("channelIds") String channelIds,
            @Param("searchChannels") @Parameter(description = "Search channel scripts")
            @QueryParam("searchChannels") boolean searchChannels,
            @Param("searchCodeTemplates") @Parameter(description = "Search code templates")
            @QueryParam("searchCodeTemplates") boolean searchCodeTemplates,
            @Param("searchGlobalScripts") @Parameter(description = "Search global scripts")
            @QueryParam("searchGlobalScripts") boolean searchGlobalScripts,
            @Param("searchMessageTemplates") @Parameter(description = "Search message templates")
            @QueryParam("searchMessageTemplates") boolean searchMessageTemplates,
            @Param("searchConnectorProperties") @Parameter(description = "Search connector properties (URLs, settings, etc.)")
            @QueryParam("searchConnectorProperties") boolean searchConnectorProperties,
            @Param("searchNames") @Parameter(description = "Search artifact names and descriptions")
            @QueryParam("searchNames") boolean searchNames
    ) throws ClientException;
}
