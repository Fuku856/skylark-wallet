package app.skylark.wallet.data.remote

import app.skylark.wallet.diagnostics.DebugLog
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 通信の結果をデバッグログに出す。リダイレクトも見えるよう、ネットワークインターセプタとして使う。
 * クエリには番号が入ることがあるので、パスだけを出す。Cookie は名前だけを出す。
 */
class LookupLogInterceptor(
    private val log: (String) -> Unit = { DebugLog.log("net", it) },
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
