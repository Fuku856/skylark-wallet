package com.example.skylarkwallet.domain.usecase

import com.example.skylarkwallet.domain.repository.LookupRepository
import com.example.skylarkwallet.domain.repository.LookupSession
import javax.inject.Inject

/** 新しいセッションで入力画面を開き、最初の CAPTCHA 画像を取得する。 */
class StartRefreshUseCase @Inject constructor(
    private val lookupRepository: LookupRepository,
) {
    data class Started(val session: LookupSession, val captcha: ByteArray)

    suspend operator fun invoke(): Started {
        val session = lookupRepository.openSession()
        try {
            return Started(session, session.fetchCaptcha())
        } catch (e: Throwable) {
            session.close()
            throw e
        }
    }
}
