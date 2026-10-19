package com.example.skylarkwallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    private var unlocked by mutableStateOf(false)
    private var lockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkylarkTheme {
                val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
                val current = settings
                lockEnabled = current?.biometricLock == true
                when {
                    current == null -> Box(Modifier.fillMaxSize())
                    current.biometricLock && !unlocked -> LockScreen(onUnlock = ::authenticate)
                    else -> SkylarkRoot(settings = current, refreshViewModel = refreshViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) {
            unlocked = false
            refreshViewModel.cancel()
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
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
}
