package com.ksm.draftcsrapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val items: List<DraftItem> = emptyList(),
    val totalItems: Int = 0,
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DraftRepository
) : ViewModel() {

    private val pageSize = 10
    private var currentPage = 0
    private var busy = false

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (busy) return
        currentPage = 0
        _state.value = HomeUiState(isLoading = true)
        load(1)
    }

    fun loadMore() {
        val s = _state.value
        if (busy || s.isLoading || s.endReached || s.items.isEmpty()) return
        load(currentPage + 1)
    }

    private fun load(page: Int) {
        busy = true
        viewModelScope.launch {
            repository.getDrafts(page, pageSize).fold(
                onSuccess = { p ->
                    currentPage = page
                    _state.update {
                        it.copy(
                            items = it.items + p.items,
                            totalItems = p.totalItems,
                            isLoading = false,
                            endReached = page >= p.totalPages || p.items.isEmpty(),
                            error = null
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        if (e is SessionExpiredException) {
                            it.copy(isLoading = false, sessionExpired = true)
                        } else {
                            // Gagal load halaman berikutnya: daftar lama dipertahankan
                            it.copy(
                                isLoading = false,
                                error = if (it.items.isEmpty()) e.message else null
                            )
                        }
                    }
                }
            )
            busy = false
        }
    }
}
