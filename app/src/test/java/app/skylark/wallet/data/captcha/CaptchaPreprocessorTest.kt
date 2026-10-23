package app.skylark.wallet.data.captcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptchaPreprocessorTest {

    @Test
    fun normalizeText_pureDigits_returnsSame() {
        assertEquals("0153", CaptchaPreprocessor.normalizeText("0153"))
        assertEquals("30341", CaptchaPreprocessor.normalizeText("30341"))
        assertEquals("25936", CaptchaPreprocessor.normalizeText("25936"))
    }

    @Test
    fun normalizeText_misreadCharacters_convertedToDigits() {
        // 'O' -> '0', 'l' -> '1', 'S' -> '5'
        assertEquals("0153", CaptchaPreprocessor.normalizeText("Ol53"))
        // 'D' -> '0', 'I' -> '1'
        assertEquals("0153", CaptchaPreprocessor.normalizeText("D1S3"))
        // 'Z' -> '2', 's' -> '5', 'B' -> '8'
        assertEquals("2583", CaptchaPreprocessor.normalizeText("ZsB3"))
        // 'e' -> '3', 'b' -> '6', 'T' -> '7', 'g' -> '9'
        assertEquals("3679", CaptchaPreprocessor.normalizeText("ebTg"))
    }

    @Test
    fun normalizeText_withSpacesAndNoise_extractsDigits() {
        assertEquals("0153", CaptchaPreprocessor.normalizeText(" 0 1 5 3 \n"))
        assertEquals("30341", CaptchaPreprocessor.normalizeText(" 3 0 3 4 1 "))
    }

    @Test
    fun normalizeText_tooShortOrTooLong_returnsNull() {
        assertNull(CaptchaPreprocessor.normalizeText("123")) // 3桁は不適合
        assertNull(CaptchaPreprocessor.normalizeText("123456")) // 6桁は不適合
        assertNull(CaptchaPreprocessor.normalizeText("")) // 空文字
    }
}
