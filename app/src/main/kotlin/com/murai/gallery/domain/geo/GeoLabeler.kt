package com.murai.gallery.domain.geo

import android.content.Context
import android.location.Geocoder
import com.murai.gallery.data.db.entity.LibraryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Resolves human-readable place labels for geotagged items using the platform
 * Geocoder. When the backend service is unavailable (offline devices), the
 * label stays empty and coordinates are shown instead — never an error.
 */
object GeoLabeler {

    suspend fun label(context: Context, lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            if (lat == 0.0 && lon == 0.0) return@withContext ""
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching ""
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val a = addresses?.firstOrNull() ?: return@runCatching ""
                buildList {
                    a.countryName?.let { add(it) }
                    a.locality?.let { add(it) }
                    a.subLocality?.let { add(it) }
                    a.thoroughfare?.let { add(it) }
                }.joinToString(", ")
            }.getOrDefault("")
        }

    suspend fun enrich(items: List<LibraryItemEntity>, onUpdate: suspend (Long, String) -> Unit) {
        val context = appContext ?: return
        for (item in items) {
            if (item.locationLabel.isNotBlank()) continue
            val label = label(context, item.latitude, item.longitude)
            if (label.isNotBlank()) onUpdate(item.id, label)
        }
    }

    @Volatile var appContext: Context? = null
}
