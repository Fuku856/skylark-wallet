package app.skylark.wallet

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.diagnostics.DebugLog
import app.skylark.wallet.notification.ExpiryNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class SkylarkApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        logUncaughtExceptions()
        DebugLog.log("app", "start version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT}")
        observeDebugLogSetting()
        ExpiryNotifier.createChannel(this)
        ExpiryNotifier.schedule(this)
    }

    /** 裏設定に合わせてデバッグログを切り替える。設定の変更もここでまとめて記録する。 */
    private fun observeDebugLogSetting() {
        appScope.launch {
            settingsRepository.settings
                .distinctUntilChanged()
                .catch { e ->
                    // ログのために設定の読み込み失敗でアプリを落とさない
                    Log.w(DebugLog.TAG, "failed to read debug log setting", e)
                    DebugLog.setEnabled(false)
                }
                .collect { settings ->
                    DebugLog.setEnabled(settings.debugLog)
                    DebugLog.log("settings", settings.toString())
                }
        }
    }

    private fun logUncaughtExceptions() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DebugLog.error("app", "uncaught exception on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }
}
