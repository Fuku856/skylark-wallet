package com.example.skylarkwallet.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.skylarkwallet.R

const val TERMS_URL = "https://www.skylarkyutaiken.com/input#riyoukiyaku"

/**
 * サイトの利用規約への同意画面。初回起動時と設定画面から表示する。
 * 同意済みのときだけ、照会の POST に「利用規約に同意する」を付ける。
 */
@Composable
fun TermsScreen(
    alreadyAgreed: Boolean,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    var checked by rememberSaveable { mutableStateOf(alreadyAgreed) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.terms_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.terms_body), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.terms_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, TERMS_URL.toUri()))
            }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.terms_open))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it }),
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.terms_checkbox), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onAgree, enabled = checked, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.terms_agree))
            }
            TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (alreadyAgreed) R.string.terms_revoke else R.string.terms_later))
            }
        }
    }
}
