package com.murai.gallery

import android.provider.MediaStore
import com.murai.gallery.data.media.MediaScanner
import com.murai.gallery.data.media.ScanCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scanner projection + path derivation per API level (fix #2), and the
 * deleteStale guard that must never fire on a partial or permission-less pass.
 */
class ScanProjectionTest {

    @Test
    fun `projection excludes RELATIVE_PATH below API 29`() {
        for (sdk in intArrayOf(26, 27, 28)) {
            val projection = MediaScanner.projectionFor(sdk)
            assertFalse(
                "API $sdk must not project RELATIVE_PATH",
                projection.contains(MediaStore.Files.FileColumns.RELATIVE_PATH)
            )
            assertFalse(
                "API $sdk must not project IS_TRASHED",
                projection.contains(MediaStore.Files.FileColumns.IS_TRASHED)
            )
            assertFalse(
                "API $sdk must not project IS_FAVORITE",
                projection.contains(MediaStore.Files.FileColumns.IS_FAVORITE)
            )
        }
    }

    @Test
    fun `projection includes RELATIVE_PATH and buckets from API 29`() {
        for (sdk in intArrayOf(29, 30, 33, 34, 35)) {
            val projection = MediaScanner.projectionFor(sdk)
            assertTrue(projection.contains(MediaStore.Files.FileColumns.RELATIVE_PATH))
            assertTrue(projection.contains(MediaStore.Files.FileColumns.BUCKET_ID))
        }
    }

    @Test
    fun `projection excludes favorite and trashed below API 30`() {
        val projection = MediaScanner.projectionFor(29)
        assertFalse(projection.contains(MediaStore.Files.FileColumns.IS_FAVORITE))
        assertFalse(projection.contains(MediaStore.Files.FileColumns.IS_TRASHED))
        val projection30 = MediaScanner.projectionFor(30)
        assertTrue(projection30.contains(MediaStore.Files.FileColumns.IS_FAVORITE))
        assertTrue(projection30.contains(MediaStore.Files.FileColumns.IS_TRASHED))
    }

    @Test
    fun `projection always carries the mandatory columns`() {
        for (sdk in intArrayOf(26, 28, 29, 33, 35)) {
            val projection = MediaScanner.projectionFor(sdk)
            for (must in arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )) {
                assertTrue("API $sdk missing $must", projection.contains(must))
            }
        }
    }

    @Test
    fun `folder derivation prefers bucket then relative path then data`() {
        assertEquals("Camera", MediaScanner.deriveFolder("DCIM/Camera/", "x", "Camera"))
        assertEquals("Camera", MediaScanner.deriveFolder("DCIM/Camera/", "x", null))
        assertEquals("Camera", MediaScanner.deriveFolder("DCIM/Camera/", "", null))
        assertEquals("Camera", MediaScanner.deriveFolder("", "/storage/emulated/0/DCIM/Camera/IMG.jpg", null))
        assertEquals("Storage", MediaScanner.deriveFolder("", "", null))
        assertEquals("Root", MediaScanner.deriveFolder("Root/", "", null))
        assertEquals("Pictures", MediaScanner.deriveFolder("Pictures/", "/x", null))
    }

    @Test
    fun `display path comes from relative path or data`() {
        assertEquals(
            "DCIM/Camera/IMG.jpg",
            MediaScanner.deriveDisplayPath("DCIM/Camera/", "ignored", "IMG.jpg")
        )
        assertEquals(
            "/storage/emulated/0/DCIM/legacy.jpg",
            MediaScanner.deriveDisplayPath("", "/storage/emulated/0/DCIM/legacy.jpg", "legacy.jpg")
        )
        assertEquals("fallback.jpg", MediaScanner.deriveDisplayPath("", "", "fallback.jpg"))
    }

    @Test
    fun `deleteStale only allowed after a complete permitted pass`() {
        assertTrue(ScanCoordinator.allowDeleteStale(cursorCompleted = true, hasPermission = true, cursorNull = false))
        // No permission -> never delete (revoked or partial grants included).
        assertFalse(ScanCoordinator.allowDeleteStale(cursorCompleted = true, hasPermission = false, cursorNull = false))
        // Null cursor -> never delete.
        assertFalse(ScanCoordinator.allowDeleteStale(cursorCompleted = true, hasPermission = true, cursorNull = true))
        // Partial pass (cursor not fully consumed) -> never delete.
        assertFalse(ScanCoordinator.allowDeleteStale(cursorCompleted = false, hasPermission = true, cursorNull = false))
    }
}
