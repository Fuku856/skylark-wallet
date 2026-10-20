package app.skylark.wallet.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.skylark.wallet.BuildConfig
import app.skylark.wallet.R
import app.skylark.wallet.domain.model.ThemeMode
import app.skylark.wallet.notification.ExpiryNotifier
import app.skylark.wallet.ui.common.openExternalUrl
import app.skylark.wallet.ui.lock.BiometricAuth

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current as? FragmentActivity
    val biometricAvailable = remember { BiometricAuth.canAuthenticate(context) }
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
        // 項目が少なくてもフッターが画面の最下部に来るよう、スクロール領域の最小の高さを画面の高さにする
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                SwitchItem(
                    title = stringResource(R.string.settings_biometric),
                    summary = stringResource(
                        if (biometricAvailable) R.string.settings_biometric_summary else R.string.settings_biometric_unavailable,
                    ),
                    checked = s.biometricLock && biometricAvailable,
                    enabled = biometricAvailable && activity != null,
                    onCheckedChange = { enabled ->
                        // 有効にするときは、実際に認証できることを確かめてから保存する（解除できないロックを防ぐ）
                        if (enabled && activity != null) {
                            BiometricAuth.authenticate(
                                activity = activity,
                                title = resources.getString(R.string.lock_enable_prompt_title),
                                subtitle = resources.getString(R.string.lock_enable_prompt_subtitle),
                                onSuccess = { viewModel.setBiometricLock(true) },
                            )
                        } else {
                            viewModel.setBiometricLock(false)
                        }
                    },
                )
                ThemeItem(selected = s.themeMode, onSelect = viewModel::setThemeMode)
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
                CategoryHeader(stringResource(R.string.backup_category))
                BackupItems()
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
                    headlineContent = { Text(stringResource(R.string.settings_github)) },
                    supportingContent = { Text(REPOSITORY_URL) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_github), contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable { openExternalUrl(context, REPOSITORY_URL.toUri()) },
                )
                Spacer(Modifier.weight(1f))
                AboutFooter()
            }
        }
    }
}

private const val REPOSITORY_URL = "https://github.com/Fuku856/skylark-wallet"

@Composable
private fun ThemeItem(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(stringResource(label)) }
                }
            }
        },
    )
}

/** 設定項目をまとめるカテゴリの見出し。 */
@Composable
private fun CategoryHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** アプリ名・バージョン・クレジット。バージョンはリリース時にタグから設定される。 */
@Composable
private fun AboutFooter() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.settings_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
