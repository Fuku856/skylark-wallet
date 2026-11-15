package app.skylark.wallet.data.captcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.FloatBuffer

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
    fun luminanceOnWhite_transparentPixel_isWhite() {
        assertEquals(255, CaptchaPreprocessor.luminanceOnWhite(0x00000000))
        assertEquals(255, CaptchaPreprocessor.luminanceOnWhite(0x00FF0000))
    }

    @Test
    fun luminanceOnWhite_opaquePixel_usesColor() {
        assertEquals(0, CaptchaPreprocessor.luminanceOnWhite(0xFF000000.toInt()))
        assertEquals(255, CaptchaPreprocessor.luminanceOnWhite(0xFFFFFFFF.toInt()))
    }

    @Test
    fun luminanceOnWhite_halfTransparentBlack_isGray() {
        assertEquals(127, CaptchaPreprocessor.luminanceOnWhite(0x80000000.toInt()))
    }

    @Test
    fun targetWidth_preservesAspectRatio() {
        // 450x100 -> 288x64
        assertEquals(288, CaptchaPreprocessor.targetWidth(450, 100))
        assertEquals(1, CaptchaPreprocessor.targetWidth(0, 100))
        assertEquals(1, CaptchaPreprocessor.targetWidth(450, 0))
    }

    @Test
    fun toGrayscaleFloatBuffer_normalizesToZeroOneRange() {
        val pixels = intArrayOf(
            0xFF000000.toInt(), // 黒 -> 0.0f
            0xFFFFFFFF.toInt(), // 白 -> 1.0f
            0x00000000, // 透明 -> 白合成で 1.0f
        )
        val buffer = CaptchaPreprocessor.toGrayscaleFloatBuffer(pixels)
        assertEquals(3, buffer.remaining())
        assertEquals(0.0f, buffer.get(0), 1e-5f)
        assertEquals(1.0f, buffer.get(1), 1e-5f)
        assertEquals(1.0f, buffer.get(2), 1e-5f)
    }

    @Test
    fun decodeDigitsCtc_collapsesDuplicatesAndIgnoresNonDigits() {
        // index: 0="", 1="A"(非数字), 2="3", 3="0", 4="4", 5="1"
        val charset = listOf("", "A", "3", "0", "4", "1")
        val digitIndices = intArrayOf(2, 3, 4, 5)
        val numClasses = charset.size

        // 時系列:
        // t=0: "3" (連続1)
        // t=1: "3" (連続2 -> 統合される)
        // t=2: ""  (Blank, ただし非数字 "A" のロジットが最大でも無視されることを確認)
        // t=3: "3" (Blank を挟んだので2文字目の "3" として出力される)
        // t=4: "0"
        // t=5: "4"
        // t=6: "1"
        val rows = arrayOf(
            floatArrayOf(0.1f, 0.2f, 5.0f, 0.0f, 0.0f, 0.0f),
            floatArrayOf(0.1f, 9.0f, 4.0f, 0.0f, 0.0f, 0.0f), // "A"=9.0f は無視され、"3"=4.0f が選ばれる
            floatArrayOf(3.0f, 8.0f, 0.5f, 0.0f, 0.0f, 0.0f), // Blank=3.0f が数字より高いので Blank
            floatArrayOf(0.1f, 0.0f, 6.0f, 0.0f, 0.0f, 0.0f), // 再度 "3"
            floatArrayOf(0.1f, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f), // "0"
            floatArrayOf(0.1f, 0.0f, 0.0f, 0.0f, 5.0f, 0.0f), // "4"
            floatArrayOf(0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 5.0f), // "1"
        )
        val flat = FloatBuffer.allocate(rows.size * numClasses)
        for (row in rows) {
            for (v in row) flat.put(v)
        }
        flat.flip()

        val decoded = CaptchaPreprocessor.decodeDigitsCtc(
            outBuffer = flat,
            seqLen = rows.size,
            numClasses = numClasses,
            digitIndices = digitIndices,
            charset = charset,
        )
        assertEquals("33041", decoded)
    }

    @Test
    fun normalizeText_tooShortOrTooLong_returnsNull() {
        assertNull(CaptchaPreprocessor.normalizeText("123")) // 3桁は不適合
        assertNull(CaptchaPreprocessor.normalizeText("123456")) // 6桁は不適合
        assertNull(CaptchaPreprocessor.normalizeText("")) // 空文字
    }
}
