package app.skylark.wallet.ui.lookup

import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.skylark.wallet.data.remote.ResultParser
import app.skylark.wallet.diagnostics.DebugLog
import app.skylark.wallet.ui.common.applyLookupSettings
import org.jsoup.Jsoup

/** WebView で起きたことを照会の状態（[RefreshViewModel]）へ伝える。 */
interface LookupPageListener {
    fun onInputReady()
    fun onSubmitting()
    fun onResultPage(html: String, url: String, cookie: String?)
    fun onPageProblem(problem: RefreshViewModel.PageProblem)
}

/**
 * サイトの入力画面を開き、[loadKey] が変わるたびに開き直す。照会が終わって画面から外れたら、
 * Cookie と DOM ストレージをすべて消す（一括更新の途中では消さない）。
 */
@Composable
fun LookupWebView(
    code9: String,
    agree: Boolean,
    loadKey: Int,
    listener: LookupPageListener,
    modifier: Modifier = Modifier,
) {
    val currentListener by rememberUpdatedState(listener)
    var driver by remember { mutableStateOf<LookupPageDriver?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                applyLookupSettings()
                // reCAPTCHA は google.com の iframe で動くため、この WebView だけサードパーティ Cookie を受け付ける
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                val pageDriver = LookupPageDriver(
                    this,
                    object : LookupPageListener {
                        override fun onInputReady() = currentListener.onInputReady()
                        override fun onSubmitting() = currentListener.onSubmitting()
                        override fun onResultPage(html: String, url: String, cookie: String?) =
                            currentListener.onResultPage(html, url, cookie)
                        override fun onPageProblem(problem: RefreshViewModel.PageProblem) =
                            currentListener.onPageProblem(problem)
                    },
                )
                webViewClient = pageDriver.client
                driver = pageDriver
            }
        },
        onRelease = { webView ->
            driver?.release()
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.destroy()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        },
    )

    LaunchedEffect(driver, loadKey) {
        driver?.load(code9, agree)
    }
}

/**
 * 入力画面での自動操作。サイトのページ（[LookupHost.isAllowed]）でだけ JavaScript を実行する。
 *
 * 1. 入力画面を読み込んだら、メンテナンスでないか・入力フォームがあるかを確かめ、番号と規約の同意を入れる
 * 2. reCAPTCHA のトークンが入るまで [POLL_MILLIS] ごとに確かめる（チェックは人が行う）
 * 3. トークンが入ったら送信ボタンを押し、次に読み込んだページを結果として渡す
 *
 * JS ブリッジ（addJavascriptInterface）は reCAPTCHA の iframe にも公開されてしまうため使わず、
 * evaluateJavascript の戻り値だけでやり取りする。
 */
