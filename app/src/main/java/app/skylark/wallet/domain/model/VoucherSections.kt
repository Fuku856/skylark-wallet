package app.skylark.wallet.domain.model

/** ホームの区分（未使用・使用済み）と並び順。バーコード画面もこれに合わせて券を並べる。 */
data class VoucherSections(
    /** 未使用の券（期限の近い順）。 */
    val active: List<Voucher>,
    /** 残高0円の券（新しく登録したものほど前）。 */
    val used: List<Voucher>,
) {
    /**
     * バーコード画面で並べる券。
     * [initialId] があればその券と同じ区分の券をホームと同じ順で返す。
     * 無ければ未使用かつ期限内の券を期限の近い順で返す（期限切れの券は出さない）。
     */
    fun barcodeList(initialId: String?, now: Long): List<Voucher> {
        val active = active.filter { it.barcode13 != null }
        val used = used.filter { it.barcode13 != null }
        return when {
            initialId != null && active.any { it.id == initialId } -> active
            initialId != null && used.any { it.id == initialId } -> used
            else -> active.filterNot { it.isExpired(now) }
        }
    }

    companion object {
        fun of(vouchers: List<Voucher>): VoucherSections = VoucherSections(
            active = vouchers.filterNot { it.isUsed }.sortedWith(Voucher.ExpiryOrder),
            used = vouchers.filter { it.isUsed }.sortedByDescending { it.createdAt },
        )
    }
}
