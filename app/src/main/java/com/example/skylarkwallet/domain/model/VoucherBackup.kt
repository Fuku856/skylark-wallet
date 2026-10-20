package com.example.skylarkwallet.domain.model

/** 端末移行用に書き出す、復号済みの券。ID は移行先で振り直す。 */
data class VoucherBackup(
    val code9: String,
    val barcode13: String?,
    val resultHtml: String?,
    val balance: Int?,
    val expiryAt: Long?,
    val balanceCheckedAt: Long?,
    val lastUpdatedAt: Long?,
    val memo: String,
    val createdAt: Long,
)

/** バックアップから読み込んだ結果。 */
data class ImportSummary(
    val added: Int,
    /** 同じ9桁が登録済み（またはファイル内で重複）のため飛ばした数。 */
    val skippedDuplicate: Int,
    /** 9桁の形式が正しくないため飛ばした数。 */
    val skippedInvalid: Int,
)
