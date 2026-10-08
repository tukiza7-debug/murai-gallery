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
 */
class ViewerViewModel(
    private val container: AppContainer,
    private val scopeKey: String,
    anchorId: Long
) : ViewModel() {

    val ids = MutableStateFlow<List<Long>>(emptyList())
    val index = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val ordered = withContext(Dispatchers.Default) { buildOrder(scopeKey) }
            ids.value = ordered
            index.value = ordered.indexOf(anchorId).coerceAtLeast(0)
        }
    }

    private suspend fun buildOrder(scopeKey: String): List<Long> {
        val rows = container.db.libraryDao().sortKeyRows().toMutableList()
        val filtered = when {
            scopeKey == "home" -> rows
            scopeKey == "favorites" -> rows.filter { it.favorite }
            scopeKey.startsWith("album:") -> {
                val bucket = scopeKey.removePrefix("album:")
                rows.filter { it.bucketId == bucket }
            }
            scopeKey == "search" -> {
                val f = SearchQueryHolder.last
                rows.filter { row ->
                    (!f.onlyImages || !row.isVideo) &&
                        (!f.onlyVideos || row.isVideo) &&
                        (f.query.isNullOrBlank() ||
                            row.name.contains(f.query, ignoreCase = true) ||
                            row.folder.contains(f.query, ignoreCase = true))
                }
            }
            else -> rows
        }
        val spec = container.settings.sortSpec(currentScreen(scopeKey)).first()
        val list = filtered.toMutableList()
        SortEngine.sort(list, spec.option, spec.direction, spec.reshuffleSeed)
        return list.map { it.id }
    }

    private fun currentScreen(scopeKey: String): SortScreen = when {
        scopeKey.startsWith("album:") -> SortScreen.ALBUM
        scopeKey == "favorites" -> SortScreen.FAVORITES
        scopeKey == "search" -> SortScreen.SEARCH
        else -> SortScreen.HOME
    }

    suspend fun current(): LibraryItemEntity? {
        val id = ids.value.getOrNull(index.value) ?: return null
        return container.db.libraryDao().byId(id)
    }

    suspend fun at(position: Int): LibraryItemEntity? {
        val id = ids.value.getOrNull(position) ?: return null
        return container.db.libraryDao().byId(id)
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

/** Extracts the embedded MP4 trailer from a motion photo (JPEG + MP4). */
object MotionPhotoExtractor {
    fun extract(context: android.content.Context, jpegPath: String): java.io.File? {
        return runCatching {
            val file = java.io.File(jpegPath)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            val sig = "ftyp".toByteArray(Charsets.US_ASCII)
            var trailerStart = -1
            var i = 4096
            while (i < bytes.size - 4) {
                if (bytes[i] == sig[0] && bytes[i + 1] == sig[1] &&
                    bytes[i + 2] == sig[2] && bytes[i + 3] == sig[3]
                ) {
                    trailerStart = i - 4
                    break
                }
                i++
            }
            if (trailerStart <= 0) return null
            val out = java.io.File(context.cacheDir, "motion_${file.nameWithoutExtension}.mp4")
            out.outputStream().use { it.write(bytes, trailerStart, bytes.size - trailerStart) }
            out
        }.getOrNull()
    }
}
