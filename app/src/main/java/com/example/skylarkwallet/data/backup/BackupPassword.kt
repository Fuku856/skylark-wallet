package com.example.skylarkwallet.data.backup

import java.security.SecureRandom
import java.text.Normalizer

/**
 * バックアップファイルを暗号化するパスワード。アプリが生成して利用者にメモしてもらう。
 * Crockford Base32 の20文字（約100bit）なので、ファイルが流出しても総当たりでは解けない。
 */
object BackupPassword {

    const val LENGTH = 20
    private const val GROUP = 4

    /** 読み間違えやすい I・L・O・U を除いた32文字。 */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** `XXXX-XXXX-XXXX-XXXX-XXXX` の形で返す。 */
    fun generate(random: SecureRandom = SecureRandom()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(GROUP)
            .joinToString("-") { it.joinToString("") }

    /**
     * 入力されたパスワードを生成時の形（区切りなし・大文字）にそろえる。
     * 全角・小文字・ハイフンや空白の有無を吸収し、O は 0、I と L は 1 として読む。
     */
    fun normalize(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC)
            .uppercase()
            .filter { it.isLetterOrDigit() }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .joinToString("")

    /** [normalize] 済みの値が生成されうる形か。 */
    fun isWellFormed(normalized: String): Boolean = normalized.length == LENGTH && normalized.all { it in ALPHABET }
}
