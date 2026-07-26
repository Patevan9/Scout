package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

// resolveEntity() and buildAliasMap() need a live TruthDb (Android SQLite), so
// they aren't covered here -- only the pure displayName() formatting is
// unit-testable in this source set. The DB-dependent paths are exercised by
// on-device testing instead.
class ScoutEntityResolverTest {

    @Test fun `display name capitalizes a lowercase entity slug`() {
        assertEquals("Diana", ScoutEntityResolver.displayName("diana"))
        assertEquals("Nicolas", ScoutEntityResolver.displayName("nicolas"))
    }

    @Test fun `display name capitalizes each word of a multi-word slug`() {
        assertEquals("My Wife", ScoutEntityResolver.displayName("my wife"))
    }
}
