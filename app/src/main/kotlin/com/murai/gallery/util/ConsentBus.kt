package com.murai.gallery.util

import android.content.IntentSender
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges MediaStore consent requests (IntentSender) from background code to
 * the activity, which launches them via a registered launcher and reports the
 * outcome back here.
 *
 * v2.0.1 hardening:
 *  - Requests travel over an UNLIMITED channel: multiple requests queue
 *    instead of overwriting each other, and a request raised while the
 *    activity is recreating survives until the new collector drains it (the
 *    old StateFlow both dropped concurrent requests and replayed stale ones).
 *  - Each request is completed exactly once: on the user's answer, when the
 *    launch fails, or when the activity goes away ([cancelAll]).
 */
object ConsentBus {

    data class Pending(
        val sender: IntentSender?,
        val onResult: (Boolean) -> Unit,
        val id: Long
    )

    private val channel = Channel<Pending>(Channel.UNLIMITED)
    private val seq = AtomicLong(1)
    private val completed: MutableSet<Long> = Collections.synchronizedSet(HashSet())
    private val activeRef = java.util.concurrent.atomic.AtomicReference<Pending?>(null)

    /** Resets all queue state. Used by unit tests for isolation only. */
    fun resetForTest() {
        activeRef.set(null)
        completed.clear()
        seq.set(1)
        // Drain anything left in the channel so tests never see stale items.
        while (true) {
            channel.tryReceive().getOrNull() ?: break
        }
    }

    /** One-shot stream of consent requests (each consumed by one collector). */
    val requests: Flow<Pending> = channel.receiveAsFlow()

    /** The request currently being shown, if any. */
    val pending: Pending? get() = activeRef.get()

    /**
     * Enqueues a consent request. The sender is delivered verbatim; a null
     * sender (defensive callers only) is completed with false by the activity
     * side when it reaches the front of the queue.
     */
    fun request(sender: IntentSender?, onResult: (Boolean) -> Unit) {
        val p = Pending(sender, onResult, seq.getAndIncrement())
        val sent = channel.trySend(p)
        if (sent.isFailure) {
            // Queue unavailable: fail the callback instead of dropping it.
            complete(p, false)
        }
    }

    /** Called by the activity when the system dialog returns. */
    fun complete(success: Boolean) {
        val p = activeRef.getAndSet(null) ?: return
        complete(p, success)
    }

    /** Marks a specific request as resolved exactly once. */
    fun complete(p: Pending, success: Boolean) {
        if (!completed.add(p.id)) return
        if (activeRef.get()?.id == p.id) activeRef.set(null)
        runCatching { p.onResult(success) }
    }

    /**
     * Marks [p] as the request currently being shown. Called right before the
     * launcher starts the IntentSender; launch failures complete with false.
     */
    fun setActive(p: Pending) {
        activeRef.set(p)
    }

    /** Activity is going away: resolve everything outstanding with false. */
    fun cancelAll() {
        complete(false)
    }
}
