package app.skylark.wallet.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 優待券の保存形式。9桁・13桁・結果 HTML は暗号文（Base64）のみを保存する。
 * 「使用済み」は保存せず `balance == 0` から導出する。
 */
@Entity(tableName = "vouchers")
data class VoucherEntity(
    @PrimaryKey val id: String,
    val encryptedCode9: String,
    val encryptedBarcode13: String? = null,
    val encryptedResultHtml: String? = null,
    val balance: Int? = null,
    val expiryAt: Long? = null,
    val balanceCheckedAt: Long? = null,
    val lastUpdatedAt: Long? = null,
    val memo: String = "",
    val createdAt: Long,
    /** 直近の照会で「無効なコード」と判定されたときのサイトのメッセージ。成功時に null に戻す。 */
    val lastError: String? = null,
)
