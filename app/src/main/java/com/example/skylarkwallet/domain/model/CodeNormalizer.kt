package com.example.skylarkwallet.domain.model

import java.text.Normalizer

/** 9桁のご優待番号の正規化と検証。 */
object CodeNormalizer {

    const val CODE_LENGTH = 9

    private val ALNUM_9 = Regex("^[0-9A-Za-z]{9}$")

    /**
     * 全角を半角にし、ハイフン・空白などの英数字以外を取り除く。
     * 大文字・小文字はサイトでの扱いが不明なため変えない。
     */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }

    fun isValid(code: String): Boolean = ALNUM_9.matches(code)

    /** サイトの3つの入力欄に合わせて3文字ずつに分ける。 */
    fun split(code: String): List<String> {
        require(isValid(code)) { "invalid code" }
        return code.chunked(3)
    }

    /** 表示用に3文字ずつハイフンで区切る（例: ABC-123-XYZ）。 */
    fun format(code: String): String = code.chunked(3).joinToString("-")
}
