package com.murai.gallery.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.murai.gallery.di.AppContainer
import com.murai.gallery.domain.geo.GeoLabeler
import com.murai.gallery.domain.update.UpdateChecker
import com.murai.gallery.util.ErrorLogger
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Media library scan: incremental, paged, runs in the background. */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        container.scanner.scan { }
        GeoLabeler.enrich(container.repository.geotagged()) { id, label ->
            container.db.libraryDao().setLocationLabel(id, label)
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "murai-scan",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ScanWorker>().build()
            )
        }
    }
}

/** Picks a fresh wallpaper from the chosen albums every configured interval. */
class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        val enabled = container.settings.wallpaperEnabled.first()
        if (!enabled) return Result.success()
        val albums = container.settings.wallpaperAlbums.first()
        if (albums.isEmpty()) return Result.success()
        val pick = container.repository.pagedForAlbumRandom(albums) ?: return Result.success()
        return try {
            WallpaperApplier.apply(applicationContext, pick.uri, container.settings.wallpaperBoth.first())
            Result.success()
        } catch (t: Throwable) {
            ErrorLogger.write(applicationContext, "wallpaper", t)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context, hours: Int) {
            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
                hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "murai-wallpaper",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("murai-wallpaper")
        }
    }
}

/** Purges error-log files older than 30 days. */
class LogRetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val removed = ErrorLogger.purgeOld(applicationContext)
        ErrorLogger.write(applicationContext, "log-retention", null, "removed=$removed")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogRetentionWorker>(3, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "murai-log-retention",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/** Daily silent update check so the badge is fresh when the user opens settings. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = UpdateChecker.check(com.murai.gallery.BuildConfig.VERSION_NAME)
        if (result is UpdateChecker.CheckResult.Offline) return Result.success()
        val release = (result as? UpdateChecker.CheckResult.Success)?.release ?: return Result.success()
        ErrorLogger.write(applicationContext, "update-check", null, "latest=${release.tagName}")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "murai-update-check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_BOOT_COMPLETED) {
            ScanWorker.enqueue(context)
        }
    }
}

private object WallpaperApplier {
    fun apply(context: Context, uri: String, both: Boolean) {
        val stream = context.contentResolver.openInputStream(android.net.Uri.parse(uri)) ?: return
        val bitmap = android.graphics.BitmapFactory.decodeStream(stream) ?: return
        val manager = android.app.WallpaperManager.getInstance(context)
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            if (both) {
                manager.setBitmap(bitmap, null, true, android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
            } else {
                manager.setBitmap(bitmap, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
            }
        } else {
            manager.setBitmap(bitmap)
        }
    }
}
