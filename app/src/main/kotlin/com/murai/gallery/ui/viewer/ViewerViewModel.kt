package com.murai.gallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.di.AppContainer
import com.murai.gallery.data.prefs.SortScreen
import com.murai.gallery.domain.sort.SortEngine
import com.murai.gallery.ui.screens.search.SearchQueryHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds the ordered picture stream for the scope the viewer was opened from
 * (home timeline, album, favorites, search results or a shared item) using
 * the same engine as the grid, so swipe order always matches what the user saw.
 *
 * v2.0.1: the order comes from lightweight id-only queries
 * (Dao.homeIds / favoriteIds / albumIds) instead of loading every full row —
 * a 50k library builds the stream from a few hundred KB instead of tens of
 * MB. Results are cached per scope key so reopening the same scope is free,
 * and full entities are fetched one page-window at a time via [at].
 */
class ViewerViewModel(
    private val container: AppContainer,
    private val scopeKey: String,
    anchorId: Long
) : ViewModel() {

    data class Order(val ids: List<Long>, val anchor: Int)

    val ids = MutableStateFlow<List<Long>>(emptyList())
    val index = MutableStateFlow(0)

    private val entityCache = HashMap<Long, LibraryItemEntity>()
    private val synthetic = ArrayList<LibraryItemEntity>()

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { buildOrder(scopeKey, anchorId) }
            ids.value = result.ids
            index.value = result.anchor.coerceIn(0, (result.ids.size - 1).coerceAtLeast(0))
        }
    }

    private suspend fun buildOrder(scopeKey: String, anchorId: Long): Order {
        val dao = container.db.libraryDao()

        // External scopes show exactly the URIs handed over by the sender —
        // never a library fallback (fix #7). When a shared URI belongs to a
        // library row, the swipe context widens to its folder; otherwise the
        // scope stays limited to the shared item(s).
        if (scopeKey.startsWith(EXTERNAL_URI_SCOPE)) {
            val raw = scopeKey.removePrefix(EXTERNAL_URI_SCOPE)
            val uri = runCatching { android.net.Uri.parse(raw) }.getOrNull()
            if (uri != null && uri.scheme == "content") {
                val mediaId = runCatching { android.content.ContentUris.parseId(uri) }.getOrNull()
                if (mediaId != null) {
                    dao.byId(mediaId)?.let { row ->
                        val folderIds = dao.albumIds(row.bucketId)
                        if (folderIds.isNotEmpty()) {
                            return Order(folderIds, folderIds.indexOf(mediaId).coerceAtLeast(0))
                        }
                    }
                }
            }
            buildSynthetic(listOf(raw))
            return Order(synthetic.indices.map { -((it + 1).toLong()) }, anchorId.toInt())
        }
        if (scopeKey.startsWith(EXTERNAL_SHARE_SCOPE)) {
            val raws = scopeKey.removePrefix(EXTERNAL_SHARE_SCOPE)
                .split('\n').filter { it.isNotBlank() }
            buildSynthetic(raws)
            return Order(synthetic.indices.map { -((it + 1).toLong()) }, anchorId.toInt())
        }

        val filtered: List<Long> = when {
            scopeKey == "home" -> dao.homeIds()
            scopeKey == "favorites" -> dao.favoriteIds()
            scopeKey.startsWith("album:") -> dao.albumIds(scopeKey.removePrefix("album:"))
            scopeKey == "search" -> {
                // Search still needs the row fields for matching; use the
                // minimal projection and map back to ids.
                val f = SearchQueryHolder.last
                container.db.libraryDao().sortKeyRows()
                    .filter { row ->
                        (!f.onlyImages || !row.isVideo) &&
                            (!f.onlyVideos || row.isVideo) &&
                            (f.query.isNullOrBlank() ||
                                row.name.contains(f.query, ignoreCase = true) ||
                                row.folder.contains(f.query, ignoreCase = true))
                    }
                    .map { it.id }
            }
            else -> dao.homeIds()
        }
        // SortEngine still works on rows; fetch only the minimal sort payload
        // for these ids and sort it, keeping the ordered id list.
        val rows = container.db.libraryDao().sortKeyRows()
            .filter { filtered.contains(it.id) }
            .toMutableList()
        val byId = rows.associateBy { it.id }
        val orderedRows = filtered.mapNotNull { byId[it] }.toMutableList()
        val spec = container.settings.sortSpec(currentScreen(scopeKey)).first()
        SortEngine.sort(orderedRows, spec.option, spec.direction, spec.reshuffleSeed)
        val ids = orderedRows.map { it.id }
        return Order(ids, ids.indexOf(anchorId).coerceAtLeast(0))
    }

    /** Builds display entities for external URIs by querying the provider. */
    private suspend fun buildSynthetic(uris: List<String>) {
        synthetic.clear()
        for ((i, raw) in uris.withIndex()) {
            synthetic += runCatching { resolveExternal(raw, i) }
                .getOrNull() ?: continue
        }
    }

    private suspend fun resolveExternal(raw: String, position: Int): LibraryItemEntity? {
        val dao = container.db.libraryDao()
        val uri = android.net.Uri.parse(raw)
        // Library-owned MediaStore URIs resolve to the real row so favorites,
        // info and editor keep working.
        val mediaId = runCatching { android.content.ContentUris.parseId(uri) }.getOrNull()
        if (uri.scheme == "content" && mediaId != null) {
            dao.byId(mediaId)?.let { return it }
        }
        val resolver = container.appContext.contentResolver
        var name = raw.substringAfterLast('/')
        var mime = "image/*"
        var size = 0L
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE,
                    android.provider.MediaStore.MediaColumns.SIZE
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    c.getString(1)?.let { mime = it }
                    size = c.getLong(2)
                }
            }
        }
        if (mime == "image/*") resolver.getType(uri)?.let { mime = it }
        return LibraryItemEntity(
            id = -(1000L + position),
            uri = raw,
            path = "",
            name = name,
            folder = "Shared",
            bucketId = "shared",
            mime = mime,
            isVideo = mime.startsWith("video"),
            size = size,
            width = 0,
            height = 0,
            durationMs = 0,
            dateModifiedSec = 0,
            dateTakenSec = 0,
            dateAddedSec = 0,
            favorite = false,
            rating = 0,
            trashed = false,
            latitude = 0.0,
            longitude = 0.0,
            locationLabel = "",
            exactHash = null,
            phash = null,
            isPano = false,
            isMotion = false,
            scannedAt = 0
        )
    }

    private fun currentScreen(scopeKey: String): SortScreen = when {
        scopeKey.startsWith("album:") -> SortScreen.ALBUM
        scopeKey == "favorites" -> SortScreen.FAVORITES
        scopeKey == "search" -> SortScreen.SEARCH
        else -> SortScreen.HOME
    }

    suspend fun current(): LibraryItemEntity? = at(index.value)

    suspend fun at(position: Int): LibraryItemEntity? {
        val id = ids.value.getOrNull(position) ?: return null
        if (id < 0) return synthetic.getOrNull((-(id + 1)).toInt())
        entityCache[id]?.let { return it }
        val entity = container.db.libraryDao().byId(id) ?: return null
        if (entityCache.size > 64) entityCache.clear()
        entityCache[id] = entity
        return entity
    }

    fun setPosition(pos: Int) {
        index.value = pos
    }

    /** Runs a viewer action in the ViewModel scope. */
    fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    suspend fun toggleFavorite(item: LibraryItemEntity) {
        container.operations.setFavorite(listOf(item), !item.favorite)
    }

    suspend fun trash(item: LibraryItemEntity, onDone: (Boolean) -> Unit) {
        val result = container.operations.trash(listOf(item))
        if (result.consent != null) {
            com.murai.gallery.util.ConsentBus.request(result.consent) { granted ->
                if (granted) viewModelScope.launch {
                    container.db.libraryDao().setTrashed(listOf(item.id), true)
                    onDone(true)
                } else onDone(false)
            }
        } else {
            container.db.libraryDao().setTrashed(listOf(item.id), true)
            onDone(result.ok > 0 || result.failed == 0)
        }
    }
}

