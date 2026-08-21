package com.example.scoutface.brain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for ScoutLanguagePack -- no Android, no org.json (this class has
 * neither dependency; see its own doc comment). Most tests below build a small,
 * literal fixture list rather than loading the real bundled language_pack.json --
 * that keeps these tests fast, deterministic, and decoupled from the exact wording
 * curated into the real file, while still exercising the class's actual contract.
 *
 * The one exception is [ambiguous banned examples are absent from the actual
 * bundled v1 data], which deliberately reads the real asset file's raw text --
 * that one test's whole point is to guard the real file's content, not a stand-in.
 * It reads the file directly via java.io.File rather than through org.json, since
 * org.json is an Android-provided class this repo's plain JVM unit test task
 * cannot use without Robolectric (which this project does not depend on) -- see
 * that test's own comment for why a raw-text check is still exact and safe here.
 */
class ScoutLanguagePackTest {

    private val fixtureSource = listOf(
        "GREETING" to listOf("what is up", "yo", "wassup"),
        "ACKNOWLEDGE" to listOf("gotcha", "no worries"),
        "CONFIRM" to listOf("you got it", "spot on")
    )

    @Test fun `a known exact variant resolves to its category`() {
        val pack = ScoutLanguagePack(fixtureSource)
        assertEquals("ACKNOWLEDGE", pack.categoryFor("gotcha"))
    }

    @Test fun `an unknown phrase returns null`() {
        val pack = ScoutLanguagePack(fixtureSource)
        assertNull(pack.categoryFor("banana"))
    }

    @Test fun `every variant in a fixture flattens to its declared category`() {
        val pack = ScoutLanguagePack(fixtureSource)
        for ((category, variants) in fixtureSource) {
            for (variant in variants) {
                assertEquals("variant '$variant' should resolve to $category", category, pack.categoryFor(variant))
            }
        }
    }

    @Test fun `a surface phrase duplicated across two different categories is rejected`() {
        val source = listOf(
            "GREETING" to listOf("sup"),
            "CONFIRM" to listOf("sup") // contrived collision -- never present in the real bundled data
        )
        val pack = ScoutLanguagePack(source)
        assertNull(pack.categoryFor("sup"))
        assertTrue(pack.rejectedDuplicates.contains("sup"))
    }

    @Test fun `a surface phrase duplicated within the SAME category is also rejected`() {
        // A data-entry typo (the same variant listed twice under one category)
        // gets the same conservative treatment as a cross-category collision --
        // this class never assumes a repeat is harmless just because it happens to
        // point at the same category both times.
        val source = listOf(
            "GREETING" to listOf("yo", "yo")
        )
        val pack = ScoutLanguagePack(source)
        assertNull(pack.categoryFor("yo"))
        assertTrue(pack.rejectedDuplicates.contains("yo"))
    }

    @Test fun `a rejected duplicate does not block lookup of other, unrelated variants`() {
        val source = listOf(
            "GREETING" to listOf("sup", "yo"),
            "CONFIRM" to listOf("sup")
        )
        val pack = ScoutLanguagePack(source)
        assertNull(pack.categoryFor("sup"))
        assertEquals("GREETING", pack.categoryFor("yo"))
    }

    @Test fun `an empty source is safe and every lookup returns null`() {
        val pack = ScoutLanguagePack(emptyList())
        assertNull(pack.categoryFor("yo"))
        assertNull(pack.categoryFor(""))
        assertTrue(pack.rejectedDuplicates.isEmpty())
    }

    @Test fun `input already in normalized form matches`() {
        val pack = ScoutLanguagePack(fixtureSource)
        // "what is up" is exactly the form TextNormalizer.normalizeUtterance()
        // would produce from "what's up"/"whats up" -- this class trusts that
        // normalization already happened before categoryFor() is ever called.
        assertEquals("GREETING", pack.categoryFor("what is up"))
    }

    @Test fun `categoryFor performs no independent lowercasing or trimming of its own`() {
        val pack = ScoutLanguagePack(fixtureSource)
        // A differently-cased or stray-whitespace form of a known variant must NOT
        // resolve -- proving this class does zero normalization of its own and
        // fully trusts its caller, per the class doc comment's input contract.
        assertNull(pack.categoryFor("What Is Up"))
        assertNull(pack.categoryFor(" what is up "))
        assertNull(pack.categoryFor("WASSUP"))
    }

    @Test fun `a one-word exact variant such as 'yo' can resolve from the pack`() {
        val pack = ScoutLanguagePack(fixtureSource)
        // Deliberately not gated by any one-word allowlist here -- this class has
        // no concept of word count at all, only exact whole-string matching. See
        // the class doc comment's "One-word behavior" section for why that's safe.
        assertEquals("GREETING", pack.categoryFor("yo"))
    }

    @Test fun `normalized greeting form 'what is up' resolves as GREETING`() {
        val pack = ScoutLanguagePack(fixtureSource)
        assertEquals("GREETING", pack.categoryFor("what is up"))
    }

    @Test fun `ambiguous banned examples are absent from the actual bundled v1 data`() {
        // Reads the real, bundled app/src/main/assets/datasets/language_pack.json
        // as raw text -- Gradle's Test task runs with its working directory set to
        // the module directory (app/), so this relative path resolves correctly
        // under ./gradlew testDebugUnitTest, the same way CI runs this suite.
        //
        // A plain substring search for "later"/"affirmative" would wrongly flag
        // approved phrases like "catch you later" (which legitimately CONTAINS
        // "later"). Instead this checks for the exact, quoted JSON string token --
        // "\"later\"" only matches a standalone JSON value equal to exactly
        // "later", never a substring inside a longer approved phrase, since a real
        // JSON array entry like "catch you later" is serialized as
        // "catch you later" (a space, not a quote, immediately precedes "later").
        val file = File("src/main/assets/datasets/language_pack.json")
        assertTrue("expected bundled language_pack.json at ${file.absolutePath}", file.exists())
        val raw = file.readText()

        assertFalse(
            "bare 'later' must never be a standalone GOODBYE variant -- too context-dependent to safely close the conversation",
            raw.contains("\"later\"")
        )
        assertFalse(
            "'affirmative' must never appear -- it fundamentally represents YES, and YES/NO are deliberately out of v1",
            raw.contains("\"affirmative\"")
        )
    }
}
