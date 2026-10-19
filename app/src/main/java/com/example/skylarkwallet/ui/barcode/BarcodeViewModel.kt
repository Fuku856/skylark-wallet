package com.example.skylarkwallet.ui.barcode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.model.VoucherSections
import com.example.skylarkwallet.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: VoucherRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val initialId: String? = savedStateHandle.get<String>(ARG_ID)?.ifBlank { null }

    data class UiState(
        val loading: Boolean = true,
        val vouchers: List<Voucher> = emptyList(),
        val initialPage: Int = 0,
        val autoBrightness: Boolean = true,
    )

    val state: StateFlow<UiState> = combine(repository.observeVouchers(), settingsRepository.settings) { all, settings ->
        // ホームと同じ区分・同じ順で並べる（使用済みの券から開いたときは使用済みの券だけ）
        val list = VoucherSections.of(all).barcodeList(initialId, System.currentTimeMillis())
        UiState(
            loading = false,
            vouchers = list,
            initialPage = list.indexOfFirst { it.id == initialId }.coerceAtLeast(0),
            autoBrightness = settings.autoBrightness,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    companion object {
        const val ARG_ID = "id"
    }
}
