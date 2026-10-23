package app.skylark.wallet.data.repository

import app.skylark.wallet.data.remote.ResultParser
import app.skylark.wallet.data.remote.SkylarkScraper
import app.skylark.wallet.data.remote.SkylarkSession
import app.skylark.wallet.domain.model.LookupOutcome
import app.skylark.wallet.domain.repository.LookupRepository
import app.skylark.wallet.domain.repository.LookupSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkylarkLookupRepository @Inject constructor(
    private val scraper: SkylarkScraper,
) : LookupRepository {

    override suspend fun openSession(): LookupSession {
        val session = scraper.newSession()
        try {
            scraper.open(session)
        } catch (e: Throwable) {
            session.close()
            throw e
        }
        return SkylarkLookupSession(scraper, session)
    }
}

private class SkylarkLookupSession(
    private val scraper: SkylarkScraper,
    private val session: SkylarkSession,
) : LookupSession {

    override suspend fun fetchCaptcha(): ByteArray = scraper.fetchCaptcha(session)

    override suspend fun submit(code9: String, captcha: String, termsAgreed: Boolean): LookupOutcome {
        val actionUrl = session.form?.actionUrl ?: throw IllegalStateException("session not opened")
        val html = scraper.submit(session, code9, captcha, termsAgreed)
        return when (val parsed = ResultParser.parse(html)) {
            is ResultParser.Outcome.Success ->
                LookupOutcome.Success(parsed.result, scraper.inlineBarcodeImages(session, html, actionUrl))
            is ResultParser.Outcome.CaptchaMismatch -> LookupOutcome.CaptchaMismatch(parsed.message)
            is ResultParser.Outcome.InvalidCode -> LookupOutcome.InvalidCode(parsed.message)
            ResultParser.Outcome.Maintenance -> LookupOutcome.Maintenance
            ResultParser.Outcome.Unknown -> LookupOutcome.ParseFailure
        }
    }

    override fun close() = session.close()
}
