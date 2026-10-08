package com.murai.gallery.domain.exif

import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs

/** EXIF read/write helpers for dates and GPS location. */
object ExifEditor {

    data class ExifInfo(
        val dateTakenSec: Long,
        val latitude: Double,
        val longitude: Double,
        val cameraModel: String?,
        val orientation: Int
    )

    fun read(file: File): ExifInfo {
        val exif = runCatching { ExifInterface(file) }.getOrNull()
            ?: return ExifInfo(0, 0.0, 0.0, null, 0)
        val date = runCatching {
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.let { parse(it) } ?: 0L
        }.getOrDefault(0L)
        val latLong = exif.latLong
        return ExifInfo(
            dateTakenSec = date,
            latitude = latLong?.get(0) ?: 0.0,
            longitude = latLong?.get(1) ?: 0.0,
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
        )
    }

    fun writeDate(file: File, epochSec: Long): Boolean = runCatching {
        val exif = ExifInterface(file)
        val stamp = format(epochSec)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        exif.saveAttributes()
        true
    }.getOrDefault(false)

    fun writeLocation(file: File, latitude: Double, longitude: Double): Boolean = runCatching {
        val exif = ExifInterface(file)
        if (latitude == 0.0 && longitude == 0.0) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
        } else {
            exif.setLatLong(latitude, longitude)
        }
        exif.saveAttributes()
        true
    }.getOrDefault(false)

    fun parse(value: String): Long {
        val patterns = arrayOf(
            "yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss",
            "yyyy:MM:dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (p in patterns) {
            val parsed = runCatching {
                val fmt = java.text.SimpleDateFormat(p, java.util.Locale.US)
                fmt.timeZone = java.util.TimeZone.getDefault()
                fmt.parse(value)
            }.getOrNull()
            if (parsed != null) return parsed.time / 1000
        }
        return 0L
    }

    fun format(epochSec: Long): String {
        val fmt = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getDefault()
        return fmt.format(java.util.Date(epochSec * 1000))
    }

    fun dms(value: Double, isLat: Boolean): String {
        val dir = when {
            isLat -> if (value >= 0) "N" else "S"
            else -> if (value >= 0) "E" else "W"
        }
        val v = abs(value)
        val deg = v.toInt()
        val minFull = (v - deg) * 60
        val min = minFull.toInt()
        val sec = (minFull - min) * 60
        return String.format(java.util.Locale.US, "%d°%d'%.1f\"%s", deg, min, sec, dir)
    }
}
