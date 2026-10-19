package com.example.skylarkwallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.ui.SkylarkRoot
import com.example.skylarkwallet.ui.captcha.RefreshViewModel
import com.example.skylarkwallet.ui.lock.LockScreen
import com.example.skylarkwallet.ui.lock.LockViewModel
import com.example.skylarkwallet.ui.settings.LOCK_AUTHENTICATORS
import com.example.skylarkwallet.ui.theme.SkylarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val refreshViewModel: RefreshViewModel by viewModels()

    /** 生体認証ロックを解除済みか。アプリがバックグラウンドに回ったら戻す。 */
    private val lockViewModel: LockViewModel by viewModels()
    private var lockEnabled = false

    /**
     * 端末で認証できるか（画面ロックや生体情報が登録されているか）。認証できない端末でロック画面を出すと
     * 解除も設定変更もできなくなるため、その間はロックしない（設定画面でもロックは無効として表示している）。
     */
    private var canAuthenticate by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkylarkTheme {
                val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
                val current = settings
                lockEnabled = current?.biometricLock == true
                // ロックが無効な間は解除済みとして扱う（設定画面でロックを有効にした直後にロックしないため）
                SideEffect { if (current != null && !current.biometricLock) lockViewModel.unlocked = true }
                when {
                    current == null -> Box(Modifier.fillMaxSize())
                    current.biometricLock && canAuthenticate && !lockViewModel.unlocked ->
                        LockScreen(onUnlock = ::authenticate)
                    else -> SkylarkRoot(settings = current, refreshViewModel = refreshViewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // システム設定で画面ロックを追加・削除して戻ってきた場合に備え、表示のたびに確かめる
        canAuthenticate = BiometricManager.from(this).canAuthenticate(LOCK_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) {
            lockViewModel.unlocked = false
            refreshViewModel.cancel()
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lockViewModel.unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 認証の手段が無くなった場合はロックを外す（キャンセルや試行回数超過ではロックしたまま）
                    if (errorCode in UNAVAILABLE_ERRORS) canAuthenticate = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        val UNAVAILABLE_ERRORS = setOf(
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        )
    }
}
