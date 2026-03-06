// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.model.Channel;
import com.mirth.connect.plugins.ClientPlugin;

/**
 * Plugin that adds "Source Code Search" action to the Channel list
 * and Channel editor panels.
 */
public class SourceCodeSearchPlugin extends ClientPlugin {

    private Frame parent;
    private SourceCodeSearchDialog dialog;

    public SourceCodeSearchPlugin(String name) {
        super(SourceCodeSearchServletInterface.PLUGIN_NAME);
    }

    @Override
    public String getPluginPointName() {
        return "Source Code Search";
    }

    @Override
    public void start() {
        parent = PlatformUI.MIRTH_FRAME;

        ImageIcon icon = new ImageIcon(Frame.class.getResource("images/folder_explore.png"));

        // Add to the Channels list panel
        parent.addTask("sourceCodeSearch", "Source Code Search",
                "Search across all channel scripts, code templates, and global scripts.", "",
                icon, parent.channelPanel.channelTasks,
                parent.channelPanel.channelPopupMenu, this);

        // Add to the Channel editor panel
        parent.addTask("sourceCodeSearchChannel", "Source Code Search",
                "Search this channel and its code templates.", "",
                icon, parent.channelEditTasks,
                parent.channelEditPopupMenu, this);
    }

    @Override
    public void stop() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
    }

    @Override
    public void reset() {
    }

    /**
     * Called when the user clicks "Source Code Search" from the Channels list.
     */
    public void sourceCodeSearch() {
        List<String> selectedChannelIds = new ArrayList<>();
        List<Channel> selectedChannels = parent.channelPanel.getSelectedChannels();
        if (selectedChannels != null && !parent.channelPanel.isGroupSelected()) {
            for (Channel ch : selectedChannels) {
                selectedChannelIds.add(ch.getId());
            }
        }

        openDialog(selectedChannelIds, false);
    }

    /**
     * Called when the user clicks "Source Code Search" from the Channel editor.
     */
    public void sourceCodeSearchChannel() {
        List<String> channelIds = new ArrayList<>();
        Channel current = parent.channelEditPanel.currentChannel;
        if (current != null) {
            channelIds.add(current.getId());
        }

        openDialog(channelIds, true);
    }

    private void openDialog(List<String> channelIds, boolean channelScoped) {
        if (dialog == null || !dialog.isDisplayable()) {
            dialog = new SourceCodeSearchDialog(parent, channelIds);
        } else {
            dialog.updateSelectedChannels(channelIds);
            dialog.toFront();
            dialog.requestFocus();
        }
        dialog.setChannelScoped(channelScoped);
        dialog.setVisible(true);
    }
}
