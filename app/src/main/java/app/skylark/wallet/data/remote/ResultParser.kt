package app.skylark.wallet.data.remote

import app.skylark.wallet.domain.model.Ean13
import app.skylark.wallet.domain.model.VoucherResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * POST 後に返る HTML を解析する。
 *
 * セレクタはサイトの `/css/public.css`（`body.inp` 向けスタイル）から推定したもの。
 * 実 DOM と違っていても動くように、本文テキストに対する正規表現をフォールバックとして併用する。
 */
object ResultParser {

    sealed interface Outcome {
        data class Success(val result: VoucherResult) : Outcome
        data class CaptchaMismatch(val message: String?) : Outcome
        data class InvalidCode(val message: String?) : Outcome
        data object Maintenance : Outcome
        data object Unknown : Outcome
    }

    // --- 結果画面のセレクタ（実 DOM を確認したらここを更新する） ---
    const val SELECTOR_BALANCE = "hgroup.ticket p.val"
    const val SELECTOR_CHECKED_AT = "hgroup.ticket p.date"
    const val SELECTOR_BARCODE_NUMBER = ".bcode span"
    const val SELECTOR_BARCODE_IMAGE = ".bcode img"
    const val SELECTOR_EXPIRY = ".limit"
    const val SELECTOR_ERROR = "p.err"
    const val SELECTOR_MAINTENANCE = "#gh.mainte"

    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    private val DIGITS_13 = Regex("(?<!\\d)\\d{13}(?!\\d)")
    private val BALANCE_NEAR_LABEL = Regex("残高[^0-9０-９]{0,20}?([0-9][0-9,]*)\\s*円")
    private val AMOUNT = Regex("([0-9][0-9,]*)\\s*円")
    private val EXPIRY = Regex(
        "(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*(\\d{1,2})\\s*時\\s*(\\d{1,2})\\s*分\\s*(?:迄|まで)",
    )
    private val CHECKED_AT = Regex(
        "(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日\\s*(午前|午後)?\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*時?\\s*点",
    )

    fun isMaintenance(doc: Document): Boolean {
        if (doc.selectFirst(SELECTOR_MAINTENANCE) != null) return true
        val title = doc.title()
        if (title.contains("Maintenance", ignoreCase = true) || title.contains("メンテナンス")) return true
        val text = doc.normalizedText()
        if (text.contains("Site Maintenance", ignoreCase = true) || text.contains("メンテナンス中")) return true
        return false
    }

    fun parse(html: String): Outcome {
        val doc = Jsoup.parse(html)
        if (isMaintenance(doc)) return Outcome.Maintenance

        val text = doc.normalizedText()
        val barcode = findBarcode(doc, text)
        if (barcode != null) {
            val balance = findBalance(doc, text) ?: return Outcome.Unknown
            return Outcome.Success(
                VoucherResult(
                    balance = balance,
                    barcode13 = barcode,
                    expiryAt = findExpiry(doc, text),
                    balanceCheckedAt = findCheckedAt(doc, text),
                ),
            )
        }

        // 入力フォームが再表示された場合はエラー
        val formShown = doc.selectFirst("input[name=${InputForm.DEFAULT_CAPTCHA_FIELD}]") != null ||
            doc.selectFirst("img[src*=captcha]") != null
        if (formShown) {
            val messages = doc.select(SELECTOR_ERROR).map { it.text().trim() }.filter { it.isNotEmpty() }
            // 番号のエラーが1つでもあれば番号の誤り。それ以外（文言が想定外のものを含む）は CAPTCHA の不一致として
            // 扱い、有効な券を「無効」と記録しないようにする
            val codeErrors = messages.filter { it.isCodeError() }
            return if (codeErrors.isNotEmpty()) {
                Outcome.InvalidCode(codeErrors.joinToString("\n"))
            } else {
                Outcome.CaptchaMismatch(messages.joinToString("\n").ifBlank { null })
            }
        }

        if (text.contains("メンテナンス中")) return Outcome.Maintenance
        return Outcome.Unknown
    }

    /** バーコード画像の URL（相対パスのまま）。WebView 用に埋め込む際に使う。 */
    fun barcodeImageSources(doc: Document): List<String> =
        doc.select(SELECTOR_BARCODE_IMAGE).map { it.attr("src") }.filter { it.isNotBlank() }

    private fun findBarcode(doc: Document, text: String): String? {
        doc.select(SELECTOR_BARCODE_NUMBER).forEach { el ->
            val digits = el.text().toHalfWidth().filter { it.isDigit() }
            if (Ean13.isValid(digits)) return digits
        }
        doc.select(SELECTOR_BARCODE_IMAGE).forEach { el ->
            val digits = el.attr("alt").toHalfWidth().filter { it.isDigit() }
            if (Ean13.isValid(digits)) return digits
        }
        return DIGITS_13.findAll(text).map { it.value }.firstOrNull { Ean13.isValid(it) }
    }

    private fun findBalance(doc: Document, text: String): Int? {
        doc.selectFirst(SELECTOR_BALANCE)?.let { el ->
            el.text().toHalfWidth().replace(",", "").let { Regex("\\d+").find(it) }
                ?.value?.toIntOrNull()?.let { return it }
        }
        val match = BALANCE_NEAR_LABEL.find(text) ?: AMOUNT.find(text) ?: return null
        return match.groupValues[1].replace(",", "").toIntOrNull()
    }

    private fun findExpiry(doc: Document, text: String): Long? {
        val source = doc.selectFirst(SELECTOR_EXPIRY)?.text()?.toHalfWidth()?.takeIf { EXPIRY.containsMatchIn(it) }
            ?: text
        return parseExpiry(source)
    }

    private fun findCheckedAt(doc: Document, text: String): Long? {
        val source = doc.selectFirst(SELECTOR_CHECKED_AT)?.text()?.toHalfWidth()
            ?.takeIf { CHECKED_AT.containsMatchIn(it) }
            ?: text
        return parseCheckedAt(source)
    }

    /** `2027 年 03月 10日 23時59分迄` → その分の終わり（23:59:59.999 JST）の Epoch Millis。 */
    fun parseExpiry(text: String): Long? {
        val m = EXPIRY.find(text.toHalfWidth()) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        return runCatching {
            LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), 59, 999_000_000)
                .atZone(TOKYO).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** `2026年 10月 19日 午後2:29時点の残高` → Epoch Millis（JST）。 */
    fun parseCheckedAt(text: String): Long? {
        val m = CHECKED_AT.find(text.toHalfWidth()) ?: return null
        val (y, mo, d, ampm, h, mi) = m.destructured
        var hour = h.toInt()
        when (ampm) {
            "午後" -> if (hour < 12) hour += 12
            "午前" -> if (hour == 12) hour = 0
        }
        return runCatching {
            LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), hour, mi.toInt())
                .atZone(TOKYO).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** ご優待番号についてのエラー文言か（画像の番号についての文言は除く）。実際の文言は未確認。 */
    private fun String.isCodeError(): Boolean =
        contains("優待") && !contains("画像") && !contains("認証")

    private fun Document.normalizedText(): String = body()?.text().orEmpty().toHalfWidth()

    /** 全角数字・コロン・カンマを半角にする。 */
    private fun String.toHalfWidth(): String = buildString(length) {
        for (c in this@toHalfWidth) {
            append(
                when (c) {
                    in '０'..'９' -> '0' + (c - '０')
                    '：' -> ':'
                    '，' -> ','
                    ' ', '　' -> ' '
                    else -> c
                },
            )
        }
    }
}
