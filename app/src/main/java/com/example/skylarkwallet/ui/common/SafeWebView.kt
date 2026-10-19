package com.example.skylarkwallet.ui.common

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
