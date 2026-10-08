package com.murai.gallery.ui.screens.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.PagingData
import com.murai.gallery.di.AppContainer
import com.murai.gallery.data.db.entity.LibraryItemEntity
import com.murai.gallery.domain.model.GroupMode
import com.murai.gallery.domain.model.SortDirection
import com.murai.gallery.domain.model.SortOption
import com.murai.gallery.domain.model.SortSpec
import com.murai.gallery.data.prefs.SortScreen
import com.murai.gallery.domain.sort.SortEngine
import com.murai.gallery.util.ConsentBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface GalleryCell {
    data class Header(val label: String) : GalleryCell
    data class Item(val entity: LibraryItemEntity) : GalleryCell
}

data class GalleryUiState(
    val spec: SortSpec = SortSpec(),
    val selection: Set<Long> = emptySet(),
    val selectionMode: Boolean = false,
    val spanCount: Int = 3,
    val totalCount: Int = 0,
    val scanning: Boolean = false
)

/**
 * Home timeline. Sort keys are projected to a minimal row, sorted off the
 * main thread (~6 MB for 50k items), and paged back into the grid so the UI
 * paints immediately and scrolling stays smooth.
 */
class GalleryViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state

    private val sortedIds = MutableStateFlow<List<Long>>(emptyList())

    val sortPresets = container.db.sortPresetsDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            container.settings.sortSpec(SortScreen.HOME).collect { spec ->
                _state.value = _state.value.copy(spec = spec)
                rebuild(spec)
            }
        }
        viewModelScope.launch {
            container.settings.gridSize.collect { span ->
                _state.value = _state.value.copy(spanCount = span)
            }
        }
        viewModelScope.launch {
            container.repository.observeCount().collect { n ->
                _state.value = _state.value.copy(totalCount = n)
            }
        }
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true)
            container.scanner.scan { }
            _state.value = _state.value.copy(scanning = false)
            rebuild(_state.value.spec)
        }
    }

    fun setSpec(spec: SortSpec, persist: Boolean = true) {
        _state.value = _state.value.copy(spec = spec)
        if (persist) viewModelScope.launch {
            container.settings.setSortSpec(SortScreen.HOME, spec)
        }
        rebuild(spec)
    }

    private fun rebuild(spec: SortSpec) {
        viewModelScope.launch(Dispatchers.Default) {
            val rows = container.db.libraryDao().sortKeyRows().toMutableList()
            SortEngine.sort(rows, spec.option, spec.direction, spec.reshuffleSeed)
            sortedIds.emit(rows.map { it.id })
        }
    }

    fun pages(): PagingSource<Int, GalleryCell> =
        SortedCellsPagingSource(container, sortedIds.value, _state.value.spec.group)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun pagesAsFlow(): Flow<PagingData<GalleryCell>> =
        combine(sortedIds, _state) { ids, st -> ids to st.spec.group }
            .flatMapLatest { (ids, group) ->
                Pager(PagingConfig(pageSize = 90, initialLoadSize = 180, enablePlaceholders = false)) {
                    SortedCellsPagingSource(container, ids, group)
                }.flow
            }

    fun savePreset(name: String, spec: SortSpec) {
        viewModelScope.launch {
            container.db.sortPresetsDao().insert(
                com.murai.gallery.data.db.entity.SortPresetEntity(
                    name = name,
                    sortOptionId = spec.option.id,
                    ascending = spec.direction == SortDirection.ASCENDING,
                    groupId = spec.group.id,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Runs [action] against the currently selected entities. */
    fun selectionAction(action: suspend (List<LibraryItemEntity>) -> Unit) {
        viewModelScope.launch {
            val entities = container.db.libraryDao().byIds(_state.value.selection.toList())
            if (entities.isNotEmpty()) action(entities)
        }
    }

    fun trashSelection(onDone: () -> Unit) {
        viewModelScope.launch {
            val entities = container.db.libraryDao().byIds(_state.value.selection.toList())
            val result = container.operations.trash(entities)
            if (result.consent != null) {
                ConsentBus.request(result.consent) {
                    viewModelScope.launch {
                        container.db.libraryDao().setTrashed(entities.map { it.id }, true)
                        onDone()
                    }
                }
            } else {
                container.db.libraryDao().setTrashed(entities.map { it.id }, true)
                onDone()
            }
        }
    }

    fun toggleSelection(id: Long) {
        val current = _state.value.selection
        val next = if (id in current) current - id else current + id
        _state.value = _state.value.copy(selection = next, selectionMode = next.isNotEmpty())
    }

    fun enterSelection(id: Long) {
        _state.value = _state.value.copy(selection = setOf(id), selectionMode = true)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selection = emptySet(), selectionMode = false)
    }

    fun setSpan(span: Int) {
        viewModelScope.launch { container.settings.setGridSize(span) }
    }

    suspend fun entities(ids: Collection<Long>): List<LibraryItemEntity> =
        container.db.libraryDao().byIds(ids.toList())
}

/**
 * Pages cells from a precomputed sorted id list. Group headers are derived
 * from the last item of the previous page, so boundaries stay correct.
 */
class SortedCellsPagingSource(
    private val container: AppContainer,
    private val ids: List<Long>,
    private val group: GroupMode
) : PagingSource<Int, GalleryCell>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, GalleryCell> =
        withContext(Dispatchers.IO) {
            val page = params.key ?: 0
            val from = page * PAGE_SIZE
            if (from >= ids.size) return@withContext LoadResult.Page(emptyList(), null, null)
            val slice = ids.subList(from, minOf(from + PAGE_SIZE, ids.size))
            val items = container.db.libraryDao().byIds(slice)
            val byId = items.associateBy { it.id }
            val ordered = slice.mapNotNull { byId[it] }
            val cells = ArrayList<GalleryCell>(ordered.size + 2)
            if (group != GroupMode.NONE) {
                ordered.firstOrNull()?.let {
                    cells.add(GalleryCell.Header(SortEngine.groupLabel(it, group.id)))
                }
            }
            for (i in ordered.indices) {
                val item = ordered[i]
                cells.add(GalleryCell.Item(item))
                if (group != GroupMode.NONE) {
                    val next = ordered.getOrNull(i + 1)
                    if (next != null && SortEngine.groupLabel(item, group.id) !=
                        SortEngine.groupLabel(next, group.id)
                    ) {
                        cells.add(GalleryCell.Header(SortEngine.groupLabel(next, group.id)))
                    }
                }
            }
            val nextKey = if (from + PAGE_SIZE < ids.size) page + 1 else null
            LoadResult.Page(cells, prevKey = if (page > 0) page - 1 else null, nextKey = nextKey)
        }

    override fun getRefreshKey(state: PagingState<Int, GalleryCell>): Int? =
        state.anchorPosition?.let { anchor -> anchor / PAGE_SIZE }

    companion object {
        const val PAGE_SIZE = 90
    }
}
