// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.model.ExtensionPermission;

/**
 * Guards the registration contract between this plugin and the engine's
 * authorization controller.
 *
 * <p>Every assertion here covers a failure that produces no error at runtime.
 * A wrong extension name, a missing operation, or a task name that does not
 * match the client's registration all leave the plugin looking installed and
 * working while the permission quietly fails to apply.</p>
 */
class SourceCodeSearchServicePluginTest {

    private static ExtensionPermission permission() {
        ExtensionPermission[] permissions = new SourceCodeSearchServicePlugin().getExtensionPermissions();
        assertEquals(1, permissions.length, "expected exactly one declared permission");
        return permissions[0];
    }

    @Test
    void extensionNameMatchesTheNameTheServletRegistersUnder() {
        // The servlet passes PLUGIN_NAME to its MirthServlet superclass, and the engine
        // delivers operations as "<extensionName>#<opName>". If these two differ, the
        // registered key is one nothing ever looks up.
        assertEquals(SourceCodeSearchServletInterface.PLUGIN_NAME, permission().getExtensionName());
    }

    @Test
    void pluginPointNameMatchesTheExtensionName() {
        assertEquals(SourceCodeSearchServletInterface.PLUGIN_NAME,
                new SourceCodeSearchServicePlugin().getPluginPointName());
    }

    @Test
    void everyAnnotatedOperationIsRegistered() {
        Set<String> annotated = new HashSet<>();
        for (Method method : SourceCodeSearchServletInterface.class.getMethods()) {
            MirthOperation operation = method.getAnnotation(MirthOperation.class);
            if (operation != null) {
                assertEquals(SourceCodeSearchServletInterface.PERMISSION_SEARCH, operation.permission(),
                        "operation '" + operation.name() + "' declares a different permission, so it "
                                + "would not be covered by the registered one");
                annotated.add(operation.name());
            }
        }

        Set<String> registered = new HashSet<>(Arrays.asList(permission().getOperationNames()));
        assertEquals(annotated, registered);
        assertTrue(registered.containsAll(Set.of("search", "count")));
    }

    @Test
    void everyOperationIsAuditable() {
        for (Method method : SourceCodeSearchServletInterface.class.getMethods()) {
            MirthOperation operation = method.getAnnotation(MirthOperation.class);
            if (operation != null) {
                assertTrue(operation.auditable(),
                        "operation '" + operation.name() + "' is not auditable, so searches it "
                                + "serves would leave no server event");
            }
        }
    }

    @Test
    void bothClientTaskNamesAreGated() {
        Set<String> tasks = new HashSet<>(Arrays.asList(permission().getTaskNames()));

        assertEquals(Set.of(SourceCodeSearchServletInterface.TASK_SEARCH,
                SourceCodeSearchServletInterface.TASK_SEARCH_CHANNEL), tasks);
    }
}
