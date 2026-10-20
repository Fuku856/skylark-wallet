package com.example.skylarkwallet.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.R
import com.example.skylarkwallet.data.backup.BackupManager
import com.example.skylarkwallet.data.backup.BackupPassword
import com.example.skylarkwallet.ui.lock.BiometricAuth
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 設定画面の「バックアップを作成」「バックアップから復元」と、その流れで出すダイアログ。 */
@Composable
fun BackupItems(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val activity = LocalActivity.current as? FragmentActivity
    var showExportNotice by rememberSaveable { mutableStateOf(false) }
    var showAuthUnavailable by rememberSaveable { mutableStateOf(false) }
    // パスワードを間違えたときに打ち直さなくて済むよう、照合中も入力を残す（保存状態には残さない）
    var passwordInput by remember { mutableStateOf("") }
    LaunchedEffect(state) {
        if (state !is BackupViewModel.State.AskPassword && state != BackupViewModel.State.Working) passwordInput = ""
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::export) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::selectImportFile)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_export)) },
        supportingContent = { Text(stringResource(R.string.backup_export_summary)) },
        modifier = Modifier.clickable {
            // 券をまとめて持ち出せる操作なので、アプリのロック設定に関わらず毎回本人確認する。
            // 画面ロックが無い端末では確認できないため、作成させない
            if (activity != null && BiometricAuth.canAuthenticate(context)) {
                BiometricAuth.authenticate(
                    activity = activity,
                    title = resources.getString(R.string.backup_auth_title),
                    subtitle = resources.getString(R.string.backup_auth_subtitle),
                    onSuccess = { showExportNotice = true },
                )
            } else {
                showAuthUnavailable = true
            }
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_import)) },
        supportingContent = { Text(stringResource(R.string.backup_import_summary)) },
        // 独自の拡張子には MIME タイプが無いので、すべてのファイルから選んでもらう
        modifier = Modifier.clickable { openDocument.launch(arrayOf("*/*")) },
    )

    if (showExportNotice) {
        AlertDialog(
            onDismissRequest = { showExportNotice = false },
            title = { Text(stringResource(R.string.backup_export)) },
            text = { Text(stringResource(R.string.backup_export_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    showExportNotice = false
                    createDocument.launch(defaultFileName())
                }) { Text(stringResource(R.string.backup_choose_location)) }
            },
            dismissButton = { TextButton(onClick = { showExportNotice = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showAuthUnavailable) {
        MessageDialog(
            title = stringResource(R.string.backup_export),
            message = stringResource(R.string.backup_auth_unavailable),
            onDismiss = { showAuthUnavailable = false },
        )
    }

    when (val s = state) {
        BackupViewModel.State.Idle -> Unit
        BackupViewModel.State.Working -> WorkingDialog()
        is BackupViewModel.State.Exported -> PasswordDialog(s.password, s.count, onDone = viewModel::dismiss)
        is BackupViewModel.State.AskPassword -> PasswordInputDialog(
            input = passwordInput,
            onInputChange = { passwordInput = it.take(MAX_PASSWORD_INPUT) },
            wrongPassword = s.wrongPassword,
            onDismiss = viewModel::dismiss,
            onSubmit = { viewModel.import(s.uri, it) },
        )
        is BackupViewModel.State.Imported -> MessageDialog(
            title = stringResource(R.string.backup_import_done_title),
            message = buildString {
                append(stringResource(R.string.backup_import_added, s.summary.added))
                if (s.summary.skippedDuplicate > 0) {
                    append('\n').append(stringResource(R.string.backup_import_skipped_duplicate, s.summary.skippedDuplicate))
                }
                if (s.summary.skippedInvalid > 0) {
                    append('\n').append(stringResource(R.string.backup_import_skipped_invalid, s.summary.skippedInvalid))
                }
                append("\n\n").append(stringResource(R.string.backup_import_delete_file))
            },
            onDismiss = viewModel::dismiss,
        )
        is BackupViewModel.State.Failed -> MessageDialog(
            title = stringResource(R.string.backup_error_title),
            message = stringResource(s.message),
            onDismiss = viewModel::dismiss,
        )
    }
}

private fun defaultFileName(): String =
    "skylark-wallet-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.${BackupManager.FILE_EXTENSION}"

@Composable
private fun WorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingIndicator()
                Text(stringResource(R.string.backup_working))
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

/**
 * 書き出したファイルのパスワードを表示する。パスワードはこの画面でしか見られないため、
 * 書き留めたことを確かめてから閉じられるようにする。写真に撮ってクラウドに同期されないよう、スクリーンショットも禁止する。
 */
@Composable
private fun PasswordDialog(password: String, count: Int, onDone: () -> Unit) {
    var noted by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.backup_password_message, count))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        password,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    stringResource(R.string.backup_password_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = noted, role = Role.Checkbox, onValueChange = { noted = it }),
                ) {
                    Checkbox(checked = noted, onCheckedChange = null)
                    Text(stringResource(R.string.backup_password_noted), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone, enabled = noted) { Text(stringResource(R.string.close)) } },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    )
}

@Composable
private fun PasswordInputDialog(
    input: String,
    onInputChange: (String) -> Unit,
    wrongPassword: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val wellFormed = BackupPassword.isWellFormed(BackupPassword.normalize(input))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_input_message))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX", fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.backup_error_wrong_password)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(input) }, enabled = wellFormed) { Text(stringResource(R.string.backup_restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

/** ハイフンや空白を含めても十分な長さ。貼り付けで極端に長い文字列が入るのを防ぐ。 */
private const val MAX_PASSWORD_INPUT = 64

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
