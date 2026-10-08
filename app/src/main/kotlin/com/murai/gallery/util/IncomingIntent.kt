package com.murai.gallery.util

import android.content.Intent
import android.net.Uri

/**
 * Normalizes every externally triggered entry point (ACTION_VIEW, SEND,
 * SEND_MULTIPLE, SET_WALLPAPER) into a small description the UI can act on.
 *
 * v2.0.1 rule: the exact URI the caller handed us is what gets opened — the
 * old code fell back to "open the first library item", which showed the user
 * the wrong picture and required storage permission for a grant we already
 * hold. All handlers here work purely on the provided URI(s), so external
 * open works even when the app has no media permission at all (URI grants).
 */
sealed interface IncomingIntent {

    /** ACTION_VIEW with a single content/file URI. */
    data class View(val uri: Uri) : IncomingIntent

    /** ACTION_SEND with one stream (EXTRA_STREAM) or text (EXTRA_TEXT). */
    data class Send(val uri: Uri?, val text: String?) : IncomingIntent

    /** ACTION_SEND_MULTIPLE with EXTRA_STREAM list. */
    data class SendMultiple(val uris: List<Uri>) : IncomingIntent

    /** ACTION_SET_WALLPAPER — jump straight to the wallpaper tool. */
    data object SetWallpaper : IncomingIntent

    companion object {
        fun from(intent: Intent?): IncomingIntent? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.let { View(it) }
                Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (stream != null) Send(stream, null)
                    else if (!text.isNullOrBlank()) Send(null, text)
                    else intent.data?.let { Send(it, null) }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    @Suppress("DEPRECATION")
                    val streams = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (!streams.isNullOrEmpty()) SendMultiple(streams.filterNotNull())
                    else null
                }
                Intent.ACTION_SET_WALLPAPER -> SetWallpaper
                else -> null
            }
        }
    }
}
