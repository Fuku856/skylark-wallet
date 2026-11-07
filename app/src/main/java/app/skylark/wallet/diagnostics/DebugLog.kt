package app.skylark.wallet.diagnostics

import android.util.Log

/**
 * アプリ全体のデバッグログ。裏設定で ON にしたときだけ logcat（タグ [TAG]）に出す。
 *
 * ご優待番号・バーコード番号・画像の番号・OCR の認識結果・Cookie の値・HTML・メモ・バックアップの
 * パスワードは渡さないこと。念のため、長い数字列はここでも伏せる。
 */
object DebugLog {
    const val TAG = "SkylarkWallet"
    private val LONG_DIGITS = Regex("\\d{9,}")

    @Volatile
    var enabled = false

    /** [area] はログの分類（lookup / net / voucher など）。 */
    fun log(area: String, message: String) {
        if (enabled) Log.i(TAG, "[$area] ${sanitize(message)}")
    }

    fun error(area: String, message: String, error: Throwable) {
        if (enabled) Log.e(TAG, "[$area] ${sanitize(message)}\n${sanitize(Log.getStackTraceString(error))}")
    }

    fun sanitize(message: String): String = message.replace(LONG_DIGITS, "***")
}
