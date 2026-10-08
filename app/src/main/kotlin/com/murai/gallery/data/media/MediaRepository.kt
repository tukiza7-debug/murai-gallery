package com.murai.gallery.data.media

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.murai.gallery.data.db.MuraiDatabase
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.domain.model.MediaFilter
import kotlinx.coroutines.flow.Flow

/** Read-side facade over the local library cache. */
class MediaRepository(
    private val context: Context,
    private val db: MuraiDatabase,
    val scanner: MediaScanner,
    val operations: MediaOperations
) {

    private val dao get() = db.libraryDao()

    fun paged(filter: MediaFilter): Flow<PagingData<LibraryItemEntity>> {
        if (filter.bucketId != null) {
            return Pager(PAGING_CONFIG) {
                dao.pagingBucket(filter.bucketId, filter.onlyVideos)
            }.flow
        }
        return Pager(PAGING_CONFIG) {
            dao.pagingFiltered(
                onlyImages = filter.onlyImages,
                onlyVideos = filter.onlyVideos,
                minSize = filter.minSizeBytes ?: -1L,
                maxSize = filter.maxSizeBytes ?: -1L,
                minDate = filter.minDateSec ?: -1L,
                maxDate = filter.maxDateSec ?: -1L,
                bucketId = filter.bucketId,
                query = filter.query?.takeIf { it.isNotBlank() },
                location = filter.locationText?.takeIf { it.isNotBlank() }
            )
        }.flow
    }

    suspend fun byId(id: Long): LibraryItemEntity? = dao.byId(id)
    suspend fun byIds(ids: List<Long>): List<LibraryItemEntity> = dao.byIds(ids)
    suspend fun recent(limit: Int = 60): List<LibraryItemEntity> = dao.recent(limit)
    suspend fun latest(): LibraryItemEntity? = dao.latest()
    suspend fun geotagged(): List<LibraryItemEntity> = dao.geotagged()
    suspend fun folderUsage() = dao.folderUsage()
    suspend fun biggestFiles(minSize: Long, limit: Int) = dao.biggestFiles(minSize, limit)
    suspend fun screenshots() = dao.screenshots()
    suspend fun byFolder(folder: String) = dao.byFolder(folder)
    suspend fun totalBytes() = dao.totalBytes()
    suspend fun longestVideo() = dao.longestVideo()
    suspend fun biggestSize() = dao.biggestSize()
    suspend fun oldestDate() = dao.oldestDate()
    suspend fun newestDate() = dao.newestDate()
    suspend fun countImages() = dao.countByType(false)
    suspend fun countVideos() = dao.countByType(true)
    suspend fun folderCounts() = dao.folderCounts()
    suspend fun monthCounts() = dao.monthCounts()
    suspend fun mimeCounts() = dao.mimeCounts()
    suspend fun allFolders() = dao.allFolders()
    suspend fun count() = dao.count()

    suspend fun pagedForAlbumRandom(bucketIds: Set<String>): LibraryItemEntity? =
        dao.randomInBuckets(bucketIds.toList())

    /** Opens the raw bytes of an item for hashing; null when unavailable. */
    fun openForHash(item: LibraryItemEntity): java.io.InputStream? = runCatching {
        context.contentResolver.openInputStream(android.net.Uri.parse(item.uri))
    }.getOrNull()

    /** Decodes a small bitmap for perceptual hashing. */
    fun decodeForHash(item: LibraryItemEntity): android.graphics.Bitmap? = runCatching {
        if (item.isVideo) return null
        context.contentResolver.openInputStream(android.net.Uri.parse(item.uri))?.use { stream ->
            com.murai.gallery.domain.hash.Hashing.decodeSmall(stream)
        }
    }.getOrNull()

    fun observeBin() = dao.observeBin()
    fun observeFavorites() = dao.observeFavorites()
    fun observeAlbums() = dao.observeAlbums()
    fun observeCount() = dao.observeCount()

    companion object {
        val PAGING_CONFIG = PagingConfig(
            pageSize = 120,
            initialLoadSize = 240,
            prefetchDistance = 60,
            enablePlaceholders = false
        )
    }
}

fun Context.mediaRepository(db: MuraiDatabase): MediaRepository =
    MediaRepository(this, db, MediaScanner(this, db), MediaOperations(this, db))
