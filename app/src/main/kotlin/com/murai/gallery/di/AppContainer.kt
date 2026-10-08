package com.murai.gallery.di

import android.content.Context
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.media.MediaOperations
import com.murai.gallery.data.media.MediaRepository
import com.murai.gallery.data.media.MediaScanner
import com.murai.gallery.data.prefs.SettingsRepository
import com.murai.gallery.util.ErrorLogger

/**
 * Tiny hand-rolled container. The app deliberately avoids a reflection-heavy
 * DI framework: construction is explicit and cheap.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val db: MuraiDatabase = MuraiDatabase.get(context)
    val settings: SettingsRepository = SettingsRepository(context)
    val scanner: MediaScanner = MediaScanner(context, db)
    val operations: MediaOperations = MediaOperations(context, db)
    val repository: MediaRepository = MediaRepository(context, db, scanner, operations)

    init {
        // Escaped scan errors still reach the on-device log even though the
        // single-flight coordinator swallows them to protect waiters.
        scanner.coordinator.onError = { tag, t ->
            ErrorLogger.write(appContext, tag, t)
        }
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
