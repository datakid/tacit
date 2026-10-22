package app.tacit.files

import app.tacit.fixtures.FileFixtures
import java.util.Random

object FileQueryFixtures {

    private val shortFragments = listOf("a", "e", "r", "ph", "re", "in", "20", "19", "2", "no", "ta", "xx", "q", "z9")

    fun queries(count: Int, seed: Long = 7L): List<String> {
        val random = Random(seed)
        val english = FileFixtures.englishWords
        val arabic = FileFixtures.arabicWords
        val extensions = FileFixtures.extensions
        val folders = listOf("download", "documents", "dcim", "pictures", "music", "movies", "whatsapp", "telegram", "books", "android", "2021", "تقرير")
        fun word(): String = if (random.nextInt(100) < 12) arabic[random.nextInt(arabic.size)] else english[random.nextInt(english.size)]
        fun fragment(): String {
            val full = word()
            val start = random.nextInt(full.length)
            val end = start + 1 + random.nextInt(full.length - start)
            return full.substring(start, end)
        }
        val result = ArrayList<String>(count)
        for (n in 0 until count) {
            val query = when (n % 15) {
                0, 1, 2 -> word()
                3 -> fragment()
                4 -> shortFragments[random.nextInt(shortFragments.size)]
                5 -> word() + " " + word()
                6 -> word() + " " + fragment()
                7 -> word() + " " + extensions[random.nextInt(extensions.size)]
                8 -> word() + " ext:" + extensions[random.nextInt(extensions.size)]
                9 -> "*." + extensions[random.nextInt(extensions.size)]
                10 -> word() + " in:" + folders[random.nextInt(folders.size)]
                11 -> "ext:" + extensions[random.nextInt(extensions.size)] + " in:" + folders[random.nextInt(folders.size)]
                12 -> word().replaceFirstChar { it.uppercase() } + " " + (2019 + random.nextInt(8))
                13 -> word() + " " + word() + " " + word()
                else -> "zzq" + word()
            }
            result.add(query)
        }
        return result
    }
}
