package app.tacit

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.io.File

class TacitTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        runCatching { File(targetContext.filesDir, Welcome.MARKER).createNewFile() }
        super.onCreate(arguments)
    }
}
