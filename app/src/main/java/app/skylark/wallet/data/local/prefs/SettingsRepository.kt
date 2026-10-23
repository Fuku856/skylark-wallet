package app.skylark.wallet.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.skylark.wallet.domain.model.AppSettings
import app.skylark.wallet.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val TERMS_AGREED = booleanPreferencesKey("terms_agreed")
        val TERMS_PROMPTED = booleanPreferencesKey("terms_prompted")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")
        val EXPIRY_NOTIFICATION = booleanPreferencesKey("expiry_notification")
        val NOTIFIED_KEYS = stringSetPreferencesKey("notified_keys")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            termsAgreed = prefs[Keys.TERMS_AGREED] ?: false,
            termsPrompted = prefs[Keys.TERMS_PROMPTED] ?: false,
            biometricLock = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            autoBrightness = prefs[Keys.AUTO_BRIGHTNESS] ?: true,
            expiryNotification = prefs[Keys.EXPIRY_NOTIFICATION] ?: false,
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTermsAgreed(agreed: Boolean) = context.dataStore.edit {
        it[Keys.TERMS_AGREED] = agreed
        it[Keys.TERMS_PROMPTED] = true
    }

    suspend fun setBiometricLock(enabled: Boolean) = context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }

    suspend fun setAutoBrightness(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_BRIGHTNESS] = enabled }

    suspend fun setExpiryNotification(enabled: Boolean) =
        context.dataStore.edit { it[Keys.EXPIRY_NOTIFICATION] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    /** 期限通知を送ったかどうかの記録（同じ通知を重複して出さないため）。 */
    suspend fun notifiedKeys(): Set<String> = context.dataStore.data.first()[Keys.NOTIFIED_KEYS].orEmpty()

    suspend fun setNotifiedKeys(keys: Set<String>) = context.dataStore.edit { it[Keys.NOTIFIED_KEYS] = keys }
}
