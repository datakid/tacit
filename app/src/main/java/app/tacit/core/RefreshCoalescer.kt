package app.tacit.core

import java.util.concurrent.atomic.AtomicInteger

object RefreshSources {
    const val APPS = 1
    const val CONTACTS = 2
    const val SETTINGS = 4
    const val SNIPPETS = 8
    const val QUICKLINKS = 16
    const val ALL = 31

    fun expand(bits: Int): Int = if ((bits and APPS) != 0) bits or SETTINGS else bits
}

class RefreshCoalescer(
    private val delayMs: Long,
    private val schedule: (Long, Runnable) -> Unit,
    private val run: (Int) -> Unit
) {

    private val pending = AtomicInteger(0)
    private val task = Runnable { flush() }

    fun request(sources: Int) {
        if (sources == 0) return
        while (true) {
            val current = pending.get()
            if (pending.compareAndSet(current, current or sources)) {
                if (current == 0) schedule(delayMs, task)
                return
            }
        }
    }

    fun flush() {
        val bits = pending.getAndSet(0)
        if (bits != 0) run(bits)
    }
}
