package app.skylark.wallet.domain.model

/** 1回の POST（照会）の結果。 */
sealed interface LookupOutcome {
    /** 照会成功。[html] は WebView 表示用に画像を埋め込んだ結果 HTML。 */
    data class Success(val result: VoucherResult, val html: String) : LookupOutcome

    /** 画像の番号が一致しなかった。同じセッションで再入力できる。 */
    data class CaptchaMismatch(val message: String?) : LookupOutcome

    /** ご優待番号が無効だった。 */
    data class InvalidCode(val message: String?) : LookupOutcome

    /** サイトがメンテナンス中。 */
    data object Maintenance : LookupOutcome

    /** 結果画面を解析できなかった（サイトの仕様変更の可能性）。 */
    data object ParseFailure : LookupOutcome
}
