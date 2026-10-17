package app.tacit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepoRulesTest {

    private fun moduleDir(): File {
        val here = File("").absoluteFile
        return if (File(here, "src/main").exists()) here else File(here, "app")
    }

    private fun kotlinFiles(): List<File> {
        val module = moduleDir()
        val roots = listOf("src/main", "src/test", "src/androidTest", "src/sharedTest").map { File(module, it) } +
            File(module.parentFile, "benchmark/src")
        return roots.filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    @Test
    fun arabicTranslationCoversEveryTranslatableString() {
        val nameRegex = Regex("<string name=\"([a-z_0-9]+)\"(?![^>]*translatable=\"false\")")
        val english = nameRegex.findAll(File(moduleDir(), "src/main/res/values/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        val arabic = Regex("<string name=\"([a-z_0-9]+)\"").findAll(File(moduleDir(), "src/main/res/values-ar/strings.xml").readText())
            .map { it.groupValues[1] }.toSet()
        val missing = english - arabic
        assertTrue("Missing Arabic strings: $missing", missing.isEmpty())
    }

    @Test
    fun noCommentsInKotlinSources() {
        val offenders = ArrayList<String>()
        for (file in kotlinFiles()) {
            val stripped = stripStrings(file.readText())
            if (stripped.contains("//") || stripped.contains("/*")) offenders.add(file.name)
        }
        assertTrue("Comments found in: $offenders", offenders.isEmpty())
    }

    @Test
    fun releaseNotesExistForTheCurrentVersion() {
        val gradle = File(moduleDir(), "build.gradle.kts").readText()
        val version = Regex("versionName = \"([^\"]+)\"").find(gradle)!!.groupValues[1]
        val notes = File(moduleDir().parentFile, "docs/release-notes").list().orEmpty()
        assertTrue("No release notes for version $version", notes.any { it.startsWith("v$version") })
    }

    private fun repoRoot(): File = moduleDir().parentFile

    private fun gradleText(): String = File(moduleDir(), "build.gradle.kts").readText()

    private fun versionName(): String = Regex("versionName = \"([^\"]+)\"").find(gradleText())!!.groupValues[1]

    private fun versionCode(): Int = Regex("versionCode = (\\d+)").find(gradleText())!!.groupValues[1].toInt()

    @Test
    fun versionCodeFollowsTheVersionName() {
        val parts = versionName().substringBefore('-').split('.').map { it.toInt() }
        assertEquals(parts[0] * 10000 + parts[1] * 100 + parts[2], versionCode())
    }

    @Test
    fun licenceIsGplThree() {
        val text = File(repoRoot(), "LICENSE").readText()
        assertTrue(text.contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(text.contains("Version 3, 29 June 2007"))
    }

    @Test
    fun fdroidListingExistsInEnglishAndArabicWithinLimits() {
        val base = File(repoRoot(), "fastlane/metadata/android")
        for (locale in listOf("en-US", "ar")) {
            val dir = File(base, locale)
            fun read(name: String) = File(dir, name).readText().trimEnd('\n')
            assertTrue("$locale title", read("title.txt").length in 1..50)
            assertTrue("$locale short description", read("short_description.txt").length in 1..80)
            assertTrue("$locale full description", read("full_description.txt").length in 1..4000)
            val changelog = File(dir, "changelogs/${versionCode()}.txt")
            assertTrue("$locale changelog for ${versionCode()} is missing", changelog.exists())
            assertTrue("$locale changelog is over 500 bytes", changelog.readText().trimEnd('\n').toByteArray().size <= 500)
        }
    }

    @Test
    fun fdroidRecipeDraftIsPresentAndFree() {
        val recipe = File(repoRoot(), "docs/fdroid/app.tacit.yml").readText()
        assertTrue(recipe.contains("License: GPL-3.0-only"))
        assertTrue(recipe.contains("Repo: https://github.com/nickburser122/tacit.git"))
    }

    @Test
    fun signingKeysNeverEnterTheRepoOrTheWorkflow() {
        val root = repoRoot()
        val skipped = setOf(".git", ".gradle", "build", ".idea")
        val keyFiles = setOf("jks", "keystore", "p12", "pfx", "pem")
        val offenders = root.walkTopDown()
            .onEnter { it.name !in skipped }
            .filter { it.isFile && it.extension.lowercase() in keyFiles }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue("Key material in the repo: $offenders", offenders.isEmpty())
        val gradle = gradleText()
        for (word in listOf("storeFile", "storePassword", "keyPassword", "keyAlias")) {
            assertFalse("build.gradle.kts mentions $word", gradle.contains(word))
        }
        val workflow = File(root, ".github/workflows/android.yml").readText()
        assertFalse("The workflow reads repository secrets", workflow.contains("secrets."))
        val ignored = File(root, ".gitignore").readLines().map { it.trim() }
        for (pattern in listOf("*.jks", "*.keystore", "*.p12", "signing.properties")) {
            assertTrue(".gitignore misses $pattern", pattern in ignored)
        }
    }

    @Test
    fun manifestHasNoInternet() {
        val manifest = File(moduleDir(), "src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("ACCESS_NETWORK_STATE"))
        assertFalse(manifest.contains("QUERY_ALL_PACKAGES"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
    }

    @Test
    fun noRuntimeDependencies() {
        val gradle = File(moduleDir(), "build.gradle.kts").readText()
        val runtime = Regex("^\\s*implementation\\(", RegexOption.MULTILINE).findAll(gradle).count()
        assertTrue("Runtime dependencies must be justified in README", runtime == 0)
    }

    @Test
    fun noManifestOverlayAddsInternet() {
        val src = File(moduleDir(), "src")
        val manifests = src.walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList()
        for (manifest in manifests) {
            assertFalse(manifest.path, manifest.readText().contains("android.permission.INTERNET"))
        }
    }

    private fun stripStrings(source: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("\"\"\"", i) -> {
                    val end = source.indexOf("\"\"\"", i + 3)
                    i = if (end < 0) source.length else end + 3
                }
                source[i] == '"' -> {
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                }
                source[i] == '\'' && i + 2 < source.length && source[i + 1] == '\\' -> {
                    val end = source.indexOf('\'', i + 3)
                    i = if (end < 0) source.length else end + 1
                }
                source[i] == '\'' && i + 2 < source.length && source[i + 2] == '\'' -> i += 3
                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }
}
