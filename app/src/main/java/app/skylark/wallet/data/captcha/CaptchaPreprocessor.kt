package app.skylark.wallet.data.captcha

import android.graphics.Bitmap

object CaptchaPreprocessor {

    /** サイトの画像の番号は4〜5桁の半角数字。 */
    val CAPTCHA_PATTERN = Regex("^\\d{4,5}$")

    /**
     * 大津の二値化（Otsu's Thresholding）により、背景パステル色を純白（255）、文字色を純黒（0）に二値化する。
     */
    fun binarize(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. グレースケール輝度配列とヒストグラムを計算
        val grays = IntArray(totalPixels)
        val histogram = IntArray(256)
        for (i in 0 until totalPixels) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // 整数による高速輝度計算: Y = (77*R + 150*G + 29*B) >> 8
            val gray = (77 * r + 150 * g + 29 * b) shr 8
            grays[i] = gray
            histogram[gray]++
        }

        // 2. 大津の閾値アルゴリズムにより最適な二値化閾値を探索
        var sum = 0.0
        for (i in 0 until 256) {
            sum += i.toDouble() * histogram[i]
        }

        var sumB = 0.0
        var wB = 0
        var varMax = 0.0
        var threshold = 128

        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = totalPixels - wB
            if (wF == 0) break

            sumB += t.toDouble() * histogram[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }

        // 3. 閾値より暗い文字部分を黒、明るい背景部分を白に設定
        val resultPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            resultPixels[i] = if (grays[i] < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        return Bitmap.createBitmap(resultPixels, width, height, Bitmap.Config.ARGB_8888)
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
