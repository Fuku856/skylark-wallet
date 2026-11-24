package app.skylark.wallet.ui.lookup

import java.net.URI

/** 照会の WebView で開いてよいページ。サイトのページ以外へは遷移させず、JS も注入しない。 */
object LookupHost {

    const val HOST = "www.skylarkyutaiken.com"
    const val INPUT_URL = "https://$HOST/input"

    /** `https` かつサイトのホストなら true。ポート指定やユーザー情報付きの URL は認めない。 */
    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            uri.port == -1 &&
            uri.rawUserInfo == null
    }
}
