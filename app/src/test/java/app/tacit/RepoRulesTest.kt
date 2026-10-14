package app.tacit

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
