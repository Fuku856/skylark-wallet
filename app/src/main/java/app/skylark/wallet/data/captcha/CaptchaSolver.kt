package app.skylark.wallet.data.captcha

/**
 * CAPTCHA 画像を解析して数字文字列を解くソルバーのインターフェース。
 */
interface CaptchaSolver {
    suspend fun solve(imageBytes: ByteArray): SolveResult

    sealed interface SolveResult {
        data class Success(val digits: String) : SolveResult
        data class Failure(val reason: String? = null) : SolveResult
    }
}
