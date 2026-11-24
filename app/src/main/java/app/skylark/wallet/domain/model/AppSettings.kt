package app.skylark.wallet.domain.model

data class AppSettings(
    /** サイトの利用規約に同意済みか。同意済みのときだけ照会でき、入力画面の同意欄にチェックを入れる。 */
    val termsAgreed: Boolean = false,
    /** 初回の規約同意画面を表示済みか。 */
    val termsPrompted: Boolean = false,
    val biometricLock: Boolean = false,
    val autoBrightness: Boolean = true,
    val expiryNotification: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 照会の診断ログを logcat に出すか。 */
    val debugLog: Boolean = false,
)

/** 画面の明暗。SYSTEM は端末の設定に従う。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
