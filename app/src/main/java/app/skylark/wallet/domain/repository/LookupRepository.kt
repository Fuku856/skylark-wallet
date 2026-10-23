package app.skylark.wallet.domain.repository

import app.skylark.wallet.domain.model.LookupOutcome
import java.io.Closeable

/** サイトへの照会。1枚の券につき1つの [LookupSession] を使い、終わったら閉じる。 */
interface LookupRepository {
    /** 新しいセッション（Cookie）で入力画面を開く。 */
    suspend fun openSession(): LookupSession
}

interface LookupSession : Closeable {
    /** 同じセッションで CAPTCHA 画像を取得する（画像交換も同じ）。 */
    suspend fun fetchCaptcha(): ByteArray

    suspend fun submit(code9: String, captcha: String, termsAgreed: Boolean): LookupOutcome
}
