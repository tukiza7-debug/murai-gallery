package com.murai.gallery.domain.sort

import com.murai.gallery.data.db.dao.SortKeyRow
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.util.NaturalOrderComparator
import java.text.Collator
import java.util.Calendar
import java.util.Locale
import java.util.Random

/**
 * Pure sorting engine for the advanced sort system. Runs on a background
 * dispatcher (caller's responsibility) and never throws on missing metadata:
 * items lacking the sort key are appended at the end regardless of direction.
 */
object SortEngine {

    private val natural = NaturalOrderComparator()
    private val collator: Collator = Collator.getInstance(Locale.getDefault())

    fun sort(items: MutableList<SortKeyRow>, option: SortOption, direction: SortDirection, seed: Long) {
        if (option == SortOption.RANDOM) {
            val rnd = Random(seed)
            for (i in items.size - 1 downTo 1) {
                val j = rnd.nextInt(i + 1)
                val tmp = items[i]
                items[i] = items[j]
                items[j] = tmp
            }
            return
        }
        val asc = direction == SortDirection.ASCENDING
        val present = ArrayList<SortKeyRow>(items.size)
        val missing = ArrayList<SortKeyRow>()
        for (item in items) {
            if (hasKey(item, option)) present.add(item) else missing.add(item)
        }
        val base = comparator(option, asc)
        present.sortWith(base)
        items.clear()
        items.addAll(present)
        items.addAll(missing)
    }

    fun effectiveDate(item: SortKeyRow): Long =
        if (item.dateTakenSec > 0) item.dateTakenSec else item.dateModifiedSec

    fun effectiveDate(item: LibraryItemEntity): Long =
        if (item.dateTakenSec > 0) item.dateTakenSec else item.dateModifiedSec

    fun sortLabelRes(option: SortOption): Int = when (option) {
        SortOption.DATE_TAKEN -> com.murai.gallery.R.string.sort_date_taken
        SortOption.DATE_ADDED -> com.murai.gallery.R.string.sort_date_added
        SortOption.NAME_NATURAL -> com.murai.gallery.R.string.sort_name_natural
        SortOption.SIZE -> com.murai.gallery.R.string.sort_size
        SortOption.FILE_TYPE -> com.murai.gallery.R.string.sort_file_type
        SortOption.RESOLUTION -> com.murai.gallery.R.string.sort_resolution
        SortOption.DURATION -> com.murai.gallery.R.string.sort_duration
        SortOption.RATING_FAVORITES -> com.murai.gallery.R.string.sort_rating
        SortOption.LOCATION -> com.murai.gallery.R.string.sort_location
        SortOption.RANDOM -> com.murai.gallery.R.string.sort_random
    }

    private fun hasKey(item: SortKeyRow, option: SortOption): Boolean = when (option) {
        SortOption.DATE_TAKEN -> item.dateTakenSec > 0 || item.dateModifiedSec > 0
        SortOption.DATE_ADDED -> item.dateAddedSec > 0
        SortOption.NAME_NATURAL -> item.name.isNotBlank()
        SortOption.SIZE -> item.size > 0
        SortOption.FILE_TYPE -> item.mime.isNotBlank()
        SortOption.RESOLUTION -> item.width > 0 && item.height > 0
        SortOption.DURATION -> !item.isVideo || item.durationMs > 0
        SortOption.RATING_FAVORITES -> true
        SortOption.LOCATION -> item.locationLabel.isNotBlank() ||
            (item.latitude != 0.0 && item.longitude != 0.0)
        SortOption.RANDOM -> true
    }

    private fun comparator(option: SortOption, asc: Boolean): Comparator<SortKeyRow> {
        val base = when (option) {
            SortOption.DATE_TAKEN -> compareBy { effectiveDate(it) }
            SortOption.DATE_ADDED -> compareBy { it.dateAddedSec }
            SortOption.NAME_NATURAL -> Comparator { a, b -> natural.compare(a.name, b.name) }
            SortOption.SIZE -> compareBy { it.size }
            SortOption.FILE_TYPE -> Comparator { a, b ->
                val c = a.mime.compareTo(b.mime, ignoreCase = true)
                if (c != 0) c else natural.compare(a.name, b.name)
            }
            SortOption.RESOLUTION -> compareBy { it.width.toLong() * it.height }
            SortOption.DURATION -> compareBy { it.durationMs }
            SortOption.RATING_FAVORITES -> compareBy<SortKeyRow> { it.rating }
                .thenBy { if (it.favorite) 1 else 0 }
            SortOption.LOCATION -> Comparator { a, b ->
                val la = a.locationLabel.ifBlank { coordsText(a) }
                val lb = b.locationLabel.ifBlank { coordsText(b) }
                val c = collator.compare(la, lb)
                if (c != 0) c else natural.compare(a.name, b.name)
            }
            SortOption.RANDOM -> compareBy { it.id }
        }
        return if (asc) base else base.reversed()
    }

    private fun coordsText(item: SortKeyRow): String =
        String.format(Locale.US, "%.2f,%.2f", item.latitude, item.longitude)

    /** Bucket label used by grouped timelines. */
    fun groupLabel(item: SortKeyRow, modeRaw: String): String = when (modeRaw) {
        "day" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault())
            .format(Calendar.getInstance().apply { timeInMillis = effectiveDate(item) * 1000 }.time)
        "month" -> java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(Calendar.getInstance().apply { timeInMillis = effectiveDate(item) * 1000 }.time)
        "year" -> String.format(
            Locale.US, "%04d",
            Calendar.getInstance().apply { timeInMillis = effectiveDate(item) * 1000 }.get(Calendar.YEAR)
        )
        "album" -> item.folder
        "type" -> if (item.isVideo) "Video" else "Photo"
        "location" -> item.locationLabel.ifBlank { "—" }
        else -> ""
    }

    fun groupLabel(item: LibraryItemEntity, modeRaw: String): String = groupLabel(item.toKey(), modeRaw)

    fun LibraryItemEntity.toKey(): SortKeyRow = SortKeyRow(
        id = id, name = name, folder = folder, bucketId = bucketId, mime = mime, isVideo = isVideo,
        size = size, width = width, height = height, durationMs = durationMs,
        dateModifiedSec = dateModifiedSec, dateTakenSec = dateTakenSec,
        dateAddedSec = dateAddedSec, favorite = favorite, rating = rating,
        latitude = latitude, longitude = longitude, locationLabel = locationLabel
    )
}
