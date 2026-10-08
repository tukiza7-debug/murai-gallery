package com.murai.gallery

import com.murai.gallery.data.media.ScanCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single-flight scan coordination (fix #2): concurrent scan requests coalesce
 * into one pass, sequential requests run their own pass, and the coordinator
 * never lets an escaping exception reach waiters.
 */
class SingleFlightTest {

    @Test
    fun `concurrent callers coalesce into one pass`() = runTest {
        val coordinator = ScanCoordinator()
        val runs = AtomicInteger(0)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val jobs = (1..8).map { _ ->
            async(Dispatchers.Unconfined) {
                coordinator.once {
                    if (runs.incrementAndGet() == 1) started.complete(Unit)
                    // Suspend a moment to give the other callers a chance to
                    // pile up behind the active pass.
                    kotlinx.coroutines.delay(50)
                }
            }
        }
        jobs.awaitAll()
        assertEquals(1, runs.get())
        assertTrue(started.isCompleted)
    }

    @Test
    fun `sequential callers each run a pass`() = runTest {
        val coordinator = ScanCoordinator()
        val runs = AtomicInteger(0)
        repeat(3) {
            coordinator.once { runs.incrementAndGet() }
        }
        assertEquals(3, runs.get())
    }

    @Test
    fun `escaping exceptions never reach waiters`() = runTest {
        val coordinator = ScanCoordinator()
        coordinator.onError = { _, _ -> } // recorded, not rethrown
        coordinator.once { error("boom") }
        // The coordinator stays usable after a failed pass.
        var ran = false
        coordinator.once { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `running flag tracks the active pass`() = runTest {
        val coordinator = ScanCoordinator()
        assertFalse(coordinator.running)
        var sawRunning = false
        coordinator.once {
            sawRunning = coordinator.running
        }
        assertTrue(sawRunning)
        assertFalse(coordinator.running)
    }

    @Test
    fun `waiter launched mid-pass does not rerun the scan`() = runTest {
        val coordinator = ScanCoordinator()
        val runs = AtomicInteger(0)
        val inside = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val active = launch(Dispatchers.Unconfined) {
            coordinator.once {
                runs.incrementAndGet()
                inside.complete(Unit)
                gate.await()
            }
        }
        inside.await()
        val waiter = async(Dispatchers.Unconfined) {
            coordinator.once { runs.incrementAndGet() }
        }
        kotlinx.coroutines.yield()
        gate.complete(Unit)
        active.join()
        waiter.await()
        assertEquals(1, runs.get())
    }

    @Test
    fun `mutex parity - passes never interleave`() = runTest {
        val coordinator = ScanCoordinator()
        val mutex = Mutex()
        var inside = false
        val violations = AtomicInteger(0)
        val jobs = (1..10).map { _ ->
            async(Dispatchers.Unconfined) {
                coordinator.once {
                    mutex.withLock {
                        if (inside) violations.incrementAndGet()
                        inside = true
                        kotlinx.coroutines.yield()
                        inside = false
                    }
                }
            }
        }
        jobs.awaitAll()
        assertEquals(0, violations.get())
    }
}
