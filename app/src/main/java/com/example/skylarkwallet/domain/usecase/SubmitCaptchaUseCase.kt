package com.example.skylarkwallet.domain.usecase

import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.domain.model.LookupOutcome
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.repository.LookupSession
import com.example.skylarkwallet.domain.repository.VoucherRepository
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
