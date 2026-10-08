package com.murai.gallery

import com.murai.gallery.util.ConsentBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consent bus semantics (fix #9): requests queue instead of overwriting,
 * every callback completes exactly once, and cancellation resolves with
 * false. The queue survives until a collector drains it, so an activity
 * recreation never loses a request.
 */
class ConsentBusTest {

    private fun enqueue(id: Long, results: MutableList<Pair<Long, Boolean>>): ConsentBus.Pending =
        ConsentBus.Pending(
            sender = null, // nullable for JVM tests; the activity guards nulls
            onResult = { granted -> results.add(id to granted) },
            id = id
        )

    @Test
    fun `queued requests are delivered in order`() = runTest {
        ConsentBus.resetForTest()
        val received = mutableListOf<Long>()
        val collector = launch(Dispatchers.Unconfined) {
            ConsentBus.requests.collect { received.add(it.id) }
        }
        val results = mutableListOf<Pair<Long, Boolean>>()
        val p1 = enqueue(11, results)
        val p2 = enqueue(12, results)
        val p3 = enqueue(13, results)
        ConsentBus.request(p1.sender, p1.onResult)
        ConsentBus.request(p2.sender, p2.onResult)
        ConsentBus.request(p3.sender, p3.onResult)
        withTimeout(5000) {
            while (received.size < 3) kotlinx.coroutines.yield()
        }
        assertEquals(listOf(1L, 2L, 3L), received)
        collector.cancel()
    }

    @Test
    fun `complete resolves exactly once`() {
        var count = 0
        val p = ConsentBus.Pending(sender = null, onResult = { count++ }, id = 99L)
        ConsentBus.complete(p, true)
        ConsentBus.complete(p, true)
        ConsentBus.complete(p, false)
        assertEquals(1, count)
    }

    @Test
    fun `cancelAll completes the active request with false`() {
        var resolved: Boolean? = null
        val p = ConsentBus.Pending(sender = null, onResult = { resolved = it }, id = 5L)
        ConsentBus.setActive(p)
        ConsentBus.cancelAll()
        assertEquals(false, resolved)
    }

    @Test
    fun `complete without active request is a no-op`() {
        ConsentBus.complete(true)
        ConsentBus.cancelAll()
        // No exception is the contract.
    }

    @Test
    fun `many concurrent null-sender requests all resolve false`() = runTest {
        ConsentBus.resetForTest()
        val flags = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        // Simulate the activity: drain the queue and resolve each request.
        val collector = launch(Dispatchers.Unconfined) {
            ConsentBus.requests.collect { ConsentBus.complete(it, false) }
        }
        val jobs = (1..50).map { _ ->
            async(Dispatchers.Unconfined) {
                ConsentBus.request(null) { flags.add(it) }
            }
        }.awaitAll()
        assertEquals(50, jobs.size)
        withTimeout(5000) {
            while (flags.size < 50) kotlinx.coroutines.yield()
        }
        assertEquals(50, flags.size)
        assertTrue(flags.all { !it })
        collector.cancel()
    }

    @Test
    fun `setActive then user answer resolves with true`() {
        var resolved: Boolean? = null
        val p = ConsentBus.Pending(sender = null, onResult = { resolved = it }, id = 7L)
        ConsentBus.setActive(p)
        ConsentBus.complete(true)
        assertEquals(true, resolved)
        assertFalse(ConsentBus.pending != null)
    }
}
