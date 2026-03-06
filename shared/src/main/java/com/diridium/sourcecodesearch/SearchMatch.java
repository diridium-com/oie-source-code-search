// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.io.Serializable;

/**
 * A single search match within a specific script location.
 */
public class SearchMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelId;
    private String channelName;
    private String groupType;    // CHANNEL, CODE_TEMPLATE, GLOBAL_SCRIPT
    private String location;     // e.g. "Source > Transformer > Step 2: mapVar"
    private int lineNumber;
    private String lineText;

    public SearchMatch() {
    }

    public SearchMatch(String groupType, String channelId, String channelName,
                       String location, int lineNumber, String lineText) {
        this.groupType = groupType;
        this.channelId = channelId;
        this.channelName = channelName;
        this.location = location;
        this.lineNumber = lineNumber;
        this.lineText = lineText;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getLineText() {
        return lineText;
    }

    public void setLineText(String lineText) {
        this.lineText = lineText;
    }
}
