package com.example.skylarkwallet.domain.model

/** 結果画面から読み取った値。 */
data class VoucherResult(
    val balance: Int,
    val barcode13: String,
    val expiryAt: Long?,
    val balanceCheckedAt: Long?,
)
