package com.murai.gallery.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached row for one item in the device media library.
 * All data originates from a fresh scan of the media provider.
 */
@Entity(
    tableName = "library_items",
    indices = [
        Index("dateTakenSec"), Index("bucketId"), Index("isVideo"),
        Index("trashed"), Index("favorite"), Index("folder"),
        Index("exactHash"), Index("phash")
    ]
)
data class LibraryItemEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val path: String,
    val name: String,
    val folder: String,
    val bucketId: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateModifiedSec: Long,
    val dateTakenSec: Long,
    val dateAddedSec: Long,
    val favorite: Boolean,
    val rating: Int,
    val trashed: Boolean,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    val exactHash: String?,
    val phash: Long?,
    val isPano: Boolean,
    val isMotion: Boolean,
    val scannedAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Long
)

@Entity(
    tableName = "tag_items",
    primaryKeys = ["tagId", "itemId"],
    indices = [Index("itemId")]
)
data class TagItemEntity(
    val tagId: Long,
    val itemId: Long
)

@Entity(tableName = "sort_presets")
data class SortPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOptionId: String,
    val ascending: Boolean,
    val groupId: String,
    val createdAt: Long
)

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encPath: String,
    val origName: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val addedAt: Long,
    val origUri: String?
)
