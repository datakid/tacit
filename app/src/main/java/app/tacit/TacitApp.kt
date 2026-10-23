package app.tacit

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class TacitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLog()
        Graph.init(this)
    }

    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashLog.write(filesDir, error)
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}

object CrashLog {
    fun write(directory: File, error: Throwable) {
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            File(directory, TacitApp.CRASH_FILE).writeText("${System.currentTimeMillis()}\n${BuildInfo.describe()}\n$trace")
        }
    }
}

object BuildInfo {
    fun describe(): String =
        "Tacit ${BuildConfigProxy.versionName} on Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
}

object BuildConfigProxy {
    val versionName: String
        get() = runCatching {
            val context = Graph.context
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
}
