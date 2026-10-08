package com.murai.gallery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.murai.gallery.MainActivity
import com.murai.gallery.MuraiApp
import com.murai.gallery.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Latest photo" home widget: shows the most recent picture and opens the
 * gallery when tapped. Refreshed on update broadcasts and after each scan.
 */
class LatestPhotoWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, placeholder(context))
        }
        refresh(context)
    }

    companion object {
        fun placeholder(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.latest_photo_widget)

        fun refresh(context: Context) {
            val app = MuraiApp.get(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LatestPhotoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            (context.applicationContext as MuraiApp).appScope.launch {
                val latest = app.container.repository.latest()
                val views = placeholder(context)
                if (latest != null) {
                    val bmp = decodeScaled(context, latest.uri)
                    if (bmp != null) {
                        views.setImageViewBitmap(R.id.widget_image, bmp)
                        views.setTextViewText(R.id.widget_caption, "")
                    }
                }
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pending = PendingIntent.getActivity(
                    context, 11, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                manager.updateAppWidget(ids, views)
            }
        }

        private fun decodeScaled(context: Context, uri: String): Bitmap? = runCatching {
            val resolver = context.contentResolver
            val source = android.net.Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 720) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }
}
