package app.skylark.wallet.domain.model

/** 券のメモ（1行の任意テキスト）の整形。 */
object MemoNormalizer {

    const val MAX_LENGTH = 40

    private val CONTROL = Regex("\\p{Cntrl}|[\\u0085\\u2028\\u2029]")
    private val SPACES = Regex("\\s+")

    /**
     * 入力中のテキストを整える。貼り付けやキーボードの候補で入った改行・制御文字を空白にし、長さを切る。
     * 入力途中の空白は残す（単語の区切りを打てなくならないように）。
     * 絵文字などのサロゲートペアを途中で切った場合は、残った上位サロゲートを落とす。
     */
    fun sanitizeInput(raw: String): String {
        val cut = raw.replace(CONTROL, " ").take(MAX_LENGTH)
        return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
    }

    /** 保存する値。[sanitizeInput] に加え、連続した空白をまとめて前後の空白を取り除く。 */
    fun normalize(raw: String): String = sanitizeInput(raw).replace(SPACES, " ").trim()
}
