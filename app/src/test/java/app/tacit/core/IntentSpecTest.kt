package app.tacit.core

import app.tacit.core.IntentSpec.Problem
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentSpecTest {

    private val own = "app.tacit"

    private fun problems(spec: IntentSpec, ownPackage: String = own) = spec.validate(ownPackage)

    private fun view(data: String) = IntentSpec(action = "android.intent.action.VIEW", data = data)

    private fun extras(count: Int) = (1..count).map { IntentExtra("k$it", ExtraType.STRING, "v") }

    @Test
    fun anEmptySpecHasNothingToLaunch() {
        assertEquals(listOf(Problem.NOTHING_TO_LAUNCH), problems(IntentSpec()))
    }

    @Test
    fun anActionAloneIsEnough() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "android.settings.WIFI_SETTINGS")))
    }

    @Test
    fun aComponentAloneIsEnough() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(component = "com.example.app/.MainActivity")))
    }

    @Test
    fun actionsWithSpacesOrControlCharactersAreRejected() {
        assertEquals(listOf(Problem.BAD_ACTION), problems(IntentSpec(action = "android intent")))
        assertEquals(listOf(Problem.BAD_ACTION), problems(IntentSpec(action = "a\u0007b")))
        assertEquals(listOf(Problem.BAD_ACTION), problems(IntentSpec(action = "x".repeat(201))))
    }

    @Test
    fun dataNeedsASchemeAndSomethingAfterIt() {
        assertEquals(listOf(Problem.BAD_DATA), problems(view("example.com")))
        assertEquals(listOf(Problem.BAD_DATA), problems(view("https:")))
        assertEquals(listOf(Problem.BAD_DATA), problems(view("https://exa mple.com")))
        assertEquals(listOf(Problem.BAD_DATA), problems(view("1http://x")))
        assertEquals(emptyList<Problem>(), problems(view("https://example.com/a?b=c#d")))
        assertEquals(emptyList<Problem>(), problems(view("tel:+201001234567")))
        assertEquals(emptyList<Problem>(), problems(view("package:com.example.app")))
    }

    @Test
    fun fileDataIsBlockedInAnyCase() {
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("file:///sdcard/x.txt")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("FILE:///sdcard/x.txt")))
    }

    @Test
    fun contentDataFromTheOwnProviderIsBlocked() {
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://app.tacit.files/shared/a.txt")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("CONTENT://APP.TACIT.FILES/shared/a.txt")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://0@app.tacit.files/shared/a.txt")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://app%2Etacit.files/shared/a.txt")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://app.tacit.files")))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://app.tacit.files?x=1")))
    }

    @Test
    fun theBlockedAuthorityFollowsTheOwnPackage() {
        assertEquals(emptyList<Problem>(), problems(view("content://app.tacit.files/a"), "com.example.debug"))
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://com.example.debug.files/a"), "com.example.debug"))
    }

    @Test
    fun contentDataFromOtherProvidersIsAllowed() {
        assertEquals(emptyList<Problem>(), problems(view("content://com.android.contacts/contacts/1")))
        assertEquals(emptyList<Problem>(), problems(view("content://app.tacit.filesystem/a")))
        assertEquals(emptyList<Problem>(), problems(view("content://x/app.tacit.files/a")))
    }

    @Test
    fun mimeTypesNeedTwoPartsOrWildcards() {
        for (good in listOf("text/plain", "image/*", "*/*", "application/vnd.android.package-archive", "audio/x-wav", "text/x-c++src")) {
            assertEquals(good, emptyList<Problem>(), problems(IntentSpec(action = "a.b", mime = good)))
        }
        for (bad in listOf("text", "text/", "/plain", "text plain", "a/b/c", "text/pla\nin")) {
            assertEquals(bad, listOf(Problem.BAD_MIME), problems(IntentSpec(action = "a.b", mime = bad)))
        }
    }

    @Test
    fun categoriesMustBeSingleWords() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "a.b", categories = listOf("android.intent.category.DEFAULT", "x"))))
        assertEquals(listOf(Problem.BAD_CATEGORY), problems(IntentSpec(action = "a.b", categories = listOf("two words"))))
        assertEquals(listOf(Problem.BAD_CATEGORY), problems(IntentSpec(action = "a.b", categories = listOf("a,b"))))
        assertEquals(listOf(Problem.BAD_CATEGORY), problems(IntentSpec(action = "a.b", categories = listOf(""))))
    }

    @Test
    fun componentsUseThePackageSlashClassShape() {
        for (good in listOf("com.example.app/.Main", "com.example.app/com.example.app.Main", "com.example.app/com.example.Outer\$Inner", "a/b")) {
            assertEquals(good, emptyList<Problem>(), problems(IntentSpec(component = good)))
        }
        for (bad in listOf("com.example.app", "com.example.app/", "/Main", "com example/Main", "com..x/Main", "a/b c", "a/b/c")) {
            assertEquals(bad, listOf(Problem.BAD_COMPONENT), problems(IntentSpec(component = bad)))
        }
    }

    @Test
    fun theOwnAppCannotBeTargeted() {
        assertEquals(listOf(Problem.OWN_APP), problems(IntentSpec(component = "app.tacit/.ui.SettingsActivity")))
        assertEquals(listOf(Problem.OWN_APP), problems(IntentSpec(action = "a.b", component = "app.tacit/app.tacit.ui.SettingsActivity")))
    }

    @Test
    fun aPackageThatOnlyStartsLikeTheOwnOneIsFine() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(component = "app.tacit.other/.Main")))
        assertEquals(emptyList<Problem>(), problems(IntentSpec(component = "app.tacitx/.Main")))
    }

    @Test
    fun theOwnPackageIsTheOneThatIsPassedIn() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(component = "app.tacit/.Main"), "app.tacit.debug"))
        assertEquals(listOf(Problem.OWN_APP), problems(IntentSpec(component = "app.tacit.debug/.Main"), "app.tacit.debug"))
    }

    @Test
    fun twentyExtrasAreAllowedAndTwentyOneAreNot() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "a.b", extras = extras(20))))
        assertEquals(listOf(Problem.TOO_MANY_EXTRAS), problems(IntentSpec(action = "a.b", extras = extras(21))))
    }

    @Test
    fun extraKeysMustBeNonEmptyAndShort() {
        val long = "k".repeat(100)
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra(long, ExtraType.STRING, "v")))))
        assertEquals(listOf(Problem.EXTRA_KEY_TOO_LONG), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra(long + "k", ExtraType.STRING, "v")))))
        assertEquals(listOf(Problem.BAD_EXTRA_KEY), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("", ExtraType.STRING, "v")))))
        assertEquals(listOf(Problem.BAD_EXTRA_KEY), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("a\nb", ExtraType.STRING, "v")))))
    }

    @Test
    fun textValuesStopAtOneThousandCharacters() {
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("t", ExtraType.STRING, "x".repeat(1000))))))
        assertEquals(listOf(Problem.EXTRA_TEXT_TOO_LONG), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("t", ExtraType.STRING, "x".repeat(1001))))))
        assertEquals(emptyList<Problem>(), problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("t", ExtraType.STRING, "")))))
    }

    @Test
    fun numbersMustParseForTheirType() {
        fun check(type: ExtraType, value: String) = problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("n", type, value))))
        assertEquals(emptyList<Problem>(), check(ExtraType.INT, "-2147483648"))
        assertEquals(emptyList<Problem>(), check(ExtraType.INT, "42"))
        assertEquals(listOf(Problem.BAD_EXTRA_NUMBER), check(ExtraType.INT, "2147483648"))
        assertEquals(listOf(Problem.BAD_EXTRA_NUMBER), check(ExtraType.INT, "4.5"))
        assertEquals(listOf(Problem.BAD_EXTRA_NUMBER), check(ExtraType.INT, ""))
        assertEquals(emptyList<Problem>(), check(ExtraType.LONG, "9223372036854775807"))
        assertEquals(listOf(Problem.BAD_EXTRA_NUMBER), check(ExtraType.LONG, "9223372036854775808"))
        assertEquals(listOf(Problem.BAD_EXTRA_NUMBER), check(ExtraType.LONG, "ten"))
    }

    @Test
    fun booleansAreTrueOrFalseInAnyCase() {
        fun check(value: String) = problems(IntentSpec(action = "a.b", extras = listOf(IntentExtra("b", ExtraType.BOOLEAN, value))))
        assertEquals(emptyList<Problem>(), check("true"))
        assertEquals(emptyList<Problem>(), check("FALSE"))
        assertEquals(listOf(Problem.BAD_EXTRA_BOOLEAN), check("yes"))
        assertEquals(listOf(Problem.BAD_EXTRA_BOOLEAN), check("1"))
        assertEquals(listOf(Problem.BAD_EXTRA_BOOLEAN), check(""))
    }

    @Test
    fun everyProblemIsReportedOnceInTheOrderOfTheFields() {
        val spec = IntentSpec(
            action = "bad action",
            data = "file:///x",
            mime = "text",
            categories = listOf("a b"),
            component = "app.tacit/.X",
            extras = listOf(IntentExtra("", ExtraType.INT, "x"), IntentExtra("", ExtraType.INT, "y"))
        )
        assertEquals(
            listOf(Problem.BAD_ACTION, Problem.BLOCKED_DATA, Problem.BAD_MIME, Problem.BAD_CATEGORY, Problem.OWN_APP,
                Problem.BAD_EXTRA_KEY, Problem.BAD_EXTRA_NUMBER),
            problems(spec)
        )
    }

    @Test
    fun aFullyFilledSpecIsValid() {
        val spec = IntentSpec(
            action = "android.intent.action.SEND",
            data = "https://example.com/a",
            mime = "text/plain",
            categories = listOf("android.intent.category.DEFAULT"),
            component = "com.example.app/.ShareActivity",
            extras = listOf(
                IntentExtra("android.intent.extra.TEXT", ExtraType.STRING, "hello"),
                IntentExtra("count", ExtraType.INT, "3"),
                IntentExtra("big", ExtraType.LONG, "10000000000"),
                IntentExtra("flag", ExtraType.BOOLEAN, "true")
            )
        )
        assertEquals(emptyList<Problem>(), problems(spec))
    }

    @Test
    fun validatingNeverThrowsOnOddInput() {
        val odd = listOf("", " ", "%", "%zz", "content://%", "content://@", "content://@@", "\u0000", "é".repeat(500))
        for (text in odd) {
            problems(IntentSpec(action = text, data = text, mime = text, categories = listOf(text), component = text,
                extras = listOf(IntentExtra(text, ExtraType.LONG, text))))
        }
        assertEquals(listOf(Problem.BLOCKED_DATA), problems(view("content://%zz@app.tacit.files/x")))
    }

    @Test
    fun categoriesParseFromCommaSeparatedText() {
        assertEquals(listOf("a", "b", "c"), IntentSpec.parseCategories(" a, b ,,c ,a"))
        assertEquals(emptyList<String>(), IntentSpec.parseCategories("  ,  "))
        assertEquals("a, b", IntentSpec.categoriesText(listOf("a", "b")))
        assertEquals(listOf("a", "b"), IntentSpec.parseCategories(IntentSpec.categoriesText(listOf("a", "b"))))
    }

    @Test
    fun theSummaryPrefersTheActionThenTheComponent() {
        assertEquals("a.b", IntentSpec(action = "a.b", component = "x/y").summary())
        assertEquals("x/y", IntentSpec(component = "x/y").summary())
        assertEquals("", IntentSpec().summary())
    }
}
