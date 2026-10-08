package com.murai.gallery.util

import android.content.IntentSender
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges MediaStore consent requests (IntentSender) from background code to
 * the activity, which launches them via a registered launcher and reports the
 * outcome back here.
 */
object ConsentBus {

    data class Pending(val sender: IntentSender, val onResult: (Boolean) -> Unit)

    val pending = MutableStateFlow<Pending?>(null)

    fun request(sender: IntentSender, onResult: (Boolean) -> Unit) {
        pending.value = Pending(sender, onResult)
    }

    fun complete(success: Boolean) {
        pending.value?.onResult?.invoke(success)
        pending.value = null
    }
}
