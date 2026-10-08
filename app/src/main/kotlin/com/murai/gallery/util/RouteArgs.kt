package com.murai.gallery.util

import java.util.Base64

/**
 * Safe encoding/decoding for dynamic navigation arguments.
 *
 * Navigation route fragments are matched literally by the NavHost, so raw
 * folder names or URIs that contain "/", "?", "#", "{", "}" or whitespace
 * break route parsing or crash the app. Every dynamic argument that this app
 * puts on a route is encoded here, and every screen decodes with the same
 * helper, so hostile names ("A/B", "a?b", "{x}", unicode, spaces) survive the
 * round trip byte-for-byte.
 */
object RouteArgs {

    private const val SEP = "|"

    /** Encodes a single route argument (album id, video uri, editor uri). */
    fun encode(raw: String): String = try {
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(Charsets.UTF_8))
    } catch (_: Throwable) {
        ""
    }

    /** Decodes a route argument; empty string when the value is malformed. */
    fun decode(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return try {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Builds the viewer payload "scope|anchorId" and encodes the whole thing
     * so the "|" separator can never collide with user content.
     */
    fun viewerPayload(scopeKey: String, anchorId: Long): String =
        encode("$anchorId@$scopeKey")

    /**
     * Decodes a viewer payload written by [viewerPayload]. Legacy payloads
     * from earlier builds ("scope|id") are still understood so deep links
     * created before the format change keep working.
     */
    fun parseViewerPayload(payload: String?): Pair<String, Long> {
        val decoded = decode(payload)
        if (decoded.isNotEmpty() && decoded.contains('@')) {
            val at = decoded.indexOf('@')
            val id = decoded.substring(0, at).toLongOrNull()
            if (id != null) return decoded.substring(at + 1) to id
        }
        // legacy fallback: scope|id
        val raw = payload ?: return "home" to 0L
        val parts = raw.split(SEP, limit = 2)
        if (parts.size == 2) {
            val id = parts[1].toLongOrNull()
            if (id != null) return parts[0] to id
        }
        val id = raw.toLongOrNull()
        return if (id != null) "home" to id else "home" to 0L
    }
}
