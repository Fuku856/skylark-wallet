package com.example.skylarkwallet

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.domain.model.ThemeMode
import com.example.skylarkwallet.ui.SkylarkRoot
import com.example.skylarkwallet.ui.captcha.RefreshViewModel
import com.example.skylarkwallet.ui.lock.BiometricAuth
import com.example.skylarkwallet.ui.lock.LockScreen
import com.example.skylarkwallet.ui.lock.LockViewModel
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
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings
            val dark = when (current?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // アプリ内でテーマを切り替えたときも、ステータスバー・ナビゲーションバーのアイコン色を合わせる
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            SkylarkTheme(darkTheme = dark) {
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
        canAuthenticate = BiometricAuth.canAuthenticate(this)
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) {
            lockViewModel.unlocked = false
            refreshViewModel.cancel()
        }
    }

    private fun authenticate() {
        BiometricAuth.authenticate(
            activity = this,
            title = getString(R.string.lock_prompt_title),
            subtitle = getString(R.string.lock_prompt_subtitle),
            onSuccess = { lockViewModel.unlocked = true },
            // 認証の手段が無くなった場合はロックを外す（キャンセルや試行回数超過ではロックしたまま）
            onError = { if (it in BiometricAuth.UNAVAILABLE_ERRORS) canAuthenticate = false },
        )
    }

    private companion object {
        // androidx.activity の enableEdgeToEdge() の既定と同じスクリム
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
