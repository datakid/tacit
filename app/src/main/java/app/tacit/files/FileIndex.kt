package app.tacit.files

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.MediaStore
import app.tacit.Prefs
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

object StorageAccess {
    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
}

class FileIndex(
    private val context: Context,
    private val prefs: Prefs,
    private val onFailure: (Throwable) -> Unit = {}
) {

    private val file = File(context.filesDir, "files.bin")
    private val scanHandler by lazy { Handler(HandlerThread("tacit-files-scan", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper) }
    private val searchHandler by lazy { Handler(HandlerThread("tacit-files-search", Process.THREAD_PRIORITY_DEFAULT).apply { start() }.looper) }
    private val main = Handler(Looper.getMainLooper())
    private val searchGeneration = AtomicInteger()
    private val scanGeneration = AtomicInteger()
    private val lock = Any()
    private val publishLock = Any()
    private val listeners = ArrayList<(ScanState) -> Unit>()
    private val waiters = ArrayList<() -> Unit>()
    private var queued = false
    private var running = false
    private var rerun = false

    @Volatile
    private var table: FileTable? = null

    @Volatile
    var state: ScanState = ScanState.Idle
        private set

    val busy: Boolean get() = synchronized(lock) { queued || running }

    fun hasFullAccess(): Boolean = StorageAccess.hasAllFiles() ||
        (Build.VERSION.SDK_INT < 30 &&
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

    fun hasAccess(): Boolean = hasFullAccess() || GrantedTrees.list(context).isNotEmpty()

    fun hasTable(): Boolean = (table?.count ?: 0) > 0

    fun count(): Int = table?.count ?: (state as? ScanState.Done)?.count ?: 0

    fun addStateListener(listener: (ScanState) -> Unit) {
        listeners.add(listener)
    }

    fun removeStateListener(listener: (ScanState) -> Unit) {
        listeners.remove(listener)
    }

    fun search(query: String, limit: Int, callback: (List<FileHit>) -> Unit) {
        val token = searchGeneration.incrementAndGet()
        searchHandler.post {
            if (token != searchGeneration.get()) return@post
            val current = loadedTable()
            val hits = FileMatcher.search(current, query, limit) { token != searchGeneration.get() }
            if (token == searchGeneration.get()) callback(hits)
        }
    }

    fun cancel() {
        searchGeneration.incrementAndGet()
    }

    fun preload() {
        if (table != null) return
        searchHandler.post { loadedTable() }
    }

    fun summarize() {
        scanHandler.post {
            if (state !== ScanState.Idle || !file.exists()) return@post
            val header = readHeader()
            if (header != null) announce(ScanState.Done(header.entryCount, header.createdAt, header.truncated))
        }
    }

    fun rescan(done: (() -> Unit)? = null) {
        synchronized(lock) {
            if (done != null) waiters.add(done)
            if (running) {
                rerun = true
                scanGeneration.incrementAndGet()
                return
            }
            if (queued) return
            queued = true
        }
        scanHandler.post(::runQueued)
    }

    fun maybeRefresh() {
        scanHandler.post {
            if (!prefs.filesEnabled || !hasAccess()) return@post
            synchronized(lock) { if (queued || running) return@post }
            val header = readHeader()
            val finishedAt = (state as? ScanState.Done)?.at ?: 0L
            if (RefreshPolicy.shouldRefresh(header, currentStamps(), System.currentTimeMillis(), finishedAt)) rescan()
        }
    }

    fun clear() {
        synchronized(publishLock) {
            scanGeneration.incrementAndGet()
            table = FileTable.EMPTY
            assign(ScanState.Idle)
        }
        scanHandler.post { file.delete() }
    }

    private fun assign(next: ScanState) {
        synchronized(publishLock) { state = next }
        main.post {
            val now = state
            for (listener in ArrayList(listeners)) listener(now)
        }
    }

    private fun assignFor(generation: Int, next: ScanState) {
        synchronized(publishLock) {
            if (generation == scanGeneration.get()) assign(next)
        }
    }

    private fun announce(next: ScanState) {
        synchronized(publishLock) {
            if (state === ScanState.Idle) assign(next)
        }
    }

    private fun runQueued() {
        synchronized(lock) {
            queued = false
            running = true
        }
        try {
            scanOnce()
        } catch (error: Throwable) {
            onFailure(error)
            val kept = table
            val settled = if (kept != null && kept.count > 0) ScanState.Done(kept.count, System.currentTimeMillis(), false) else ScanState.Idle
            synchronized(publishLock) { assign(settled) }
        } finally {
            var again = false
            var finished: List<() -> Unit> = emptyList()
            synchronized(lock) {
                running = false
                again = rerun
                rerun = false
                if (again) {
                    queued = true
                } else {
                    finished = ArrayList(waiters)
                    waiters.clear()
                }
            }
            if (again) scanHandler.post(::runQueued) else for (callback in finished) callback()
        }
    }

    private fun scanOnce() {
        if (!prefs.filesEnabled || !hasAccess()) return
        val previous = readHeader()
        val generation = scanGeneration.incrementAndGet()
        val startedAt = System.currentTimeMillis()
        assignFor(generation, ScanState.Scanning(0, startedAt))
        val stamps = currentStamps()
        val loaded = table
        val cold = (loaded == null || loaded.count == 0) && !file.exists()
        val plan = plan(previous, cold)
        var baseline = 0
        var lastReport = SystemClock.elapsedRealtime()
        var lastPartialCount = 0
        var lastPartialAt = lastReport
        var snapshotCost = 0L
        for ((index, strategy) in plan.withIndex()) {
            val builder = TableBuilder(TableBuilder.MAX_FILES, { scanGeneration.get() == generation }) { progress ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastReport >= PROGRESS_MS) {
                    lastReport = now
                    assignFor(generation, ScanState.Scanning(maxOf(baseline, progress.count), startedAt))
                }
                val grown = progress.count - lastPartialCount
                val due = grown >= PARTIAL_ENTRIES || (grown > 0 && now - lastPartialAt >= maxOf(PROGRESS_MS, snapshotCost * 4))
                if (cold && baseline == 0 && due) {
                    val begun = SystemClock.elapsedRealtime()
                    val partial = progress.snapshot()
                    synchronized(publishLock) { if (generation == scanGeneration.get()) table = partial }
                    snapshotCost = SystemClock.elapsedRealtime() - begun
                    lastPartialCount = progress.count
                    lastPartialAt = SystemClock.elapsedRealtime()
                }
            }
            strategy.scan(builder)
            if (generation != scanGeneration.get()) return
            val built = builder.snapshot()
            val last = index == plan.lastIndex
            val finishedAt = System.currentTimeMillis()
            val walked = strategy.id != ScanStrategyIds.MEDIA_STORE
            val identity = if (last && plan.size > 1) ScanStrategyIds.HYBRID else strategy.id
            val header = IndexHeader(built.count, finishedAt, if (walked) finishedAt else previous?.verifiedAt ?: 0L, identity, builder.truncated, stamps)
            if (!publish(generation, built, header)) return
            baseline = built.count
            if (last) assignFor(generation, ScanState.Done(built.count, finishedAt, builder.truncated))
            else assignFor(generation, ScanState.Scanning(built.count, startedAt))
        }
    }

    private fun plan(previous: IndexHeader?, cold: Boolean): List<ScanStrategy> {
        val hidden = prefs.filesHidden
        if (StorageAccess.hasAllFiles()) {
            val walk = NioWalkStrategy(roots(), hidden)
            val media = MediaStoreStrategy(context, hidden)
            val now = System.currentTimeMillis()
            if (cold) return listOf(media, walk)
            if (!MEDIA_STORE_DEFAULT) return listOf(walk)
            return if (RefreshPolicy.needsFullWalk(previous, now)) listOf(media, walk) else listOf(media)
        }
        if (hasFullAccess()) return listOf(NioWalkStrategy(roots(), hidden))
        return listOf(TreeStrategy(context.contentResolver, GrantedTrees.list(context), hidden))
    }

    private fun publish(generation: Int, built: FileTable, header: IndexHeader): Boolean {
        synchronized(publishLock) {
            if (generation != scanGeneration.get()) return false
            table = built
        }
        persist(built, header)
        return true
    }

    private fun currentStamps(): List<VolumeStamp> {
        if (Build.VERSION.SDK_INT < 30 || !StorageAccess.hasAllFiles()) return emptyList()
        return runCatching {
            MediaStore.getExternalVolumeNames(context).sorted().map {
                VolumeStamp(it, MediaStore.getVersion(context, it), MediaStore.getGeneration(context, it))
            }
        }.getOrDefault(emptyList())
    }

    private fun roots(): List<File> {
        val storage = context.getSystemService(StorageManager::class.java)
        val result = ArrayList<File>()
        if (Build.VERSION.SDK_INT >= 30) {
            for (volume in storage.storageVolumes) {
                val dir = volume.directory ?: continue
                if (dir.canRead()) result.add(dir)
            }
        }
        if (result.isEmpty()) result.add(Environment.getExternalStorageDirectory())
        return result
    }

    private fun loadedTable(): FileTable {
        table?.let { return it }
        val loaded = loadFromDisk()
        return synchronized(publishLock) {
            if (table == null) table = loaded
            table!!
        }
    }

    private fun loadFromDisk(): FileTable {
        if (!file.exists()) return FileTable.EMPTY
        val loaded = runCatching {
            DataInputStream(BufferedInputStream(file.inputStream(), BUFFER)).use { IndexFormat.read(it) }
        }.getOrNull() ?: return FileTable.EMPTY
        val header = loaded.header
        announce(ScanState.Done(loaded.table.count, header?.createdAt ?: file.lastModified(), header?.truncated ?: false))
        return loaded.table
    }

    private fun readHeader(): IndexHeader? {
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(BufferedInputStream(file.inputStream(), 4096)).use { IndexFormat.readHeader(it) }
        }.getOrNull()
    }

    private fun persist(data: FileTable, header: IndexHeader) {
        runCatching {
            val temp = File(file.parentFile, "files.bin.tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream(), BUFFER)).use { IndexFormat.write(it, data, header) }
            temp.renameTo(file)
        }
    }

    companion object {
        private const val BUFFER = 1 shl 17
        private const val PROGRESS_MS = 250L
        private const val PARTIAL_ENTRIES = 25_000
        private const val MEDIA_STORE_DEFAULT = true
    }
}
