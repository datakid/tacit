package app.tacit

import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Idle {

    private const val LIMIT_SECONDS = 5L

    fun await(): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS)
        val worker = CountDownLatch(1)
        Graph.worker.post { worker.countDown() }
        val workerDone = worker.await(LIMIT_SECONDS, TimeUnit.SECONDS)
        val main = CountDownLatch(1)
        Graph.main.post { main.countDown() }
        val remaining = (deadline - System.nanoTime()).coerceAtLeast(1L)
        val mainDone = main.await(remaining, TimeUnit.NANOSECONDS)
        val done = workerDone && mainDone
        if (!done) Log.w("TacitIdle", "idle wait timed out: worker=$workerDone main=$mainDone")
        return done
    }
}
