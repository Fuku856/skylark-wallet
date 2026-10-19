package com.example.skylarkwallet.data.repository

import com.example.skylarkwallet.data.remote.ResultParser
import com.example.skylarkwallet.data.remote.SkylarkScraper
import com.example.skylarkwallet.data.remote.SkylarkSession
import com.example.skylarkwallet.domain.model.LookupOutcome
import com.example.skylarkwallet.domain.repository.LookupRepository
import com.example.skylarkwallet.domain.repository.LookupSession
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
