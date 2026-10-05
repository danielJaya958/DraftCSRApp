package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class SrCategory(val label: String, val kategori: String?) {
    ALL("Semua", null),
    DRAFT("Draft", "Draft"),
    EXTERNAL("External", "External"),
    INTERNAL("Internal", "Internal")
}

enum class SignFilter { ALL, SIGNED, UNSIGNED }

data class SrFilter(
    // Tengah malam waktu lokal (dari DatePickerDialog), batas inklusif
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val technicians: Set<String> = emptySet(),
    val sign: SignFilter = SignFilter.ALL
) {
    val activeCount: Int
        get() = (if (fromMillis != null || toMillis != null) 1 else 0) +
            (if (technicians.isNotEmpty()) 1 else 0) +
            (if (sign != SignFilter.ALL) 1 else 0)
}

data class SrUiState(
    val loaded: List<DraftItem> = emptyList(),
    val visible: List<DraftItem> = emptyList(),
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val initialLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
    val sessionExpired: Boolean = false,
    val search: String = "",
    val category: SrCategory = SrCategory.ALL,
    val filter: SrFilter = SrFilter(),
    // Teknisi yang muncul di report yang sudah dimuat (untuk pilihan di dialog filter)
    val technicians: List<String> = emptyList()
) {
    // Kategori, teknisi, tanggal & status TTD disaring di aplikasi: endpoint GetServiceReportIT
    // hanya menerima pageNumber, pageSize & search.
    val isFiltering: Boolean get() = category != SrCategory.ALL || filter.activeCount > 0
}

@HiltViewModel
class ServiceReportViewModel @Inject constructor(
    private val repository: DraftRepository
) : ViewModel() {

    private val pageSize = 25

    private val _state = MutableStateFlow(SrUiState())
    val state: StateFlow<SrUiState> = _state.asStateFlow()

    private var started = false
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    // Saat filter aktif, satu halaman bisa saja tidak berisi report yang cocok. Halaman
    // berikutnya dimuat otomatis sampai ada AUTO_LOAD_STEP hasil baru, maksimal
    // MAX_AUTO_PAGES halaman per pemicu supaya tidak menyedot 200+ halaman sekaligus.
    private var autoTarget = 0
    private var autoPages = 0

    fun start() {
        if (started) return
        started = true
        refresh()
    }

    fun refreshIfStarted() {
        if (started) refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update {
            it.copy(
                loaded = emptyList(),
                visible = emptyList(),
                currentPage = 0,
                totalItems = 0,
                totalPages = 0,
                endReached = false,
                error = null,
                initialLoading = true,
                loadingMore = false
            )
        }
        resetAutoLoad()
        load(1)
    }

    fun loadMore() {
        val s = _state.value
        if (loadJob?.isActive == true || s.endReached || s.loaded.isEmpty()) return
        resetAutoLoad()
        load(s.currentPage + 1)
    }

    fun setSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed == _state.value.search) return
        _state.update { it.copy(search = trimmed) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            if (started) refresh()
        }
    }

    fun setCategory(category: SrCategory) {
        if (category == _state.value.category) return
        _state.update { applyFilters(it.copy(category = category)) }
        resetAutoLoad()
        maybeAutoLoad()
    }

    fun setFilter(filter: SrFilter) {
        _state.update { applyFilters(it.copy(filter = filter)) }
        resetAutoLoad()
        maybeAutoLoad()
    }

    fun resetFilters() {
        _state.update { applyFilters(it.copy(category = SrCategory.ALL, filter = SrFilter())) }
    }

    private fun resetAutoLoad() {
        autoTarget = _state.value.visible.size + AUTO_LOAD_STEP
        autoPages = 0
    }

    private fun maybeAutoLoad() {
        val s = _state.value
        if (!s.isFiltering || s.endReached || s.loaded.isEmpty()) return
        if (loadJob?.isActive == true) return
        if (s.visible.size >= autoTarget || autoPages >= MAX_AUTO_PAGES) return
        autoPages++
        load(s.currentPage + 1)
    }

    private fun load(page: Int) {
        if (page > 1) _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch {
            repository.getServiceReports(page, pageSize, _state.value.search).fold(
                onSuccess = { p ->
                    _state.update {
                        val loaded = it.loaded + p.items
                        applyFilters(
                            it.copy(
                                loaded = loaded,
                                currentPage = page,
                                totalItems = p.totalItems,
                                totalPages = p.totalPages,
                                endReached = page >= p.totalPages || p.items.isEmpty(),
                                initialLoading = false,
                                loadingMore = false,
                                error = null,
                                technicians = (it.technicians + p.items.mapNotNull { r ->
                                    r.username?.trim()?.ifEmpty { null }
                                }).distinct()
                            )
                        )
                    }
                    loadJob = null
                    maybeAutoLoad()
                },
                onFailure = { e ->
                    _state.update {
                        if (e is SessionExpiredException) {
                            it.copy(initialLoading = false, loadingMore = false, sessionExpired = true)
                        } else {
                            // Gagal memuat halaman berikutnya: daftar lama tetap ditampilkan
                            it.copy(initialLoading = false, loadingMore = false, error = e)
                        }
                    }
                }
            )
        }
    }

    private fun applyFilters(s: SrUiState): SrUiState =
        s.copy(visible = s.loaded.filter { matches(it, s.category, s.filter) })

    private fun matches(item: DraftItem, category: SrCategory, f: SrFilter): Boolean {
        if (category.kategori != null && !item.kategori.equals(category.kategori, ignoreCase = true)) {
            return false
        }
        if (f.technicians.isNotEmpty() && item.username?.trim() !in f.technicians) return false
        when (f.sign) {
            SignFilter.SIGNED -> if (item.isCustomerSigned != true) return false
            SignFilter.UNSIGNED -> if (item.isCustomerSigned == true) return false
            SignFilter.ALL -> Unit
        }
        if (f.fromMillis != null || f.toMillis != null) {
            val date = parseServerDate(item.reportDate)?.time ?: return false
            if (f.fromMillis != null && date < startOfDay(f.fromMillis)) return false
            if (f.toMillis != null && date >= startOfDay(f.toMillis) + DAY_MILLIS) return false
        }
        return true
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val AUTO_LOAD_STEP = 10
        const val MAX_AUTO_PAGES = 8
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
