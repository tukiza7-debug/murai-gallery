package com.murai.gallery

import com.murai.gallery.util.RouteArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route argument encoding/decoding against hostile values (fix #8): folder
 * names and URIs containing separators, glob characters or unicode must
 * survive the navigation round trip byte-for-byte.
 */
class RouteArgsTest {

    @Test
    fun `round trip survives hostile folder names`() {
        val hostile = listOf(
            "A/B",
            "a?b",
            "{x}",
            "DCIM/Camera #1",
            "100%_sure",
            "照片 2024",
            "صور",
            "space name  ",
            "semi;colon",
            "pipe|line",
            "amp&er",
            "eq=uals",
            "colon:thing",
            "..",
            ".hidden",
            "-=_+~!@#\$%^&*()[]{}<>,."
        )
        for (raw in hostile) {
            assertEquals("failed for <$raw>", raw, RouteArgs.decode(RouteArgs.encode(raw)))
        }
    }

    @Test
    fun `encoded arguments contain no route-breaking characters`() {
        val raw = "DCIM/{x}?a=b#c/d e"
        val encoded = RouteArgs.encode(raw)
        assertTrue(encoded.isNotEmpty())
        for (bad in listOf('/', '?', '#', ' ', '%', '{', '}')) {
            assertTrue("encoded still contains '$bad'", !encoded.contains(bad))
        }
    }

    @Test
    fun `viewer payload round trips scope and id`() {
        val (scope, id) = RouteArgs.parseViewerPayload(RouteArgs.viewerPayload("album:A/B?c", 4242))
        assertEquals("album:A/B?c", scope)
        assertEquals(4242L, id)
    }

    @Test
    fun `viewer payload keeps unicode scopes`() {
        val (scope, id) = RouteArgs.parseViewerPayload(RouteArgs.viewerPayload("uri:content://media/照片/1", 0L))
        assertEquals("uri:content://media/照片/1", scope)
        assertEquals(0L, id)
    }

    @Test
    fun `legacy scope|id payloads still parse`() {
        val (scope, id) = RouteArgs.parseViewerPayload("home|77")
        assertEquals("home", scope)
        assertEquals(77L, id)
    }

    @Test
    fun `garbage payloads fall back to home`() {
        assertEquals("home" to 0L, RouteArgs.parseViewerPayload(null))
        assertEquals("home" to 0L, RouteArgs.parseViewerPayload(""))
        assertEquals("home" to 0L, RouteArgs.parseViewerPayload("%%%not-base64%%%"))
        assertEquals("home" to 0L, RouteArgs.parseViewerPayload(RouteArgs.encode("no-id-here")))
    }

    @Test
    fun `decode of malformed input is empty never throws`() {
        assertEquals("", RouteArgs.decode(null))
        assertEquals("", RouteArgs.decode(""))
        assertEquals("", RouteArgs.decode("!!!"))
    }
}
