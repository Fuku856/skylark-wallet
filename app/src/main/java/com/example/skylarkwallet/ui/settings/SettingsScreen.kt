package com.example.skylarkwallet.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.BuildConfig
import com.example.skylarkwallet.R
import com.example.skylarkwallet.notification.ExpiryNotifier

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setExpiryNotification(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchItem(
                title = stringResource(R.string.settings_biometric),
                summary = stringResource(
                    if (biometricAvailable) R.string.settings_biometric_summary else R.string.settings_biometric_unavailable,
                ),
                checked = s.biometricLock && biometricAvailable,
                enabled = biometricAvailable,
                onCheckedChange = viewModel::setBiometricLock,
            )
            SwitchItem(
                title = stringResource(R.string.settings_brightness),
                summary = stringResource(R.string.settings_brightness_summary),
                checked = s.autoBrightness,
                onCheckedChange = viewModel::setAutoBrightness,
            )
            SwitchItem(
                title = stringResource(R.string.settings_notification),
                summary = stringResource(R.string.settings_notification_summary),
                checked = s.expiryNotification,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !ExpiryNotifier.canNotify(context)
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setExpiryNotification(enabled)
                    }
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_terms)) },
                supportingContent = {
                    Text(stringResource(if (s.termsAgreed) R.string.settings_terms_agreed else R.string.settings_terms_not_agreed))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenTerms),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium) },
            )
        }
    }
}

/** 生体認証または端末の PIN・パターン（API 28 以降で使える組み合わせ）。 */
const val LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}