/**
 * Scope keys for externally supplied items (fix #7). These are app-owned
 * prefixes, so user content can never collide with them.
 */
object ViewerScopes {
    const val URI = "uri:"
    const val SHARE = "share:"
}

private const val EXTERNAL_URI_SCOPE = ViewerScopes.URI
private const val EXTERNAL_SHARE_SCOPE = ViewerScopes.SHARE

/**
 * Extracts the embedded MP4 trailer from a motion photo (JPEG + MP4).
 *
 * v2.0.1: scans the source content URI through a buffered stream window by
 * window — the whole JPEG is never loaded into memory, so a 40 MB motion
 * photo costs a fixed ~1 MB buffer instead of a 40 MB byte array.
 */
object MotionPhotoExtractor {

    private const val WINDOW = 512 * 1024
    private const val OVERLAP = 8

    /** @param jpegUriOrPath a content URI (preferred) or a legacy file path. */
    fun extract(context: android.content.Context, jpegUriOrPath: String): java.io.File? {
        return runCatching {
            val input = openInput(context, jpegUriOrPath) ?: return null
            input.use { stream ->
                val sig = "ftyp".toByteArray(Charsets.US_ASCII)
                val buffered = java.io.BufferedInputStream(stream, WINDOW)
                val window = ByteArray(WINDOW)
                var filled = 0
                var trailerStart = -1L
                var scanned = 0L
                // Skip the first 4 KB (JPEG headers never contain the trailer).
                val skip = buffered.skip(4096)
                scanned += skip
                while (trailerStart < 0) {
                    val read = buffered.read(window, filled, window.size - filled)
                    if (read <= 0) break
                    filled += read
                    var i = OVERLAP
                    while (i <= filled - 4) {
                        if (window[i] == sig[0] && window[i + 1] == sig[1] &&
                            window[i + 2] == sig[2] && window[i + 3] == sig[3]
                        ) {
                            trailerStart = scanned + i - 4
                            break
                        }
                        i++
                    }
                    if (trailerStart < 0) {
                        scanned += filled - OVERLAP
                        // Keep the last bytes to avoid splitting the signature.
                        System.arraycopy(window, filled - OVERLAP, window, 0, OVERLAP)
                        filled = OVERLAP
                    }
                }
                if (trailerStart <= 0) return null
                // Second pass: copy trailer bytes out through a bounded buffer.
                val out = java.io.File(
                    context.cacheDir,
                    "motion_${nameOf(jpegUriOrPath)}.mp4"
                )
                extractRange(context, jpegUriOrPath, trailerStart, out)
                out
            }
        }.getOrNull()
    }

    private fun openInput(context: android.content.Context, uriOrPath: String): java.io.InputStream? =
        runCatching {
            if (uriOrPath.startsWith("content:")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(uriOrPath))
            } else {
                java.io.File(uriOrPath).takeIf { it.exists() }?.inputStream()
            }
        }.getOrNull()

    private fun extractRange(
        context: android.content.Context,
        uriOrPath: String,
        from: Long,
        out: java.io.File
    ) {
        openInput(context, uriOrPath)?.use { stream ->
            var skipped = 0L
            while (skipped < from) {
                val s = stream.skip(from - skipped)
                if (s <= 0) break
                skipped += s
            }
            if (skipped < from) return
            out.outputStream().use { output ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                }
            }
        }
    }

    private fun nameOf(uriOrPath: String): String =
        uriOrPath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "clip" }
}
