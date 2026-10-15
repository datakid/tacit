package app.tacit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PathNamesTest {

    @Test
    fun plainPaths() {
        assertEquals("a.pdf", PathNames.name("/storage/emulated/0/Download/a.pdf"))
        assertEquals("~/Download", PathNames.folder("/storage/emulated/0/Download/a.pdf"))
        assertEquals("~", PathNames.folder("/storage/emulated/0/a.pdf"))
    }

    @Test
    fun documentUris() {
        val document = "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Freport%20final%2B1.pdf"
        assertEquals("report final+1.pdf", PathNames.name(document))
        assertEquals("", PathNames.folder(document))
    }

    @Test
    fun treeUris() {
        assertEquals("Download", PathNames.name("content://com.android.externalstorage.documents/tree/primary%3ADownload"))
    }
}
