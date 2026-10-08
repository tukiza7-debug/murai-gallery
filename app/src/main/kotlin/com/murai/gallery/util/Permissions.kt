package com.murai.gallery.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Central permission gate. Every request made by the app goes through here so
 * the UI can show a short reason first (see RationaleDialog).
 */
enum class MuraiPermission(val permissions: Array<String>, val rationaleKey: String) {
    LIBRARY(
        buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                if (Build.VERSION.SDK_INT >= 34) {
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray(),
        "perm_rationale_library"
    ),
    NOTIFICATIONS(
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        "perm_rationale_notifications"
    );

    fun required(context: Context): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    /** True when at least one of the underlying permissions is granted. */
    fun granted(context: Context): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
