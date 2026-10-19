package app.skylark.wallet.domain.usecase

import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.domain.model.LookupOutcome
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.domain.repository.LookupSession
import app.skylark.wallet.domain.repository.VoucherRepository
import javax.inject.Inject

/** CAPTCHA を送信し、結果に応じて券を更新する。 */
class SubmitCaptchaUseCase @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(session: LookupSession, voucher: Voucher, captcha: String): LookupOutcome {
        val agreed = settingsRepository.current().termsAgreed
        val outcome = session.submit(voucher.code9, captcha, agreed)
        when (outcome) {
            is LookupOutcome.Success -> voucherRepository.saveResult(voucher.id, outcome.result, outcome.html)
            is LookupOutcome.InvalidCode -> voucherRepository.markError(voucher.id, outcome.message.orEmpty())
            else -> Unit
        }
        return outcome
    }
}
