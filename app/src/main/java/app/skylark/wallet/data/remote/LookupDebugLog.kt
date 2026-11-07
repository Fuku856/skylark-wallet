package app.skylark.wallet.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 照会の診断ログ。裏設定で ON にしたときだけ logcat（タグ [TAG]）に出す。
 *
 * ご優待番号・バーコード番号・画像の番号・OCR の認識結果・Cookie の値・HTML は渡さないこと。
 * 念のため、長い数字列はここでも伏せる。
 */
object LookupDebugLog {
    const val TAG = "SkylarkLookup"
    private val LONG_DIGITS = Regex("\\d{9,}")

    @Volatile
    var enabled = false

    fun log(message: String) {
        if (enabled) Log.i(TAG, sanitize(message))
    }

    fun sanitize(message: String): String = message.replace(LONG_DIGITS, "***")
}

/**
 * 通信の結果を診断ログに出す。リダイレクトも見えるよう、ネットワークインターセプタとして使う。
 * クエリには番号が入ることがあるので、パスだけを出す。Cookie は名前だけを出す。
 */
class LookupLogInterceptor(
    private val log: (String) -> Unit = LookupDebugLog::log,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val label = "${request.method} ${request.url.encodedPath}"
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            log("$label -> ${e.javaClass.simpleName}")
            throw e
        }
        val type = response.body.contentType()?.let { "${it.type}/${it.subtype}" } ?: "-"
        val cookies = response.headers("Set-Cookie").map { it.substringBefore('=') }
        val location = response.header("Location")?.substringBefore('?')
        log(
            buildString {
                append("$label -> ${response.code} $type cookies=$cookies")
                if (location != null) append(" location=$location")
            },
        )
        return response
    }
}
