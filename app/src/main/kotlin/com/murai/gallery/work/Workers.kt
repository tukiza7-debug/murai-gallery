package com.murai.gallery.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

/** Media library scan: incremental, paged, single-flight, runs in the background. */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.get(applicationContext)
        try {
            container.scanner.scan { }
            // Coordinates arrive in a bounded background pass after the quick
            // scan; without them Map, location sort/group and GeoLabeler are
            // empty on API 29+.
            container.scanner.backfillGps()
            GeoLabeler.enrich(container.repository.geotagged()) { id, label ->
                container.db.libraryDao().setLocationLabel(id, label)
            }
            return Result.success()
        } catch (t: Throwable) {
            ErrorLogger.write(applicationContext, "scan-worker", t)
            return Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "murai-scan",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ScanWorker>().build()
                )
            }.onFailure {
                ErrorLogger.write(context, "scan-enqueue", it)
            }
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
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "murai-wallpaper",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }.onFailure { ErrorLogger.write(context, "wallpaper-schedule", it) }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork("murai-wallpaper") }
        }
    }
}

/**
 * Decodes the wallpaper source down to screen size via inSampleSize and
 * always closes/recycles — the old code decoded the full-resolution file and
 * OOM-killed the app on 50 MP shots.
 */
object WallpaperApplier {

    fun apply(context: Context, uri: String, both: Boolean) {
        val res = context.resources
        val screenMax = maxOf(res.displayMetrics.widthPixels, res.displayMetrics.heightPixels)
        val parsed = Uri.parse(uri)
        // Two passes over the source, each with its own stream (provider
        // streams are not guaranteed to support mark/reset).
        val sample = context.contentResolver.openInputStream(parsed)?.use { stream ->
            computeSampleSize(stream, screenMax)
        } ?: return
        val bitmap = context.contentResolver.openInputStream(parsed)?.use { stream ->
            decodeSampled(stream, sample)
        } ?: return
        try {
            val manager = android.app.WallpaperManager.getInstance(context)
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                if (both) {
                    manager.setBitmap(
                        bitmap, null, true,
                        android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
                    )
                } else {
                    manager.setBitmap(bitmap, null, true, android.app.WallpaperManager.FLAG_SYSTEM)
                }
            } else {
                manager.setBitmap(bitmap)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** First pass: read the image bounds only. */
    private fun computeSampleSize(input: java.io.InputStream, maxDim: Int): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    /** Second pass: decode with the computed inSampleSize. */
    private fun decodeSampled(input: java.io.InputStream, sample: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeStream(input, null, opts)
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
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "murai-log-retention",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { ErrorLogger.write(context, "log-retention-schedule", it) }
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
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "murai-update-check",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { ErrorLogger.write(context, "update-schedule", it) }
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
