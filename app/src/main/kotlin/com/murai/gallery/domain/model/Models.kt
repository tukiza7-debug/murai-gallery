package com.murai.gallery.domain.model

/** The ten advanced sort orders offered by Murai Gallery. */
enum class SortOption(val id: String) {
    DATE_TAKEN("date_taken"),
    DATE_ADDED("date_added"),
    NAME_NATURAL("name_natural"),
    SIZE("size"),
    FILE_TYPE("file_type"),
    RESOLUTION("resolution"),
    DURATION("duration"),
    RATING_FAVORITES("rating_favorites"),
    LOCATION("location"),
    RANDOM("random");

    companion object {
        fun fromId(id: String?): SortOption? = entries.firstOrNull { it.id == id }
    }
}

enum class SortDirection { ASCENDING, DESCENDING }

/** Timeline grouping applied on top of sorting. */
enum class GroupMode(val id: String) {
    NONE("none"),
    DAY("day"),
    MONTH("month"),
    YEAR("year"),
    ALBUM("album"),
    TYPE("type"),
    LOCATION("location");

    companion object {
        fun fromId(id: String?): GroupMode? = entries.firstOrNull { it.id == id }
    }
}

/** A fully specified sort request, optionally persisted as a preset. */
data class SortSpec(
    val option: SortOption = SortOption.DATE_TAKEN,
    val direction: SortDirection = SortDirection.DESCENDING,
    val group: GroupMode = GroupMode.NONE,
    val reshuffleSeed: Long = System.nanoTime()
)

data class SortPreset(
    val id: Long,
    val name: String,
    val sortOption: SortOption,
    val ascending: Boolean,
    val group: GroupMode
)

data class Album(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val coverUri: String?,
    val isVideoAlbum: Boolean
)

data class Tag(
    val id: Long,
    val name: String,
    val colorArgb: Long,
    val itemCount: Int = 0
)

data class MediaFilter(
    val query: String? = null,
    val onlyImages: Boolean = false,
    val onlyVideos: Boolean = false,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val minDateSec: Long? = null,
    val maxDateSec: Long? = null,
    val locationText: String? = null,
    val favoriteOnly: Boolean = false,
    val trashedOnly: Boolean = false,
    val bucketId: String? = null,
    val tagId: Long? = null
)

data class DuplicateCluster(
    val key: String,
    val perceptual: Boolean,
    val itemIds: List<Long>
)

data class StatsSummary(
    val totalItems: Int,
    val totalImages: Int,
    val totalVideos: Int,
    val totalBytes: Long,
    val folders: Int,
    val longestVideoMs: Long,
    val biggestFileBytes: Long,
    val oldestDateSec: Long,
    val newestDateSec: Long,
    val byFolder: List<Pair<String, Int>>,
    val byMonth: List<Pair<String, Int>>,
    val byType: List<Pair<String, Int>>
)

/** Identifiers for every entry of the New Tools hub. */
enum class ToolId {
    DUPLICATES, EDITOR, TRIMMER, COMPRESSOR, OCR, COLLAGE, GIF_MAKER,
    BATCH_RENAME, STORAGE_CLEANER, QR_SCANNER, WALLPAPER, TELEGRAM,
    SORT_PRESETS, BACKUP, VAULT, SETTINGS
}
