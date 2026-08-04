// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.io.Serializable;
import java.util.List;

/**
 * Response envelope for both the count and search operations.
 *
 * <p>The envelope exists so the server can tell the client that results were
 * filtered by the caller's role. Without that signal a restricted user cannot
 * distinguish "this string appears nowhere" from "this string appears only in
 * channels you cannot see", and an exported result set would be silently
 * partial.</p>
 *
 * <p>{@link #getMatches()} is null for count responses, which report only
 * {@link #getMatchCount()}.</p>
 */
public class SearchResults implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<SearchMatch> matches;
    private int matchCount;
    private int skippedChannelCount;
    private boolean restricted;

    public SearchResults() {
    }

    public SearchResults(List<SearchMatch> matches, int matchCount,
                         int skippedChannelCount, boolean restricted) {
        this.matches = matches;
        this.matchCount = matchCount;
        this.skippedChannelCount = skippedChannelCount;
        this.restricted = restricted;
    }

    public List<SearchMatch> getMatches() {
        return matches;
    }

    public void setMatches(List<SearchMatch> matches) {
        this.matches = matches;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(int matchCount) {
        this.matchCount = matchCount;
    }

    /**
     * Number of channels that were in scope for the search but excluded
     * because the caller's role does not grant access to them.
     */
    public int getSkippedChannelCount() {
        return skippedChannelCount;
    }

    public void setSkippedChannelCount(int skippedChannelCount) {
        this.skippedChannelCount = skippedChannelCount;
    }

    /**
     * True when the caller's role limits them to a subset of channels, meaning
     * channels, code templates, and global scripts were filtered and the
     * results may be incomplete.
     */
    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }
}
