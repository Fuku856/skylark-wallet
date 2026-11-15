package app.skylark.wallet.ui.captcha

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import app.skylark.wallet.R

/**
 * 自動CAPTCHA が [RefreshViewModel.MAX_AUTO_ATTEMPTS] 回続けて失敗したときに、次の操作をユーザーに尋ねるダイアログ。
 * [canSkip] は一括更新中のみ true で、今の券だけを飛ばせる。
 */
@Composable
fun AutoCaptchaFailedDialog(
    canSkip: Boolean,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        // 外側に触れただけで一括更新ごと止まらないよう、閉じるのは戻る操作とボタンだけにする
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(stringResource(R.string.auto_captcha_failed_title))
        },
        text = {
            Text(stringResource(R.string.auto_captcha_failed_message, RefreshViewModel.MAX_AUTO_ATTEMPTS))
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
        // ボタンは AlertDialog が横に並べ、入りきらなければ折り返す
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(if (canSkip) R.string.captcha_abort else R.string.cancel))
            }
            if (canSkip) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.captcha_skip))
                }
            }
            OutlinedButton(onClick = onManual) {
                Text(stringResource(R.string.auto_captcha_manual_input))
            }
        },
    )
}
