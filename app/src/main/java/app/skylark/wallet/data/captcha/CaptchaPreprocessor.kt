package app.skylark.wallet.data.captcha

import java.nio.FloatBuffer

object CaptchaPreprocessor {

    /** サイトの画像の番号は4〜5桁の半角数字。 */
    val CAPTCHA_PATTERN = Regex("^\\d{4,5}$")

    /** ddddocr.onnx の入力テンソルが要求する画像の高さ（px）。 */
    const val TARGET_HEIGHT = 64

    /** アスペクト比を保ったまま高さ [TARGET_HEIGHT] にリサイズするときの幅（px）を計算する。 */
    fun targetWidth(srcWidth: Int, srcHeight: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0) return 1
        return (srcWidth.toFloat() * (TARGET_HEIGHT.toFloat() / srcHeight)).toInt().coerceAtLeast(1)
    }

    /**
     * ARGB ピクセル配列を白背景合成のグレースケール（0.0f〜1.0f）に変換し、
     * ONNX 入力用の [FloatBuffer] として返す。
     */
    fun toGrayscaleFloatBuffer(pixels: IntArray): FloatBuffer {
        val buffer = FloatBuffer.allocate(pixels.size)
        for (pixel in pixels) {
            buffer.put(luminanceOnWhite(pixel) / 255.0f)
        }
        buffer.flip()
        return buffer
    }

    /**
     * 白い背景に重ねたときの輝度（0〜255）。透過 PNG の透明な部分を黒（文字）と見なさないよう、アルファで白と合成する。
     */
    fun luminanceOnWhite(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // 整数による高速輝度計算: Y = (77*R + 150*G + 29*B) >> 8
        val gray = (77 * r + 150 * g + 29 * b) shr 8
        return (gray * a + 255 * (255 - a)) / 255
    }

    /**
     * ONNX モデルの出力ロジットから、Blank（インデックス 0）と数字（'0'〜'9'）のインデックスのみを比較して
     * Restricted CTC Greedy Decode を行い、数字文字列を復元する。
     */
    fun decodeDigitsCtc(
        outBuffer: FloatBuffer,
        seqLen: Int,
        numClasses: Int,
        digitIndices: IntArray,
        charset: List<String>,
    ): String {
        val sb = StringBuilder()
        var prevIndex = -1

        for (t in 0 until seqLen) {
            val offset = t * numClasses

            // Blank トークン (index 0)
            var maxVal = outBuffer.get(offset)
            var maxIndex = 0

            // 数字トークン ('0'〜'9') のみと比較
            for (dIdx in digitIndices) {
                val v = outBuffer.get(offset + dIdx)
                if (v > maxVal) {
                    maxVal = v
                    maxIndex = dIdx
                }
            }

            if (maxIndex != prevIndex && maxIndex != 0) {
                if (maxIndex in charset.indices) {
                    sb.append(charset[maxIndex])
                }
            }
            prevIndex = maxIndex
        }

        return sb.toString()
    }

    /**
     * OCR の認識テキストに対して誤読補正マッピングを適用し、4〜5桁の数字文字列を抽出する。
     */
    fun normalizeText(raw: String): String? {
        val sb = StringBuilder()
        for (ch in raw) {
            when {
                ch.isDigit() -> sb.append(ch)
                // 形状が酷似した英字・記号を数字に補正
                ch in "OoDQCc" -> sb.append('0')
                ch in "lIi|!j" -> sb.append('1')
                ch in "Zz" -> sb.append('2')
                ch in "Ee" -> sb.append('3')
                ch in "Ah" -> sb.append('4')
                ch in "Ss" -> sb.append('5')
                ch in "bG" -> sb.append('6')
                ch in "Tt" -> sb.append('7')
                ch == 'B' -> sb.append('8')
                ch in "gq" -> sb.append('9')
            }
        }
        val digits = sb.toString()
        return if (CAPTCHA_PATTERN.matches(digits)) digits else null
    }
}
