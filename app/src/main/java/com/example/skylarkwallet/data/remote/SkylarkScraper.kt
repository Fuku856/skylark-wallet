package com.example.skylarkwallet.data.remote

import com.example.skylarkwallet.domain.model.CodeNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Base64
import kotlin.random.Random

/** サイトのベース URL。テストでは MockWebServer の URL に差し替える。 */
data class SkylarkEndpoint(val baseUrl: HttpUrl) {
    val inputUrl: HttpUrl get() = baseUrl.resolve("/input")!!
}

/** サイトがメンテナンス中で入力画面を開けない。 */
class SiteMaintenanceException : IOException("site maintenance")

/** 入力画面の形式が想定と違う（サイトの仕様変更の可能性）。 */
class UnexpectedPageException(message: String) : IOException(message)

/**
 * 入力画面の取得・CAPTCHA 画像の取得・POST を行う。
 * ユーザーの操作を起点にしたときだけ呼ばれ、バックグラウンドでの照会は行わない。
 */
class SkylarkScraper(
    private val baseClient: OkHttpClient,
    private val endpoint: SkylarkEndpoint,
) {

    fun newSession(): SkylarkSession = SkylarkSession(baseClient)

    /** GET /input でセッション Cookie を受け取り、フォームを読み取る。 */
    suspend fun open(session: SkylarkSession): InputForm = withContext(Dispatchers.IO) {
        val url = endpoint.inputUrl
        val html = session.client.newCall(Request.Builder().url(url).get().build()).execute().use { it.bodyOrThrow() }
        val doc = Jsoup.parse(html, url.toString())
        if (doc.selectFirst(ResultParser.SELECTOR_MAINTENANCE) != null) throw SiteMaintenanceException()
        val form = InputForm.parse(doc, url) ?: throw UnexpectedPageException("input form not found")
        session.form = form
        form
    }

    /** CAPTCHA 画像を取得する。サイトの「画像交換」と同じく、クエリを乱数に差し替えて再取得する。 */
    suspend fun fetchCaptcha(session: SkylarkSession): ByteArray = withContext(Dispatchers.IO) {
        val form = session.form ?: throw IllegalStateException("session not opened")
        val url = form.captchaUrl.newBuilder()
            .query(Random.nextInt(0, 10_000_000).toString())
            .build()
        val request = Request.Builder().url(url).get()
            .header("Referer", form.pageUrl.toString())
            .header("Accept", "image/*")
            .build()
        session.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.bytes()
        }
    }

    /** ご優待番号と CAPTCHA を送信し、返ってきた HTML をそのまま返す。 */
    suspend fun submit(
        session: SkylarkSession,
        code9: String,
        captcha: String,
        termsAgreed: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val form = session.form ?: throw IllegalStateException("session not opened")
        val parts = CodeNormalizer.split(code9)
        val body = FormBody.Builder().apply {
            form.hiddenFields.forEach { (name, value) -> add(name, value) }
            form.codeFieldNames.zip(parts).forEach { (name, value) -> add(name, value) }
            add(form.captchaFieldName, captcha)
            if (termsAgreed) add(form.agreeFieldName, form.agreeValue)
        }.build()
        val request = Request.Builder().url(form.actionUrl).post(body)
            .header("Referer", form.pageUrl.toString())
            .build()
        val html = session.client.newCall(request).execute().use { it.bodyOrThrow() }
        // CAPTCHA 不一致でフォームが再表示されたら、次の送信に備えてフォームを更新する
        InputForm.parse(html, form.actionUrl)?.let { session.form = it }
        html
    }

    /**
     * 結果 HTML のバーコード画像を同じセッションで取得し、`data:` URI に置き換える。
     * 画像 URL がセッション依存でも、後から WebView で表示できるようにするため。取得できない画像はそのまま残す。
     */
    suspend fun inlineBarcodeImages(session: SkylarkSession, html: String, pageUrl: HttpUrl): String =
        withContext(Dispatchers.IO) {
            val doc = Jsoup.parse(html, pageUrl.toString())
            val images = doc.select(ResultParser.SELECTOR_BARCODE_IMAGE)
            if (images.isEmpty()) return@withContext html
            for (img in images) {
                val src = img.attr("src")
                if (src.isBlank() || src.startsWith("data:")) continue
                val url = pageUrl.resolve(src) ?: continue
                val dataUri = runCatching { fetchAsDataUri(session, url, pageUrl) }.getOrNull() ?: continue
                img.attr("src", dataUri)
            }
            doc.outerHtml()
        }

    private fun fetchAsDataUri(session: SkylarkSession, url: HttpUrl, referer: HttpUrl): String? {
        val request = Request.Builder().url(url).get().header("Referer", referer.toString()).build()
        return session.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val type = response.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/png"
            if (!type.startsWith("image/")) return null
            "data:$type;base64," + Base64.getEncoder().encodeToString(response.body.bytes())
        }
    }

    private fun Response.bodyOrThrow(): String {
        if (code == 503) throw SiteMaintenanceException()
        if (!isSuccessful) throw IOException("HTTP $code")
        return body.string()
    }
}
