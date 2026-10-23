package app.skylark.wallet.domain.repository

import app.skylark.wallet.domain.model.ImportSummary
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.domain.model.VoucherBackup
import app.skylark.wallet.domain.model.VoucherResult
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

    /** 復号できない（端末の暗号鍵が無効になったなど）ため一覧に出せない券の数。 */
    fun observeUnreadableCount(): Flow<Int>

    /** 復号できない券をすべて削除する。 */
    suspend fun deleteUnreadable()

    /** 端末移行用に、復号できる券をすべて復号して返す（復号できない券は含めない）。 */
    suspend fun exportBackup(): List<VoucherBackup>

    /** バックアップの券を追加する。同じ9桁が登録済みの券と形式が正しくない券は飛ばす。 */
    suspend fun importBackup(entries: List<VoucherBackup>): ImportSummary
}
