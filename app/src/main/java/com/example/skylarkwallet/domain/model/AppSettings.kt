package com.example.skylarkwallet.domain.model

data class AppSettings(
    /** サイトの利用規約に同意済みか。同意済みのときだけ POST に同意フラグを付ける。 */
    val termsAgreed: Boolean = false,
    /** 初回の規約同意画面を表示済みか。 */
    val termsPrompted: Boolean = false,
    val biometricLock: Boolean = false,
    val autoBrightness: Boolean = true,
    val expiryNotification: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** 画面の明暗。SYSTEM は端末の設定に従う。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
