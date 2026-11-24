package app.skylark.wallet.ui.common

import android.annotation.SuppressLint
import android.webkit.WebView

/**
 * WebView を安全側の設定にする。ファイル・コンテンツへのアクセス、DOM ストレージ、新しいウィンドウ、
 * 位置情報、フォームの保存は無効にする。JavaScript は画面ごとに [javaScriptEnabled] で決める。
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applySafeSettings(javaScriptEnabled: Boolean) {
    settings.apply {
        this.javaScriptEnabled = javaScriptEnabled
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        saveFormData = false
    }
}

/**
 * 照会（サイトの入力画面）用の設定。reCAPTCHA が動くよう JavaScript と DOM ストレージだけを有効にし、
 * それ以外は [applySafeSettings] と同じく無効にする。User-Agent は変えない。
 *
 * サイトは `viewport width=1024` を指定しているが、そのまま縮小表示すると reCAPTCHA のチェックボックスが
 * 小さくなりすぎるため、端末の幅で描く（はみ出す部分は横スクロール・ピンチズームで見られる）。
 */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.applyLookupSettings() {
    applySafeSettings(javaScriptEnabled = true)
    settings.apply {
        domStorageEnabled = true
        useWideViewPort = false
        loadWithOverviewMode = false
        builtInZoomControls = true
        displayZoomControls = false
    }
}
