package app.skylark.wallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.domain.model.AppSettings
import app.skylark.wallet.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setTermsAgreed(agreed: Boolean) = launch { repository.setTermsAgreed(agreed) }
    fun setBiometricLock(enabled: Boolean) = launch { repository.setBiometricLock(enabled) }
    fun setAutoBrightness(enabled: Boolean) = launch { repository.setAutoBrightness(enabled) }
    fun setExpiryNotification(enabled: Boolean) = launch { repository.setExpiryNotification(enabled) }
    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
