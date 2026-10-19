package com.example.skylarkwallet.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 生体認証または端末の PIN・パターン（API 28 以降で使える組み合わせ）。 */
const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** アプリのロック解除・ロック有効化で使う認証ダイアログ。 */
object BiometricAuth {

    /** 認証の手段が無くなったことを示すエラー（キャンセルや試行回数超過は含まない）。 */
    val UNAVAILABLE_ERRORS = setOf(
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    )

    /** 端末で認証できるか（画面ロックや生体情報が登録されているか）。 */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorCode)
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
