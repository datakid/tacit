package app.tacit.core

import app.tacit.fixtures.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerListTest {

    private fun app(label: String, key: String = "app:$label") = SearchItem(key, ItemKind.APP, label)

    private fun build(vararg labels: String): DrawerList = DrawerList.build(labels.map { app(it) }.toTypedArray())

    private fun labels(list: DrawerList) = list.items.map { it.label }

    @Test
    fun appsAreSortedIgnoringCaseAndAccents() {
        val list = build("zoom", "Éclair", "banana", "Apple", "écoute")
        assertEquals(listOf("Apple", "banana", "Éclair", "écoute", "zoom"), labels(list))
    }

    @Test
    fun onlyAppsAreListed() {
        val items = arrayOf(
            app("Maps"),
            SearchItem("contact:1", ItemKind.CONTACT, "Aaron"),
            SearchItem("setting:1", ItemKind.SETTING, "Wi-Fi"),
            app("Camera")
        )
        assertEquals(listOf("Camera", "Maps"), labels(DrawerList.build(items)))
    }

    @Test
    fun sectionsFollowFirstLetters() {
        val list = build("Camera", "Calendar", "Maps", "Clock", "Zoom")
        assertEquals("CMZ", String(list.letters))
        assertEquals(listOf(0, 3, 4), list.starts.toList())
        assertEquals(0, list.sectionOf(0))
        assertEquals(0, list.sectionOf(2))
        assertEquals(1, list.sectionOf(3))
        assertEquals(2, list.sectionOf(4))
        assertEquals(3, list.positionOf(1))
    }

    @Test
    fun digitsAndSymbolsShareTheHashSectionAtTheTop() {
        val list = build("2048", "Zoom", "(Beta) Notes", "1Password", "!Wow")
        assertEquals("#BWZ", String(list.letters))
        assertEquals(listOf("1Password", "2048"), labels(list).take(2))
        assertEquals(listOf(0, 2, 3, 4), list.starts.toList())
    }

    @Test
    fun leadingPunctuationDoesNotChangeTheLetter() {
        assertEquals("beta) notes", DrawerList.sortKey(app("(Beta) Notes")))
        assertEquals('B', DrawerList.letterOf(DrawerList.sortKey(app("(Beta) Notes"))))
        assertEquals(DrawerList.OTHER, DrawerList.letterOf(""))
    }

    @Test
    fun arabicLabelsFormTheirOwnSectionsAfterLatin() {
        val list = build("Zoom", "بنك", "أسماء", "Chrome", "اختبار")
        assertEquals(listOf("Chrome", "Zoom", "اختبار", "أسماء", "بنك"), labels(list))
        assertEquals("CZاب", String(list.letters))
    }

    @Test
    fun equalNamesKeepAStableOrderByKey() {
        val first = app("Chrome", "app:work/chrome")
        val second = app("Chrome", "app:main/chrome")
        val list = DrawerList.build(arrayOf(first, second))
        assertEquals(listOf("app:main/chrome", "app:work/chrome"), list.items.map { it.key })
    }

    @Test
    fun emptyInputGivesAnEmptyList() {
        val list = DrawerList.build(emptyArray())
        assertEquals(0, list.size)
        assertEquals(-1, list.sectionOf(0))
        assertEquals(0, list.positionOf(3))
    }

    @Test
    fun everyPositionBelongsToTheSectionThatContainsIt() {
        val list = DrawerList.build(Fixtures.apps(1000).toTypedArray())
        assertEquals(1000, list.size)
        for (position in 0 until list.size) {
            val section = list.sectionOf(position)
            assertTrue(list.starts[section] <= position)
            if (section + 1 < list.starts.size) assertTrue(position < list.starts[section + 1])
            assertEquals(list.letters[section], DrawerList.letterOf(DrawerList.sortKey(list.items[position])))
        }
    }

    @Test
    fun sectionsAreOrderedAndNeverRepeat() {
        val list = DrawerList.build(Fixtures.apps(1000).toTypedArray())
        val letters = String(list.letters)
        assertEquals(letters.toSet().size, letters.length)
        assertEquals(letters.toList().sorted(), letters.toList())
    }

    @Test
    fun largeListBuildsQuickly() {
        val source = Fixtures.apps(2000).toTypedArray()
        DrawerList.build(source)
        val start = System.nanoTime()
        DrawerList.build(source)
        val millis = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("took $millis ms", millis < 250.0)
    }

    @Test
    fun densityLooksUpByKeyAndFallsBackToComfortable() {
        assertEquals(RowDensity.COMPACT, RowDensity.of("compact"))
        assertEquals(RowDensity.COMFORTABLE, RowDensity.of("comfortable"))
        assertEquals(RowDensity.COMFORTABLE, RowDensity.of("huge"))
        assertEquals(RowDensity.COMFORTABLE, RowDensity.of(null))
    }

    @Test
    fun comfortableIsRoomierThanCompactInEveryDimension() {
        val roomy = RowDensity.COMFORTABLE
        val tight = RowDensity.COMPACT
        assertTrue(roomy.rowDp > tight.rowDp)
        assertTrue(roomy.slotDp > tight.slotDp)
        assertTrue(roomy.iconDp > tight.iconDp)
        assertTrue(roomy.titleSp > tight.titleSp)
    }

    @Test
    fun everyDensityFitsItsIconInsideTheSlotAndTheSlotInsideTheRow() {
        for (density in RowDensity.entries) {
            assertTrue(density.iconDp + 8 <= density.slotDp)
            assertTrue(density.slotDp + 16 <= density.rowDp)
            assertTrue(density.slotDp - 2 * density.kindPadDp >= 24)
        }
    }

    @Test
    fun densityKeysAreUnique() {
        assertEquals(RowDensity.entries.size, RowDensity.entries.map { it.key }.toSet().size)
    }
}
