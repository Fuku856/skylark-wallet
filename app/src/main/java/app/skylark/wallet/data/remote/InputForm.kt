package app.skylark.wallet.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** 入力画面のフォーム。name 属性などは実ページから読み取り、見つからなければ既知の値を使う。 */
data class InputForm(
    val pageUrl: HttpUrl,
    val actionUrl: HttpUrl,
    val codeFieldNames: List<String>,
    val captchaFieldName: String,
    val agreeFieldName: String,
    val agreeValue: String,
    val hiddenFields: List<Pair<String, String>>,
    val captchaUrl: HttpUrl,
) {
    companion object {
        // 2026-10 時点の https://www.skylarkyutaiken.com/input の実 DOM
        val DEFAULT_CODE_FIELDS = listOf("code[0]", "code[1]", "code[2]")
        const val DEFAULT_CAPTCHA_FIELD = "captchakey"
        const val DEFAULT_AGREE_FIELD = "agree"
        const val DEFAULT_AGREE_VALUE = "1"
        const val DEFAULT_CAPTCHA_PATH = "/input/captcha"

        /** 入力画面の HTML からフォームを読み取る。フォームがなければ null。 */
        fun parse(html: String, pageUrl: HttpUrl): InputForm? = parse(Jsoup.parse(html, pageUrl.toString()), pageUrl)

        fun parse(doc: Document, pageUrl: HttpUrl): InputForm? {
            val captchaInput = doc.selectFirst("input[name=$DEFAULT_CAPTCHA_FIELD]")
                ?: doc.selectFirst("input[name*=captcha]")
            val form = captchaInput?.closest("form") ?: doc.selectFirst("form[name=codeform]") ?: return null

            val action = form.attr("action").ifBlank { pageUrl.encodedPath }
            val actionUrl = pageUrl.resolve(action) ?: pageUrl

            val codeFields = form.select("input[name^=code]")
                .map { it.attr("name") }
                .filter { it.startsWith("code[") || it.matches(Regex("code\\d")) }
                .distinct()
                .takeIf { it.size == 3 }
                ?: DEFAULT_CODE_FIELDS

            val captchaField = captchaInput?.attr("name")?.ifBlank { null } ?: DEFAULT_CAPTCHA_FIELD

            val agree = form.selectFirst("input[type=checkbox][name=$DEFAULT_AGREE_FIELD]")
                ?: form.selectFirst("input[type=checkbox]")
            val agreeField = agree?.attr("name")?.ifBlank { null } ?: DEFAULT_AGREE_FIELD
            val agreeValue = agree?.attr("value")?.ifBlank { null } ?: DEFAULT_AGREE_VALUE

            val hidden = form.select("input[type=hidden]")
                .filter { it.attr("name").isNotBlank() }
                .map { it.attr("name") to it.attr("value") }

            val captchaSrc = (doc.selectFirst("img#captcha") ?: doc.selectFirst("img[src*=captcha]"))?.attr("src")
            val captchaUrl = captchaSrc?.let { pageUrl.resolve(it) }
                ?: pageUrl.resolve(DEFAULT_CAPTCHA_PATH)
                ?: "${pageUrl.scheme}://${pageUrl.host}$DEFAULT_CAPTCHA_PATH".toHttpUrlOrNull()!!

            return InputForm(
                pageUrl = pageUrl,
                actionUrl = actionUrl,
                codeFieldNames = codeFields,
                captchaFieldName = captchaField,
                agreeFieldName = agreeField,
                agreeValue = agreeValue,
                hiddenFields = hidden,
                captchaUrl = captchaUrl,
            )
        }
    }
}
