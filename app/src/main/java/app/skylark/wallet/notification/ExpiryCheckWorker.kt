package app.skylark.wallet.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.skylark.wallet.R
import app.skylark.wallet.data.local.db.VoucherDao
import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.domain.model.Voucher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 有効期限の30日前・7日前にローカル通知を出す。
 * 9桁・13桁は復号せず、平文で保存しているメモ・残高・期限だけを使う。
 */
@HiltWorker
class ExpiryCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: VoucherDao,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.current().expiryNotification) return Result.success()
        val now = System.currentTimeMillis()
        val notified = settings.notifiedKeys().toMutableSet()
        val vouchers = dao.getAll()
        val existingIds = vouchers.map { it.id }.toSet()
        // 削除済みの券の記録は消す
        notified.retainAll { key -> key.substringBefore(':') in existingIds }

        for (voucher in vouchers) {
            val expiry = voucher.expiryAt ?: continue
            // 使用済み（残高0円）と期限切れは通知しない
            if (voucher.balance == 0) continue
            val days = Voucher.daysUntil(expiry, now).takeIf { it >= 0 } ?: continue
            val threshold = ExpiryNotifier.NOTIFY_DAYS.filter { days <= it }.minOrNull() ?: continue
            val key = "${voucher.id}:$threshold"
            if (key in notified) continue

            val name = voucher.memo.ifBlank { applicationContext.getString(R.string.voucher_default_name) }
            val balance = voucher.balance?.let { applicationContext.getString(R.string.yen_format, it) }
                ?: applicationContext.getString(R.string.balance_unknown)
            ExpiryNotifier.notify(
                applicationContext,
                notificationId = key.hashCode(),
                title = applicationContext.getString(R.string.notification_expiry_title, days),
                text = applicationContext.getString(R.string.notification_expiry_text, name, balance),
            )
            notified += key
        }
        settings.setNotifiedKeys(notified)
        return Result.success()
    }
}
