package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenLabelsTest {

    private val items = listOf(
        SearchItem("app:com.example.mail/.Main:0", ItemKind.APP, "Mail", "", emptyList(), "com.example.mail/.Main|0"),
        SearchItem("app:com.example.work/.Main:10", ItemKind.APP, "Mail", "Work", emptyList(), "com.example.work/.Main|10"),
        SearchItem("contact:abc", ItemKind.CONTACT, "Asmaa Gouda"),
        SearchItem("setting:wifi", ItemKind.SETTING, "Wi-Fi"),
        SearchItem("snippet:1", ItemKind.SNIPPET, "Signature"),
        SearchItem("ql:1", ItemKind.ACTION, "Docs"),
        SearchItem("blank", ItemKind.ACTION, "   ")
    )

    @Test
    fun keysResolveToTheirLabelsInOrder() {
        val keys = listOf("setting:wifi", "app:com.example.mail/.Main:0", "contact:abc", "ql:1", "snippet:1")
        assertEquals(listOf("Wi-Fi", "Mail", "Asmaa Gouda", "Docs", "Signature"), HiddenLabels.resolve(keys, items))
    }

    @Test
    fun sameLabelsStayDistinctByKey() {
        val keys = listOf("app:com.example.mail/.Main:0", "app:com.example.work/.Main:10")
        assertEquals(listOf("Mail", "Mail"), HiddenLabels.resolve(keys, items))
    }

    @Test
    fun unknownKeysFallBackToThePackageStyleName() {
        assertEquals(listOf("com.gone.app"), HiddenLabels.resolve(listOf("app:com.gone.app/.Main:0"), items))
        assertEquals(listOf("xyz"), HiddenLabels.resolve(listOf("contact:xyz"), items))
    }

    @Test
    fun aBlankLabelDoesNotHideTheFallback() {
        assertEquals(listOf("blank"), HiddenLabels.resolve(listOf("blank"), items))
    }

    @Test
    fun noKeysGiveNoLabels() {
        assertEquals(emptyList<String>(), HiddenLabels.resolve(emptyList(), items))
    }

    @Test
    fun theFirstItemWithAKeyWins() {
        val twice = listOf(
            SearchItem("app:x", ItemKind.APP, "First"),
            SearchItem("app:x", ItemKind.APP, "Second")
        )
        assertEquals(listOf("First"), HiddenLabels.resolve(listOf("app:x"), twice))
    }
}
