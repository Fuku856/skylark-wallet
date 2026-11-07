package app.skylark.wallet.data.captcha

/**
 * CAPTCHA 画像を解析して数字文字列を解くソルバーのインターフェース。
 */
interface CaptchaSolver {
    suspend fun solve(imageBytes: ByteArray): SolveResult

    sealed interface SolveResult {
        data class Success(val digits: String) : SolveResult

        /** [detail] は例外のクラス名まで。OCR の認識結果は入れない（診断ログに出すため）。 */
        data class Failure(val kind: FailureKind, val detail: String? = null) : SolveResult
    }

    enum class FailureKind {
        /** 画像を読み込めなかった。 */
        ImageDecode,

        /** 認識はできたが、4〜5桁の数字にならなかった。 */
        NotDigits,

        /** 認識エンジンが例外を出した。 */
        Engine,
    }
}
