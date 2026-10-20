package app.skylark.wallet.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider

/** 生体認証または端末の PIN・パターン（API 28 以降で使える組み合わせ）。 */
const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** アプリのロック解除・ロック有効化・ご優待番号の表示・バックアップ作成で使う認証ダイアログ。 */
object BiometricAuth {

    /** 認証の手段が無くなったことを示すエラー（キャンセルや試行回数超過は含まない）。 */
    val UNAVAILABLE_ERRORS = setOf(
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    )

    /** 利用者が自分で閉じたことを示すエラー（知らせる必要がない）。 */
    val CANCEL_ERRORS = setOf(
        BiometricPrompt.ERROR_CANCELED,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    )

    /** 端末で認証できるか（画面ロックや生体情報が登録されているか）。 */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, message: CharSequence) -> Unit = { _, _ -> },
    ) {
        val lock = ViewModelProvider(activity)[LockViewModel::class.java]
        lock.authenticating = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lock.authenticating = false
                    // 本人と確かめられたので、認証の途中でかかった起動時のロックも解除する
                    lock.unlocked = true
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lock.authenticating = false
                    onError(errorCode, errString)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
