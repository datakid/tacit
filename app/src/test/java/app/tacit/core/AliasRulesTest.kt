package app.tacit.core

import app.tacit.data.AliasText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasRulesTest {

    @Test
    fun keyFoldsCaseAccentsSpacesAndArabicVariants() {
        assertEquals("cafe latte", AliasRules.key("  Café   Latte "))
        assertEquals(AliasRules.key("أحمد"), AliasRules.key("احمد"))
        assertEquals(AliasRules.key("إيمان"), AliasRules.key("ايمان"))
        assertEquals(AliasRules.key("فاتورة"), AliasRules.key("فاتوره"))
        assertEquals("", AliasRules.key("   "))
    }

    @Test
    fun cleanDropsBlanksAndDuplicatesButKeepsTheFirstSpelling() {
        assertEquals(listOf("WA", "Chat"), AliasRules.clean(listOf(" WA ", "wa", "Chat", "", "chat", "  ")))
        assertEquals(listOf("أحمد"), AliasRules.clean(listOf("أحمد", "احمد", "أَحْمَد")))
        assertEquals(listOf("Café", "tea"), AliasRules.clean(listOf("Café", "cafe", "tea")))
    }

    @Test
    fun splitUsesTheSameCleaning() {
        assertEquals(listOf("Mum", "mama"), AliasText.split("Mum, MUM ; mama،  "))
        assertEquals(listOf("واتس", "wa"), AliasText.split("واتس، wa"))
        assertEquals(listOf("wa", "chat"), AliasText.split(" wa ,chat,, wa"))
    }

    @Test
    fun sharedListsEveryOwnerOfAnAliasRegardlessOfSpelling() {
        val map = mapOf("app:1" to listOf("YT", "tube"), "app:2" to listOf("yt"), "app:3" to listOf("other"))
        val shared = AliasRules.shared(map) { true }
        assertEquals(1, shared.size)
        assertEquals("YT", shared[0].alias)
        assertEquals(listOf("app:1", "app:2"), shared[0].owners)
    }

    @Test
    fun sharedIgnoresItemsThatAreNoLongerPresent() {
        val map = mapOf("app:1" to listOf("yt"), "app:gone" to listOf("yt"))
        assertTrue(AliasRules.shared(map) { it == "app:1" }.isEmpty())
        assertEquals(1, AliasRules.shared(map) { true }.size)
    }

    @Test
    fun sharedIsSortedByAlias() {
        val map = mapOf("a" to listOf("zeta", "alpha"), "b" to listOf("alpha", "zeta"))
        assertEquals(listOf("alpha", "zeta"), AliasRules.shared(map) { true }.map { it.alias })
    }

    @Test
    fun otherOwnersFindsOtherItemsUsingTheSameAlias() {
        val map = mapOf("app:1" to listOf("yt"), "app:2" to listOf("YT"), "app:3" to listOf("x"))
        assertEquals(listOf("app:2"), AliasRules.otherOwners(map, "app:1", listOf("Yt")))
        assertTrue(AliasRules.otherOwners(map, "app:3", listOf("x")).isEmpty())
        assertTrue(AliasRules.otherOwners(map, "app:1", listOf("  ")).isEmpty())
    }

    @Test
    fun matchingPutsExactBeforePrefixAndIgnoresOthers() {
        val entries = listOf(
            AliasEntry("file:/b", listOf(AliasRules.key("taxes"), AliasRules.key("invoice"))),
            AliasEntry("file:/a", listOf(AliasRules.key("tax"))),
            AliasEntry("file:/c", listOf(AliasRules.key("فاتورة")))
        )
        assertEquals(listOf("file:/a", "file:/b"), AliasRules.matching("TAX", entries).map { it.key })
        assertEquals(listOf("file:/b"), AliasRules.matching("inv", entries).map { it.key })
        assertEquals(listOf("file:/c"), AliasRules.matching("فاتوره", entries).map { it.key })
        assertTrue(AliasRules.matching("x", entries).isEmpty())
        assertTrue(AliasRules.matching("   ", entries).isEmpty())
        assertTrue(AliasRules.matching("tax", emptyList()).isEmpty())
    }
}
