package app.skylark.wallet.data.captcha

/**
 * CAPTCHA 画像を解析して数字文字列を解くソルバーのインターフェース。
 */
interface CaptchaSolver {
    suspend fun solve(imageBytes: ByteArray): SolveResult

    sealed interface SolveResult {
        /** OCR で読み取った生のテキスト（読み取り失敗や例外時は null）。 */
        val rawText: String?

        data class Success(
            val digits: String,
            override val rawText: String? = digits,
        ) : SolveResult

        /** [detail] は例外のクラス名など。不具合調査のため [rawText] に OCR の認識結果を入れる。 */
        data class Failure(
            val kind: FailureKind,
            val detail: String? = null,
            override val rawText: String? = null,
        ) : SolveResult
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
