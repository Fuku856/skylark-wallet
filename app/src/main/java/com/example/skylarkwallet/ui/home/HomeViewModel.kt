package com.example.skylarkwallet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.model.VoucherSections
import com.example.skylarkwallet.domain.repository.VoucherRepository
import com.example.skylarkwallet.domain.usecase.DeleteVoucherUseCase
import com.example.skylarkwallet.domain.usecase.RegisterVoucherUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VoucherRepository,
    private val registerVoucher: RegisterVoucherUseCase,
    private val deleteVoucher: DeleteVoucherUseCase,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        /** 未使用の券（期限の近い順）。 */
        val active: List<Voucher> = emptyList(),
        /** 残高0円の券（登録順）。 */
        val used: List<Voucher> = emptyList(),
        /** 未使用・期限内の券の合計残高。 */
        val total: Int = 0,
        val lastUpdatedAt: Long? = null,
        val now: Long = System.currentTimeMillis(),
        /** 復号できず表示できない券の数。 */
        val unreadable: Int = 0,
    )

    val state: StateFlow<UiState> = repository.observeVouchers()
        .combine(repository.observeUnreadableCount()) { vouchers, unreadable ->
            val now = System.currentTimeMillis()
            val sections = VoucherSections.of(vouchers)
            UiState(
                loading = false,
                active = sections.active,
                used = sections.used,
                total = vouchers.filter { it.countsToTotal(now) }.sumOf { it.balance ?: 0 },
                lastUpdatedAt = vouchers.mapNotNull { it.lastUpdatedAt }.maxOrNull(),
                now = now,
                unreadable = unreadable,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    suspend fun register(code: String, memo: String): RegisterVoucherUseCase.Result = registerVoucher(code, memo)

    fun updateMemo(id: String, memo: String) {
        viewModelScope.launch { repository.updateMemo(id, memo) }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteVoucher(id) }
    }

    fun deleteUnreadable() {
        viewModelScope.launch { repository.deleteUnreadable() }
    }
}
