package com.murai.gallery.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.data.db.entity.SortPresetEntity
import com.murai.gallery.data.db.entity.TagEntity
import com.murai.gallery.data.db.entity.TagItemEntity
import com.murai.gallery.data.db.entity.VaultEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LibraryItemEntity>)

    @Query("DELETE FROM library_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM library_items WHERE scannedAt < :threshold")
    suspend fun deleteStale(threshold: Long): Int

    @Query("SELECT COUNT(*) FROM library_items")
    suspend fun count(): Int

    @Query("SELECT id FROM library_items")
    suspend fun allIds(): List<Long>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun byId(id: Long): LibraryItemEntity?

    @Query("SELECT * FROM library_items WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE trashed = 0 ORDER BY dateTakenSec DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE trashed = 0 ORDER BY dateTakenSec DESC LIMIT 1")
    suspend fun latest(): LibraryItemEntity?

    @Query("UPDATE library_items SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE library_items SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int)

    @Query("UPDATE library_items SET trashed = :trashed WHERE id IN (:ids)")
    suspend fun setTrashed(ids: List<Long>, trashed: Boolean)

    @Query("UPDATE library_items SET exactHash = :hash WHERE id = :id")
    suspend fun setExactHash(id: Long, hash: String)

    @Query("UPDATE library_items SET phash = :phash WHERE id = :id")
    suspend fun setPHash(id: Long, phash: Long)

    @Query("UPDATE library_items SET locationLabel = :label WHERE id = :id")
    suspend fun setLocationLabel(id: Long, label: String)

    @Query("UPDATE library_items SET latitude = :lat, longitude = :lon WHERE id = :id")
    suspend fun setCoordinates(id: Long, lat: Double, lon: Double)

    @Query(
        """SELECT * FROM library_items
           WHERE latitude = 0.0 AND longitude = 0.0 AND isVideo = 0 AND trashed = 0
           LIMIT :limit"""
    )
    suspend fun withoutCoordinates(limit: Int): List<LibraryItemEntity>

    /** Lightweight id-only ordering for the viewer; never loads full rows. */
    @Query("SELECT id FROM library_items WHERE trashed = 0 ORDER BY dateTakenSec DESC")
    suspend fun homeIds(): List<Long>

    @Query("SELECT id FROM library_items WHERE trashed = 0 AND favorite = 1 ORDER BY dateTakenSec DESC")
    suspend fun favoriteIds(): List<Long>

    @Query("SELECT id FROM library_items WHERE trashed = 0 AND bucketId = :bucketId ORDER BY dateTakenSec DESC")
    suspend fun albumIds(bucketId: String): List<Long>

    @Query("UPDATE library_items SET name = :name, path = :path WHERE id = :id")
    suspend fun rename(id: Long, name: String, path: String)

    @Query("SELECT * FROM library_items WHERE trashed = 1")
    fun observeBin(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE favorite = 1 AND trashed = 0")
    fun observeFavorites(): Flow<List<LibraryItemEntity>>

    @Query(
        """SELECT * FROM library_items
           WHERE trashed = 0
             AND (:onlyImages = 0 OR isVideo = 0)
             AND (:onlyVideos = 0 OR isVideo = 1)
             AND (:minSize < 0 OR size >= :minSize)
             AND (:maxSize < 0 OR size <= :maxSize)
             AND (:minDate < 0 OR COALESCE(NULLIF(dateTakenSec,0), dateModifiedSec) >= :minDate)
             AND (:maxDate < 0 OR COALESCE(NULLIF(dateTakenSec,0), dateModifiedSec) <= :maxDate)
             AND (:bucketId IS NULL OR bucketId = :bucketId)
             AND (:query IS NULL OR name LIKE '%' || :query || '%' OR folder LIKE '%' || :query || '%'
                  OR locationLabel LIKE '%' || :query || '%')
             AND (:location IS NULL OR locationLabel LIKE '%' || :location || '%')
           ORDER BY dateTakenSec DESC"""
    )
    fun pagingFiltered(
        onlyImages: Boolean, onlyVideos: Boolean,
        minSize: Long, maxSize: Long,
        minDate: Long, maxDate: Long,
        bucketId: String?, query: String?, location: String?
    ): PagingSource<Int, LibraryItemEntity>

    @Query(
        """SELECT * FROM library_items
           WHERE trashed = 0 AND isVideo = :isVideo AND bucketId = :bucketId
           ORDER BY dateTakenSec DESC"""
    )
    fun pagingBucket(bucketId: String, isVideo: Boolean): PagingSource<Int, LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE trashed = 0 AND isVideo = :isVideo ORDER BY dateTakenSec DESC")
    suspend fun listByType(isVideo: Boolean): List<LibraryItemEntity>

    @Query(
        """SELECT bucketId AS bucketId, MAX(folder) AS name, COUNT(*) AS itemCount,
                  MIN(uri) AS coverUri, MAX(isVideo) AS isVideoAlbum
           FROM library_items WHERE trashed = 0 GROUP BY bucketId ORDER BY itemCount DESC"""
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT DISTINCT folder FROM library_items WHERE trashed = 0 ORDER BY folder")
    suspend fun allFolders(): List<String>

    @Query("SELECT COUNT(*) FROM library_items WHERE trashed = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size),0) FROM library_items WHERE trashed = 0")
    suspend fun totalBytes(): Long

    @Query("SELECT * FROM library_items WHERE size >= :minSize AND trashed = 0 ORDER BY size DESC LIMIT :limit")
    suspend fun biggestFiles(minSize: Long, limit: Int): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE folder LIKE '%Screenshot%' AND trashed = 0 ORDER BY dateTakenSec DESC")
    suspend fun screenshots(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE folder = :folder AND trashed = 0 ORDER BY size DESC")
    suspend fun byFolder(folder: String): List<LibraryItemEntity>

    @Query(
        """SELECT folder, COUNT(*) AS c, SUM(size) AS bytes FROM library_items
           WHERE trashed = 0 GROUP BY folder ORDER BY bytes DESC"""
    )
    suspend fun folderUsage(): List<FolderUsageRow>

    @Query("SELECT * FROM library_items WHERE isVideo = 1 AND trashed = 0 ORDER BY durationMs DESC LIMIT 1")
    suspend fun longestVideo(): LibraryItemEntity?

    @Query("SELECT MAX(size) FROM library_items WHERE trashed = 0")
    suspend fun biggestSize(): Long

    @Query("SELECT MIN(NULLIF(dateTakenSec,0)) FROM library_items WHERE trashed = 0")
    suspend fun oldestDate(): Long?

    @Query("SELECT MAX(NULLIF(dateTakenSec,0)) FROM library_items WHERE trashed = 0")
    suspend fun newestDate(): Long?

    @Query("SELECT COUNT(*) FROM library_items WHERE trashed = 0 AND isVideo = :isVideo")
    suspend fun countByType(isVideo: Boolean): Int

    @Query(
        """SELECT folder, COUNT(*) AS c FROM library_items
           WHERE trashed = 0 GROUP BY folder ORDER BY c DESC LIMIT 12"""
    )
    suspend fun folderCounts(): List<FolderCountRow>

    @Query(
        """SELECT strftime('%Y-%m', COALESCE(NULLIF(dateTakenSec,0), dateModifiedSec), 'unixepoch') AS m, COUNT(*) AS c
           FROM library_items WHERE trashed = 0 GROUP BY m ORDER BY m DESC LIMIT 12"""
    )
    suspend fun monthCounts(): List<MonthCountRow>

    @Query("SELECT mime, COUNT(*) AS c FROM library_items WHERE trashed = 0 GROUP BY mime ORDER BY c DESC LIMIT 8")
    suspend fun mimeCounts(): List<MimeCountRow>

    @Query("SELECT * FROM library_items WHERE latitude != 0 OR longitude != 0")
    suspend fun geotagged(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE id IN (SELECT itemId FROM tag_items WHERE tagId = :tagId)")
    suspend fun byTag(tagId: Long): List<LibraryItemEntity>

    @Query(
        """SELECT id, name, folder, bucketId, mime, isVideo, size, width, height, durationMs,
                  dateModifiedSec, dateTakenSec, dateAddedSec, favorite, rating,
                  latitude, longitude, locationLabel
           FROM library_items WHERE trashed = 0"""
    )
    suspend fun sortKeyRows(): List<SortKeyRow>

    @Query(
        """SELECT * FROM library_items
           WHERE trashed = 0 AND bucketId IN (:bucketIds)
           ORDER BY RANDOM() LIMIT 1"""
    )
    suspend fun randomInBuckets(bucketIds: List<String>): LibraryItemEntity?

    @Query("SELECT * FROM library_items WHERE exactHash IN (SELECT exactHash FROM library_items WHERE exactHash IS NOT NULL GROUP BY exactHash HAVING COUNT(*) > 1)")
    suspend fun exactDuplicateCandidates(): List<LibraryItemEntity>

    @Query("SELECT * FROM library_items WHERE phash IS NOT NULL")
    suspend fun phashed(): List<LibraryItemEntity>
}

data class AlbumRow(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val coverUri: String,
    val isVideoAlbum: Boolean
)

data class FolderUsageRow(val folder: String, val c: Int, val bytes: Long)
data class FolderCountRow(val folder: String, val c: Int)
data class MonthCountRow(val m: String, val c: Int)
data class MimeCountRow(val mime: String, val c: Int)

/**
 * Minimal projection carrying every sort/group key. Keeps the in-memory sort
 * payload around 100 bytes per row — a 50k library sorts from ~6 MB instead
 * of ~30 MB of full entities.
 */
data class SortKeyRow(
    val id: Long,
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
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String
)

@Dao
interface TagsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoin(join: TagItemEntity)

    @Query("DELETE FROM tag_items WHERE tagId = :tagId AND itemId = :itemId")
    suspend fun removeJoin(tagId: Long, itemId: Long)

    @Transaction
    @Query("SELECT tagId, COUNT(*) AS count FROM tag_items GROUP BY tagId")
    suspend fun tagCounts(): List<TagCountRow>

    @Query("SELECT tagId FROM tag_items WHERE itemId = :itemId")
    suspend fun tagsFor(itemId: Long): List<Long>
}

data class TagCountRow(val tagId: Long, val count: Int)

@Dao
interface SortPresetsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: SortPresetEntity): Long

    @Query("DELETE FROM sort_presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sort_presets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SortPresetEntity>>

    @Query("SELECT * FROM sort_presets WHERE id = :id")
    suspend fun byId(id: Long): SortPresetEntity?
}

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VaultEntryEntity): Long

    @Query("SELECT * FROM vault_entries ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun byId(id: Long): VaultEntryEntity?

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
