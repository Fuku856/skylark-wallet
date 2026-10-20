package app.skylark.wallet.ui.webview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.skylark.wallet.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfficialViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: VoucherRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val html: String, val checkedAt: Long?) : UiState
        data object Missing : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<String>(ARG_ID).orEmpty()
        viewModelScope.launch {
            val html = repository.getResultHtml(id)
            val voucher = repository.getVoucher(id)
            _state.value = if (html != null) UiState.Loaded(html, voucher?.lastUpdatedAt) else UiState.Missing
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
