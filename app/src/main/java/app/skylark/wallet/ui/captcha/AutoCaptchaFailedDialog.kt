package app.skylark.wallet.ui.captcha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skylark.wallet.R

/**
 * 自動CAPTCHAが3回連続で失敗したときに、次のアクションをユーザーに問い合わせるダイアログ。
 */
@Composable
fun AutoCaptchaFailedDialog(
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
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
            Text(stringResource(R.string.auto_captcha_failed_message))
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                OutlinedButton(onClick = onManual) {
                    Text(stringResource(R.string.auto_captcha_manual_input))
                }
            }
        },
    )
}
