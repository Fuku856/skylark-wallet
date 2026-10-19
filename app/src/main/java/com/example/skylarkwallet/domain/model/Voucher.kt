package com.example.skylarkwallet.domain.model

import java.util.concurrent.TimeUnit

/** 復号済みの優待券。 */
data class Voucher(
    val id: String,
    val code9: String,
    val barcode13: String?,
    val balance: Int?,
    val expiryAt: Long?,
    val balanceCheckedAt: Long?,
    val lastUpdatedAt: Long?,
    val memo: String,
    val createdAt: Long,
    val hasResultHtml: Boolean,
    val lastError: String?,
) {
    val last3: String get() = code9.takeLast(3)

    /** 一度でも照会に成功しているか。 */
    val isChecked: Boolean get() = balance != null

    val isUsed: Boolean get() = balance == 0

    fun isExpired(now: Long): Boolean = expiryAt != null && expiryAt < now

    /** 有効期限までの残り日数（切り上げ）。期限不明なら null。 */
    fun daysUntilExpiry(now: Long): Long? = expiryAt?.let {
        val diff = it - now
        if (diff < 0) -1 else (diff + DAY_MILLIS - 1) / DAY_MILLIS
    }

    fun isExpiringSoon(now: Long): Boolean {
        val days = daysUntilExpiry(now) ?: return false
        return days in 0..EXPIRING_SOON_DAYS
    }

    /** 最終確認から日数が経っていて、再照会をすすめたい券か。 */
    fun needsRefresh(now: Long): Boolean {
        if (!isChecked || isUsed) return false
        val updated = lastUpdatedAt ?: return true
        return now - updated >= STALE_DAYS * DAY_MILLIS
    }

    /** 一括更新の対象（未使用かつ期限切れでない）。 */
    fun isRefreshTarget(now: Long): Boolean = !isUsed && !isExpired(now)

    /** 合計残高に含める券（残高確認済み・未使用・期限内）。 */
    fun countsToTotal(now: Long): Boolean = balance != null && balance > 0 && !isExpired(now)

    companion object {
        val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)
        const val EXPIRING_SOON_DAYS = 30L
        const val STALE_DAYS = 7L

        /** 期限の近い順（期限不明は最後、同じなら登録順）。 */
        val ExpiryOrder: Comparator<Voucher> =
            compareBy<Voucher, Long?>(nullsLast()) { it.expiryAt }.thenBy { it.createdAt }
    }
}
