package app.skylark.wallet.ui.onboarding

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.skylark.wallet.R
import app.skylark.wallet.ui.common.applySafeSettings
import app.skylark.wallet.ui.common.openExternalUrl

private const val SITE_HOST = "www.skylarkyutaiken.com"

/**
 * 入力フォームなどを除き、利用規約の見出しと本文だけをスマホ幅で表示する。
 * 規約が見つからない（サイトの構造が変わった）ときは何もせず、ページ全体をそのまま表示する。
 */
private const val SHOW_TERMS_ONLY_SCRIPT = """
(function () {
  var anchor = document.querySelector('a[name="riyoukiyaku"]');
  if (!anchor) return;
  var heading = anchor.nextElementSibling;
  var body = heading && heading.nextElementSibling;
  if (!heading || !body) return;
  var viewport = document.querySelector('meta[name="viewport"]');
  if (!viewport) {
    viewport = document.createElement('meta');
    viewport.name = 'viewport';
    document.head.appendChild(viewport);
  }
  viewport.content = 'width=device-width, initial-scale=1';
  body.style.maxHeight = 'none';
  body.style.overflow = 'visible';
  var wrapper = document.createElement('div');
  wrapper.style.cssText = 'padding:16px;font-size:15px;line-height:1.7;word-break:break-all;';
  heading.style.cssText = 'font-size:18px;margin:0 0 12px;';
  wrapper.appendChild(heading);
  wrapper.appendChild(body);
  document.body.innerHTML = '';
  document.body.style.cssText = 'margin:0;min-width:0;width:auto;';
  document.body.appendChild(wrapper);
  window.scrollTo(0, 0);
})();
"""

/** サイトの利用規約をアプリ内で表示する。サイト外へのリンクは外部ブラウザで開く。 */
@Composable
fun TermsWebViewScreen(onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.terms_web_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TermsWebView(onLoadingChange = { loading = it }, modifier = Modifier.fillMaxSize())
            if (loading) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun TermsWebView(onLoadingChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // ページ内の折りたたみ表示などに JavaScript を使うため有効にする
                applySafeSettings(javaScriptEnabled = true)
                settings.apply {
                    // サイトは PC 向けの固定幅レイアウトなので、画面幅に収めて表示する
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme == "https" && url.host == SITE_HOST) return false
                        openExternalUrl(view.context, url)
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onLoadingChange(true)

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(SHOW_TERMS_ONLY_SCRIPT, null)
                        onLoadingChange(false)
                    }
                }
                loadUrl(TERMS_URL)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
