package com.example.skylarkwallet.ui.onboarding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.example.skylarkwallet.R

private const val SITE_HOST = "www.skylarkyutaiken.com"

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TermsWebView(onLoadingChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.apply {
                    // ページ内の折りたたみ表示などに JavaScript を使うため有効にする。ファイル・コンテンツへのアクセスは無効
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                    saveFormData = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme == "https" && url.host == SITE_HOST) return false
                        try {
                            view.context.startActivity(Intent(Intent.ACTION_VIEW, url))
                        } catch (_: ActivityNotFoundException) {
                            // 開けるアプリが無ければ何もしない
                        }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = onLoadingChange(true)

                    override fun onPageFinished(view: WebView, url: String?) = onLoadingChange(false)
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
