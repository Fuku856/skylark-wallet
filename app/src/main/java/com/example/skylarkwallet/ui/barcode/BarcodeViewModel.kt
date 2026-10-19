package com.example.skylarkwallet.ui.barcode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.domain.model.Voucher
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
        val now = System.currentTimeMillis()
        val withBarcode = all.filter { it.barcode13 != null }.sortedWith(Voucher.ExpiryOrder)
        // 未使用・期限内を優先して表示する。指定された券がそれ以外なら、その券も含める
        val usable = withBarcode.filter { !it.isUsed && !it.isExpired(now) }
        val list = if (initialId == null || usable.any { it.id == initialId }) {
            usable.ifEmpty { withBarcode }
        } else {
            withBarcode
        }
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
