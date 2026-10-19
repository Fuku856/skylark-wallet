package com.example.skylarkwallet.ui.webview

import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.R
import com.example.skylarkwallet.ui.common.Format
import com.example.skylarkwallet.ui.common.SecureWindow
import com.example.skylarkwallet.ui.common.applySafeSettings

private const val BASE_URL = "https://www.skylarkyutaiken.com/"

/** 照会で保存した結果 HTML（公式画面）を表示する。 */
@Composable
fun OfficialWebViewScreen(
    onBack: () -> Unit,
    viewModel: OfficialViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.official_title)) },
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
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                OfficialViewModel.UiState.Loading -> LoadingIndicator()
                OfficialViewModel.UiState.Missing -> Text(stringResource(R.string.official_missing))
                is OfficialViewModel.UiState.Loaded -> Column(Modifier.fillMaxSize()) {
                    s.checkedAt?.let {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.official_snapshot, Format.dateTime(it)),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    SnapshotWebView(s.html, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun SnapshotWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // 結果画面の時計表示などに JavaScript を使う可能性があるため有効にする。ページ遷移はすべて止める
                applySafeSettings(javaScriptEnabled = true)
                settings.apply {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.destroy()
        },
    )
}
