package com.murai.gallery.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (value >= 100) "${value.toInt()} ${units[unit]}"
        else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun date(sec: Long, pattern: String = "d MMM yyyy"): String {
        if (sec <= 0) return "—"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return fmt.format(Date(sec * 1000))
    }

    fun dateTime(sec: Long): String = date(sec, "d MMM yyyy, HH:mm")

    fun megapixels(w: Int, h: Int): String {
        val mp = w.toDouble() * h.toDouble() / 1_000_000.0
        return if (mp >= 1) String.format(Locale.US, "%.1f MP", mp)
        else "${w}×${h}"
    }

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f, %.4f", lat, lon)
}
