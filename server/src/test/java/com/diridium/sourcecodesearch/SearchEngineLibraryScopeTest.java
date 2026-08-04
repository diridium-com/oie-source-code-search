// SPDX-License-Identifier: MPL-2.0
// Copyright (c) 2025-2026 Diridium Technologies Inc.

package com.diridium.sourcecodesearch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mirth.connect.model.codetemplates.CodeTemplateLibrary;

/**
 * Covers which code template libraries a channel-restricted user may search.
 *
 * <p>This is the subtlest part of the restriction logic: a library can reach a
 * channel either by naming it explicitly or by including new channels and not
 * excluding it, and getting the second case wrong leaks every shared template
 * to every restricted user without any visible symptom.</p>
 */
class SearchEngineLibraryScopeTest {

    private static CodeTemplateLibrary library(boolean includeNewChannels,
                                               Set<String> enabled, Set<String> disabled) {
        CodeTemplateLibrary library = new CodeTemplateLibrary();
        library.setIncludeNewChannels(includeNewChannels);
        library.setEnabledChannelIds(enabled);
        library.setDisabledChannelIds(disabled);
        return library;
    }

    @Test
    void explicitlyEnabledChannelPutsLibraryInScope() {
        CodeTemplateLibrary library = library(false, Set.of("visible-channel"), Collections.emptySet());

        assertTrue(SearchEngine.isLibraryInScope(library, Set.of("visible-channel")));
    }

    @Test
    void libraryScopedOnlyToUnreachableChannelsIsOutOfScope() {
        CodeTemplateLibrary library = library(false, Set.of("hidden-channel"), Collections.emptySet());

        assertFalse(SearchEngine.isLibraryInScope(library, Set.of("visible-channel")));
    }

    @Test
    void includeNewChannelsPutsLibraryInScopeForAnyUndisabledChannel() {
        CodeTemplateLibrary library = library(true, Collections.emptySet(), Collections.emptySet());

        assertTrue(SearchEngine.isLibraryInScope(library, Set.of("visible-channel")));
    }

    @Test
    void includeNewChannelsDoesNotCoverAnExplicitlyDisabledChannel() {
        CodeTemplateLibrary library = library(true, Collections.emptySet(), Set.of("visible-channel"));

        assertFalse(SearchEngine.isLibraryInScope(library, Set.of("visible-channel")));
    }

    @Test
    void includeNewChannelsStillCoversASecondVisibleChannel() {
        CodeTemplateLibrary library = library(true, Collections.emptySet(), Set.of("visible-channel"));

        assertTrue(SearchEngine.isLibraryInScope(library, Set.of("visible-channel", "other-visible")));
    }

    @Test
    void userWithNoVisibleChannelsSeesNoLibrary() {
        CodeTemplateLibrary library = library(true, Set.of("some-channel"), Collections.emptySet());

        assertFalse(SearchEngine.isLibraryInScope(library, Collections.emptySet()));
    }

    @Test
    void nullChannelIdSetsAreTreatedAsEmptyRatherThanThrowing() {
        CodeTemplateLibrary library = library(false, null, null);

        assertFalse(SearchEngine.isLibraryInScope(library, Set.of("visible-channel")));
    }
}