class LookupPageDriver(
    private val webView: WebView,
    private val listener: LookupPageListener,
) {
    private val handler = Handler(Looper.getMainLooper())

    /** 読み込みのたびに増やし、古い読み込みのコールバックを捨てる。 */
    private var generation = 0
    private var code9: String? = null
    private var agree = false
    private var inputHandled = false
    private var submitted = false
    private var resultHandled = false
    private var failed = false

    private val poll = Runnable { checkToken(generation) }

    val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // reCAPTCHA の iframe などサブフレームの遷移は止めない。画面全体はサイト以外へ遷移させない
            if (!request.isForMainFrame) return false
            val allowed = LookupHost.isAllowed(request.url.toString())
            if (!allowed) DebugLog.log("lookup", "blocked navigation host=${request.url.host}")
            return !allowed
        }

        override fun onPageFinished(view: WebView, url: String?) {
            handlePageFinished(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            DebugLog.log("lookup", "load error code=${error.errorCode}")
            fail(RefreshViewModel.PageProblem.Network)
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame) return
            DebugLog.log("lookup", "http error status=${errorResponse.statusCode}")
            if (errorResponse.statusCode == HTTP_SERVICE_UNAVAILABLE) fail(RefreshViewModel.PageProblem.Maintenance)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            DebugLog.log("lookup", "ssl error primary=${error.primaryError}")
            fail(RefreshViewModel.PageProblem.Network)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // WebView はもう使えないので照会を終える（画面から外れたときに破棄される）
            DebugLog.log("lookup", "render process gone crash=${detail.didCrash()}")
            fail(RefreshViewModel.PageProblem.Unexpected)
            return true
        }
    }

    /** 新しく入力画面を開く。 */
    fun load(code9: String, agree: Boolean) {
        generation++
        handler.removeCallbacks(poll)
        this.code9 = code9
        this.agree = agree
        inputHandled = false
        submitted = false
        resultHandled = false
        failed = false
        webView.stopLoading()
        webView.loadUrl(LookupHost.INPUT_URL)
    }

    fun release() {
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    private fun fail(problem: RefreshViewModel.PageProblem) {
        if (failed) return
        failed = true
        generation++
        handler.removeCallbacks(poll)
        listener.onPageProblem(problem)
    }

    private fun handlePageFinished(url: String?) {
        if (failed || url == null || !LookupHost.isAllowed(url)) return
        if (submitted) {
            if (resultHandled) return
            resultHandled = true
        } else {
            if (inputHandled) return
            inputHandled = true
        }
        val gen = generation
        webView.evaluateJavascript(LookupScript.OUTER_HTML) { raw ->
            if (gen != generation) return@evaluateJavascript
            val html = LookupScript.decodeString(raw)
                ?: return@evaluateJavascript fail(RefreshViewModel.PageProblem.Unexpected)
            if (submitted) {
                handler.removeCallbacks(poll)
                listener.onResultPage(html, url, CookieManager.getInstance().getCookie(url))
            } else {
                handleInputPage(html, gen)
            }
        }
    }

    private fun handleInputPage(html: String, gen: Int) {
        val doc = Jsoup.parse(html)
        if (ResultParser.isMaintenance(doc)) return fail(RefreshViewModel.PageProblem.Maintenance)
        if (!ResultParser.isInputForm(doc)) return fail(RefreshViewModel.PageProblem.Unexpected)
        val code = code9 ?: return
        val script = runCatching { LookupScript.fill(code, agree) }.getOrNull()
            ?: return fail(RefreshViewModel.PageProblem.Unexpected)
        webView.evaluateJavascript(script) { raw ->
            if (gen != generation) return@evaluateJavascript
            val result = LookupScript.decodeString(raw)
            if (result != LookupScript.RESULT_OK) {
                DebugLog.log("lookup", "fill result=$result")
                return@evaluateJavascript fail(RefreshViewModel.PageProblem.Unexpected)
            }
            listener.onInputReady()
            handler.postDelayed(poll, POLL_MILLIS)
        }
    }

    private fun checkToken(gen: Int) {
        if (gen != generation || submitted) return
        webView.evaluateJavascript(LookupScript.TOKEN_READY) { raw ->
            if (gen != generation || submitted) return@evaluateJavascript
            if (raw == "true") submit(gen) else handler.postDelayed(poll, POLL_MILLIS)
        }
    }

    private fun submit(gen: Int) {
        submitted = true
        listener.onSubmitting()
        webView.evaluateJavascript(LookupScript.SUBMIT) { raw ->
            if (gen != generation) return@evaluateJavascript
            when (val result = LookupScript.decodeString(raw)) {
                LookupScript.RESULT_OK -> Unit
                LookupScript.RESULT_NO_TOKEN -> {
                    // 送る直前にトークンの期限が切れた。もう一度チェックしてもらう
                    submitted = false
                    listener.onInputReady()
                    handler.postDelayed(poll, POLL_MILLIS)
                }
                else -> {
                    DebugLog.log("lookup", "submit result=$result")
                    fail(RefreshViewModel.PageProblem.Unexpected)
                }
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
