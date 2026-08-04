// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import java.util.Properties;

import com.mirth.connect.client.core.api.util.OperationUtil;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;

/**
 * Server-side lifecycle hook whose only real job is to publish the plugin's
 * permission to the engine.
 *
 * <p>Registered via {@code <serverClasses>} in {@code plugin.xml}. At startup
 * the engine collects every service plugin's {@link ExtensionPermission}s and
 * hands them to the active {@code AuthorizationController}. Role-based
 * controllers use that registration to decide whether a caller may invoke this
 * plugin's operations; without it they see an operation with no permission
 * mapping, and typically fall back to allowing it for any authenticated user.
 * The stock controller ignores the registration entirely, so this is inert on
 * installs that do not use role-based authorization.</p>
 */
public class SourceCodeSearchServicePlugin implements ServicePlugin {

    @Override
    public String getPluginPointName() {
        return SourceCodeSearchServletInterface.PLUGIN_NAME;
    }

    /**
     * Declares the single permission gating this plugin.
     *
     * <p>The extension name must match the name this plugin passes to the
     * {@code MirthServlet} constructor. Operations from an extension servlet
     * reach the authorization controller as {@code "<extensionName>#<opName>"},
     * so a mismatch here registers a key that nothing will ever look up, and
     * the permission silently fails to apply.</p>
     *
     * <p>Operation names are derived by reflecting over the servlet interface
     * rather than hardcoded, so adding an operation to the interface with this
     * permission cannot leave it unregistered. The task names gate the client's
     * two menu entries, so users without the permission do not see an action
     * that would only fail.</p>
     */
    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        ExtensionPermission searchPermission = new ExtensionPermission(
                SourceCodeSearchServletInterface.PLUGIN_NAME,
                SourceCodeSearchServletInterface.PERMISSION_SEARCH,
                "Allows searching channel scripts, code templates, global scripts, message "
                        + "templates, and connector properties. Results stay limited to the "
                        + "channels the role can access.",
                OperationUtil.getOperationNamesForPermission(
                        SourceCodeSearchServletInterface.PERMISSION_SEARCH,
                        SourceCodeSearchServletInterface.class),
                new String[] {
                        SourceCodeSearchServletInterface.TASK_SEARCH,
                        SourceCodeSearchServletInterface.TASK_SEARCH_CHANNEL
                });

        return new ExtensionPermission[] { searchPermission };
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void init(Properties properties) {
    }

    @Override
    public void update(Properties properties) {
    }

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }
}
