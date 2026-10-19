package com.example.skylarkwallet.domain.repository

import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.model.VoucherResult
import kotlinx.coroutines.flow.Flow

interface VoucherRepository {
    fun observeVouchers(): Flow<List<Voucher>>
    suspend fun getVouchers(): List<Voucher>
    suspend fun getVoucher(id: String): Voucher?

    /** 復号した9桁で重複を探す。 */
    suspend fun findByCode(code9: String): Voucher?
    suspend fun add(code9: String, memo: String): Voucher
    suspend fun saveResult(id: String, result: VoucherResult, html: String)
    suspend fun markError(id: String, message: String)
    suspend fun updateMemo(id: String, memo: String)
    suspend fun getResultHtml(id: String): String?
    suspend fun delete(id: String)
}
