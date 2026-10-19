package app.tacit

import android.app.Application
import android.os.Bundle
import android.os.StrictMode
import androidx.test.runner.AndroidJUnitRunner
import java.io.File

class TacitTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        runCatching { File(targetContext.filesDir, Welcome.MARKER).createNewFile() }
        super.onCreate(arguments)
    }

    override fun callApplicationOnCreate(app: Application?) {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
        super.callApplicationOnCreate(app)
    }
}
