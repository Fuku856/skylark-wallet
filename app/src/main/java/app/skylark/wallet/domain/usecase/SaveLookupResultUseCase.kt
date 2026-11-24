package app.skylark.wallet.domain.usecase

import app.skylark.wallet.data.remote.BarcodeImageInliner
import app.skylark.wallet.data.remote.ResultParser
import app.skylark.wallet.diagnostics.DebugLog
import app.skylark.wallet.domain.model.LookupOutcome
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.domain.repository.VoucherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/** 送信後に WebView で表示された結果ページを解析し、結果に応じて券を更新する。 */
class SaveLookupResultUseCase @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val inliner: BarcodeImageInliner,
) {
    /** [cookie] はバーコード画像の取得に使う、結果ページの WebView の Cookie。 */
    suspend operator fun invoke(voucher: Voucher, html: String, pageUrl: String, cookie: String?): LookupOutcome {
        val outcome = when (val parsed = withContext(Dispatchers.Default) { ResultParser.parse(html) }) {
            is ResultParser.Outcome.Success -> {
                DebugLog.log("lookup", "result ${ResultParser.selectorReport(html)}")
                val url = pageUrl.toHttpUrlOrNull()
                val inlined = if (url != null) inliner.inline(html, url, cookie) else html
                LookupOutcome.Success(parsed.result, inlined)
            }
            is ResultParser.Outcome.CaptchaMismatch -> LookupOutcome.CaptchaMismatch(parsed.message)
            is ResultParser.Outcome.InvalidCode -> LookupOutcome.InvalidCode(parsed.message)
            ResultParser.Outcome.Maintenance -> LookupOutcome.Maintenance
            ResultParser.Outcome.Unknown -> LookupOutcome.ParseFailure
        }
        when (outcome) {
            is LookupOutcome.Success -> voucherRepository.saveResult(voucher.id, outcome.result, outcome.html)
            is LookupOutcome.InvalidCode -> voucherRepository.markError(voucher.id, outcome.message.orEmpty())
            else -> Unit
        }
        return outcome
    }
}
