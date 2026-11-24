package app.skylark.wallet.domain.model

/** 1回の照会（送信後の結果ページ）の結果。 */
sealed interface LookupOutcome {
    /** 照会成功。[html] は WebView 表示用に画像を埋め込んだ結果 HTML。 */
    data class Success(val result: VoucherResult, val html: String) : LookupOutcome

    /** 認証（reCAPTCHA）が通らず入力画面が再表示された。入力画面を開き直して、もう一度チェックしてもらう。 */
    data class CaptchaMismatch(val message: String?) : LookupOutcome

    /** ご優待番号が無効だった。 */
    data class InvalidCode(val message: String?) : LookupOutcome

    /** サイトがメンテナンス中。 */
    data object Maintenance : LookupOutcome

    /** 結果画面を解析できなかった（サイトの仕様変更の可能性）。 */
    data object ParseFailure : LookupOutcome
}
