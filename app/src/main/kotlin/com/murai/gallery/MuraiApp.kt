package com.murai.gallery

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.geo.GeoLabeler
import com.murai.gallery.util.ErrorLogger
import com.murai.gallery.work.LogRetentionWorker
import com.murai.gallery.work.ScanWorker
import com.murai.gallery.work.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MuraiApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
        ErrorLogger.install(this)
        createChannels()
        LogRetentionWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        GeoLabeler.appContext = this

        appScope.launch {
            // restore persisted in-app language before any UI is created
            val tag = container.settings.languageTag.first()
            if (!tag.isNullOrBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
            ScanWorker.enqueue(this@MuraiApp)
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

        fun get(context: Context): MuraiApp = context.applicationContext as MuraiApp
    }
}
