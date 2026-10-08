package com.murai.gallery

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.geo.GeoLabeler
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.work.LogRetentionWorker
import com.murai.gallery.work.ScanWorker
import com.murai.gallery.work.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MuraiApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        // The crash handler must be active before anything else can fail —
        // AppContainer touches the database and WorkManager on construction.
        ErrorLogger.install(this)
        super.onCreate()

        container = try {
            AppContainer.get(this)
        } catch (t: Throwable) {
            ErrorLogger.write(this, "appcontainer-init", t)
            throw t
        }

        runCatching { createChannels() }
            .onFailure { ErrorLogger.write(this, "notification-channels", it) }
        runCatching { LogRetentionWorker.schedule(this) }
            .onFailure { ErrorLogger.write(this, "schedule-log-retention", it) }
        runCatching { UpdateCheckWorker.schedule(this) }
            .onFailure { ErrorLogger.write(this, "schedule-update-check", it) }

        GeoLabeler.appContext = this

        appScope.launch {
            // Publish the persisted language for MainActivity, which applies
            // it with AppCompatDelegate.setApplicationLocales on the MAIN
            // thread before any UI exists (the call is main-thread-only).
            try {
                val tag = container.settings.languageTag.first()
                _savedLanguage.value = tag
            } catch (t: Throwable) {
                ErrorLogger.write(this@MuraiApp, "language-restore", t)
            }
            runCatching { ScanWorker.enqueue(this@MuraiApp) }
                .onFailure { ErrorLogger.write(this@MuraiApp, "schedule-scan", it) }
        }
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TOOLS,
                getString(R.string.channel_tools),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                getString(R.string.channel_general),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        const val CHANNEL_TOOLS = "murai_tools"
        const val CHANNEL_GENERAL = "murai_general"

        private val _savedLanguage = MutableStateFlow<String?>(null)

        /** Language tag persisted by settings, applied by MainActivity on main. */
        val savedLanguage: StateFlow<String?> = _savedLanguage

        fun get(context: Context): MuraiApp = context.applicationContext as MuraiApp
    }
}
