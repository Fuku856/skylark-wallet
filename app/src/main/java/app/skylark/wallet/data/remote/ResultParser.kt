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
        /** サイトがご優待番号の取り扱いを一時停止している（2026-11 の情報流出の確認手続きなど）。 */
        data class Suspended(val message: String) : Outcome
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
    const val SELECTOR_INPUT_FORM = "form[name=codeform]"

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
        if (isInputForm(doc)) {
            val messages = doc.select(SELECTOR_ERROR).map { it.text().trim() }.filter { it.isNotEmpty() }
            // 取り扱いの一時停止の案内は「優待」を含むが番号の誤りではないので、先に分ける
            val suspended = messages.filter { it.isSuspension() }
            if (suspended.isNotEmpty()) return Outcome.Suspended(suspended.joinToString("\n"))
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

    /** 入力画面（ご優待番号のフォーム）か。2026-11 以降の reCAPTCHA 版と、それ以前の画像 CAPTCHA 版の両方を見る。 */
    fun isInputForm(doc: Document): Boolean =
        doc.selectFirst(SELECTOR_INPUT_FORM) != null ||
            doc.selectFirst(".g-recaptcha, input[name=g-recaptcha-response]") != null ||
            doc.selectFirst("input[name=captchakey]") != null

    /**
     * 結果画面の各項目がセレクタで取れたか（`selector`）、本文の正規表現で取れたか（`text`）、取れなかったか（`none`）。
     * セレクタは推定なので、実物で当たっているかをデバッグログで確かめるために使う。値そのものは含めない。
     */
    fun selectorReport(html: String): String {
        val doc = Jsoup.parse(html)
        fun hit(selector: String) = if (doc.selectFirst(selector) != null) "selector" else "text"
        return listOf(
            "balance" to hit(SELECTOR_BALANCE),
            "barcode" to hit(SELECTOR_BARCODE_NUMBER),
            "barcodeImage" to if (doc.selectFirst(SELECTOR_BARCODE_IMAGE) != null) "selector" else "none",
            "expiry" to hit(SELECTOR_EXPIRY),
            "checkedAt" to hit(SELECTOR_CHECKED_AT),
        ).joinToString(" ") { (k, v) -> "$k=$v" }
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

    /**
     * 取り扱いの一時停止の案内か。2026-11-25 に実機で確認した文言は
     * 「現在、システム上の確認手続きのため、ご入力いただいた番号のお取り扱いを一時停止しております。…」。
     */
    private fun String.isSuspension(): Boolean = contains("一時停止")

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
