// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * The task names are load-bearing in two directions at once, and neither
 * failure shows up at compile time.
 *
 * <p>The engine binds each task to a method on this plugin looked up by the
 * task name, so a name with no matching method fails only when the user clicks
 * it. The server registers the same names as the permission's task names, so a
 * name that does not match what the client registered silently stops hiding the
 * menu entry from users who lack the permission.</p>
 */
class SourceCodeSearchPluginTaskTest {

    @Test
    void eachTaskNameResolvesToACallbackMethod() {
        assertDoesNotThrow(
                () -> SourceCodeSearchPlugin.class.getMethod(SourceCodeSearchServletInterface.TASK_SEARCH),
                "no public method matches the task name the engine will invoke");
        assertDoesNotThrow(
                () -> SourceCodeSearchPlugin.class.getMethod(SourceCodeSearchServletInterface.TASK_SEARCH_CHANNEL),
                "no public method matches the task name the engine will invoke");
    }

    @Test
    void theTwoTasksAreDistinct() {
        assertNotEquals(SourceCodeSearchServletInterface.TASK_SEARCH,
                SourceCodeSearchServletInterface.TASK_SEARCH_CHANNEL);
    }
}
