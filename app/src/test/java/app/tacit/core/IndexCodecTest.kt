package app.tacit.core

import app.tacit.sources.ContactInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class IndexCodecTest {

    private fun encode(items: List<SearchItem>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { IndexCodec.write(it, items) }
        return buffer.toByteArray()
    }

    private fun decode(bytes: ByteArray): List<SearchItem>? =
        DataInputStream(ByteArrayInputStream(bytes)).use { IndexCodec.read(it) }

    private fun roundTrip(items: List<SearchItem>): List<SearchItem> = decode(encode(items))!!

    private fun contactItem(
        name: String,
        id: Long,
        numbers: List<String>,
        primary: String?,
        whatsapp: Long?,
        business: Long?,
        starred: Boolean
    ) = SearchItem("contact:key-$id", ItemKind.CONTACT, name, primary ?: numbers.firstOrNull().orEmpty(), numbers, id.toString(),
        ContactInfo("key-$id", id, name, numbers, primary, whatsapp, business, starred))

    private fun assertSameItem(expected: SearchItem, actual: SearchItem) {
        assertEquals(expected.key, actual.key)
        assertEquals(expected.kind, actual.kind)
        assertEquals(expected.label, actual.label)
        assertEquals(expected.subtitle, actual.subtitle)
        assertEquals(expected.extraNames, actual.extraNames)
        assertEquals(expected.payload, actual.payload)
        assertEquals(expected.pinned, actual.pinned)
        assertEquals(expected.aliases.toList(), actual.aliases.toList())
        assertEquals(expected.names.map { it.full }, actual.names.map { it.full })
    }

    private fun assertSameContact(expected: ContactInfo, actual: Any?) {
        assertTrue("extra is ${actual?.javaClass}", actual is ContactInfo)
        actual as ContactInfo
        assertEquals(expected.lookupKey, actual.lookupKey)
        assertEquals(expected.contactId, actual.contactId)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.numbers, actual.numbers)
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.whatsappDataId, actual.whatsappDataId)
        assertEquals(expected.whatsappBusinessDataId, actual.whatsappBusinessDataId)
        assertEquals(expected.starred, actual.starred)
    }

    @Test
    fun everyKindOfItemRoundTrips() {
        IndexOptions.transliterate = true
        val pinned = SearchItem("app:com.example/.Main:0", ItemKind.APP, "YouTube Music", "Work", listOf("music"), "com.example/.Main|0")
        pinned.pinned = true
        val aliased = SearchItem("setting:wifi", ItemKind.SETTING, "Wi-Fi", "Settings", listOf("wireless", "internet"), "android.settings.WIFI_SETTINGS")
            .withAliases(listOf("net", "واي فاي"))
        val items = listOf(
            pinned,
            aliased,
            SearchItem("snippet:1", ItemKind.SNIPPET, "Signature", "Best regards", listOf("sig"), "Best regards,\nNick"),
            SearchItem("shortcut:p:id:0", ItemKind.ACTION, "New chat", "Messenger", listOf("chat"), "p|id|0"),
            SearchItem("empty", ItemKind.ACTION, "Nothing", "", emptyList(), "")
        )
        val restored = roundTrip(items)
        assertEquals(items.size, restored.size)
        for (index in items.indices) assertSameItem(items[index], restored[index])
        assertNull(restored[0].extra)
    }

    @Test
    fun contactDetailsSurviveTheCache() {
        val full = contactItem("Asmaa Gouda", 77L, listOf("+201001234567", "0227771234"), "+201001234567", 9L, 12L, true)
        val bare = contactItem("أسماء جودة", 78L, emptyList(), null, null, null, false)
        val business = contactItem("Ahmed Galal", 79L, listOf("+201112223334"), null, null, 5L, false)
        val restored = roundTrip(listOf(full, bare, business))
        assertSameItem(full, restored[0])
        assertSameItem(bare, restored[1])
        assertSameItem(business, restored[2])
        assertSameContact(full.extra as ContactInfo, restored[0].extra)
        assertSameContact(bare.extra as ContactInfo, restored[1].extra)
        assertSameContact(business.extra as ContactInfo, restored[2].extra)
        assertNull((restored[1].extra as ContactInfo).primary)
        assertNull((restored[2].extra as ContactInfo).whatsappDataId)
        assertEquals(5L, (restored[2].extra as ContactInfo).whatsappBusinessDataId)
    }

    @Test
    fun quicklinkTypesSurviveTheCache() {
        val items = QuicklinkType.entries.mapIndexed { index, type ->
            SearchItem("ql:$index", ItemKind.ACTION, "Link $index", type.name, emptyList(), "ql|$index", type)
        }
        val restored = roundTrip(items)
        for (index in items.indices) {
            assertSameItem(items[index], restored[index])
            assertEquals(QuicklinkType.entries[index], restored[index].extra)
        }
    }

    @Test
    fun anyOtherExtraIsDroppedInsteadOfBreakingTheStream() {
        val odd = SearchItem("odd", ItemKind.ACTION, "Odd", "", emptyList(), "x", "some string")
        val after = contactItem("After", 80L, listOf("0100"), "0100", 1L, null, false)
        val restored = roundTrip(listOf(odd, after))
        assertNull(restored[0].extra)
        assertSameContact(after.extra as ContactInfo, restored[1].extra)
    }

    @Test
    fun aLongSnippetBodyIsNotTruncated() {
        val unit = "سطر طويل جدا مع emoji 😀 و line\n"
        val body = unit.repeat(4_000)
        assertTrue(body.length > 100_000)
        val snippet = SearchItem("snippet:long", ItemKind.SNIPPET, "Long", body.replace('\n', ' ').take(80), emptyList(), body)
        val restored = roundTrip(listOf(snippet))
        assertEquals(body, restored[0].payload)
        assertEquals(body.length, restored[0].payload.length)
    }

    @Test
    fun anOldFormatFileIsIgnored() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeInt(3)
            it.writeInt(1)
            it.writeUTF("app:x")
        }
        assertNull(decode(buffer.toByteArray()))
        assertEquals(4, IndexCodec.VERSION)
    }

    @Test
    fun aTruncatedStreamFailsInsteadOfReturningPartialData() {
        val bytes = encode(listOf(
            contactItem("Asmaa Gouda", 77L, listOf("+201001234567"), "+201001234567", 9L, null, true),
            SearchItem("snippet:1", ItemKind.SNIPPET, "Signature", "Best regards", emptyList(), "Best regards")
        ))
        for (cut in listOf(bytes.size - 1, bytes.size / 2, 9, 4)) {
            try {
                decode(bytes.copyOf(cut))
                fail("a stream cut at $cut bytes was accepted")
            } catch (expected: IOException) {
                assertNotNull(expected)
            }
        }
    }

    @Test
    fun corruptCountsAndMarkersAreRejected() {
        for (count in listOf(-5, Int.MAX_VALUE)) {
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).use {
                it.writeInt(IndexCodec.VERSION)
                it.writeInt(count)
            }
            try {
                decode(buffer.toByteArray())
                fail("count $count was accepted")
            } catch (expected: IOException) {
                assertNotNull(expected)
            }
        }
        val good = encode(listOf(SearchItem("a", ItemKind.APP, "A", "", emptyList(), "p")))
        val broken = good.copyOf()
        broken[broken.size - 1] = 9
        try {
            decode(broken)
            fail("an unknown extra marker was accepted")
        } catch (expected: IOException) {
            assertNotNull(expected)
        }
    }

    @Test
    fun theFileApiWritesAtomicallyAndReadsBack() {
        val dir = Files.createTempDirectory("tacit-index").toFile()
        try {
            val file = File(dir, "index.bin")
            assertNull(IndexCodec.readFile(file))
            val items = listOf(
                contactItem("Asmaa Gouda", 77L, listOf("+201001234567"), "+201001234567", 9L, null, true),
                SearchItem("ql:0", ItemKind.ACTION, "Docs", "docs.example", emptyList(), "ql|0", QuicklinkType.URL)
            )
            IndexCodec.writeFile(file, items)
            assertTrue(file.exists())
            assertFalse(File(dir, "index.bin.tmp").exists())
            val restored = IndexCodec.readFile(file)!!
            assertEquals(2, restored.size)
            assertSameContact(items[0].extra as ContactInfo, restored[0].extra)
            assertEquals(QuicklinkType.URL, restored[1].extra)
            IndexCodec.writeFile(file, emptyList())
            assertEquals(0, IndexCodec.readFile(file)!!.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
