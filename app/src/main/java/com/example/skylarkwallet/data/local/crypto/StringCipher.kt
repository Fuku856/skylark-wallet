package com.example.skylarkwallet.data.local.crypto

/**
 * 文字列を暗号化・復号する。
 * [associatedData] には暗号文を保存するレコードの ID を渡し、別レコードへの暗号文の入れ替えを検知する。
 */
interface StringCipher {
    fun encrypt(plainText: String, associatedData: String): String
    fun decrypt(cipherText: String, associatedData: String): String
}
