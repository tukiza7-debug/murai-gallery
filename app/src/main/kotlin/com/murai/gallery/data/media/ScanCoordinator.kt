package com.murai.gallery.data.media

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * Single-flight guard around the media library scan.
 *
 * ScanWorker, the pull-to-refresh path in GalleryViewModel and the permission
 * callback can all ask for a scan at the same moment. Without coordination the
 * passes interleave, cursor batches overwrite each other and — worst of all —
 * deleteStale() from pass A runs while pass B is still writing rows, so every
 * freshly written row looks stale and the library is wiped. This coordinator
 * lets exactly one scan run; concurrent callers await the same pass instead of
 * starting their own.
 */
class ScanCoordinator {

    private val mutex = Mutex()
    private val current = AtomicReference<Pass?>(null)

    private class Pass {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        @Volatile var running = true
    }

    /** True while a scan pass is executing (drives the "Scanning…" badge). */
    val running: Boolean get() = current.get()?.running == true

    /** Set by the app so escaped exceptions still reach the on-device log. */
    @Volatile
    var onError: ((String, Throwable) -> Unit)? = null

    /**
     * Runs [block] if no scan is active; concurrent callers suspend until the
     * active pass finishes and then return without re-running it. Sequential
     * callers (arriving after completion) start a fresh pass.
     *
     * The block itself must never throw — MediaScanner.scan catches internally
     * and reports via ScanEvent.Failed; any escaping exception is logged and
     * swallowed here so waiters never crash.
     */
    suspend fun once(block: suspend () -> Unit) {
        val observed = current.get()
        if (observed != null && observed.running) {
            runCatching { observed.done.await() }
            return
        }
        val mine = mutex.withLock {
            val again = current.get()
            if (again != null && again.running) {
                null // lost the race — wait for the winner below
            } else {
                Pass().also { current.set(it) }
            }
        }
        if (mine == null) {
            current.get()?.let { runCatching { it.done.await() } }
            return
        }
        try {
            block()
        } catch (t: Throwable) {
            // Never propagate: scanning is best-effort, waiters expect completion.
            onError?.invoke("scan-coordinator", t)
        } finally {
            mine.running = false
            mine.done.complete(Unit)
        }
    }

    companion object {
        /**
         * Pure decision helper (unit tested): deleteStale is only allowed after
         * a scan pass that read the cursor to the end with a permission grant
         * and a non-null cursor. Any partial or failed pass must keep every
         * cached row intact.
         */
        fun allowDeleteStale(cursorCompleted: Boolean, hasPermission: Boolean, cursorNull: Boolean): Boolean =
            cursorCompleted && hasPermission && !cursorNull
    }
}
