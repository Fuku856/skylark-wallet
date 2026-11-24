package app.skylark.wallet.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.Base64

/**
 * 結果 HTML のバーコード画像を取得し、`data:` URI に置き換える。
 * 画像 URL がセッション依存でも後から（オフラインでも）表示できるよう、照会した WebView の Cookie を付けて取得する。
 * 取得できない画像はそのまま残す。
 */
class BarcodeImageInliner(private val client: OkHttpClient) {

    /** [cookie] は WebView の `CookieManager.getCookie(pageUrl)` の値（無ければ null）。 */
    suspend fun inline(html: String, pageUrl: HttpUrl, cookie: String?): String = withContext(Dispatchers.IO) {
        val doc = Jsoup.parse(html, pageUrl.toString())
        val images = doc.select(ResultParser.SELECTOR_BARCODE_IMAGE)
        if (images.isEmpty()) return@withContext html
        for (img in images) {
            val src = img.attr("src")
            if (src.isBlank() || src.startsWith("data:")) continue
            val url = pageUrl.resolve(src) ?: continue
            // サイトの Cookie を他のホストへ送らない
            if (url.host != pageUrl.host || url.scheme != pageUrl.scheme) continue
            val dataUri = runCatching { fetchAsDataUri(url, pageUrl, cookie) }.getOrNull() ?: continue
            img.attr("src", dataUri)
        }
        doc.outerHtml()
    }

    private fun fetchAsDataUri(url: HttpUrl, referer: HttpUrl, cookie: String?): String? {
        val request = Request.Builder().url(url).get()
            .header("Referer", referer.toString())
            .header("Accept", "image/*")
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val type = response.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "image/png"
            if (!type.startsWith("image/")) return null
            "data:$type;base64," + Base64.getEncoder().encodeToString(response.body.bytes())
        }
    }
}
