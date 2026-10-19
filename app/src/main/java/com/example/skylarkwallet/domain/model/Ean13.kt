package com.example.skylarkwallet.domain.model

/** JAN（EAN-13）コードのチェックディジット検証。 */
object Ean13 {

    fun isValid(code: String): Boolean {
        if (code.length != 13 || code.any { it !in '0'..'9' }) return false
        return checkDigit(code.substring(0, 12)) == code[12] - '0'
    }

    /** 先頭12桁からチェックディジットを計算する。 */
    fun checkDigit(first12: String): Int {
        require(first12.length == 12 && first12.all { it in '0'..'9' })
        val sum = first12.mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }
}
