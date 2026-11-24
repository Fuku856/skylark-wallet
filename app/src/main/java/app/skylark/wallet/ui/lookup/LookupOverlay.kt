package app.skylark.wallet.ui.lookup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.skylark.wallet.R
import app.skylark.wallet.ui.common.SecureWindow
import app.skylark.wallet.ui.common.SwipeableSnackbarHost

/**
 * 照会中に画面全体へ重ねる、サイトの入力画面（WebView）。番号の入力と送信はアプリが行い、
 * 人には reCAPTCHA のチェックだけを求める。それ以外の間は WebView を覆って触れないようにする。
 *
 * Dialog ではなく同じウィンドウに重ねるので、[SecureWindow] の FLAG_SECURE がそのまま効く。
 */
@Composable
fun LookupOverlay(
    state: RefreshViewModel.UiState.Active,
    termsAgreed: Boolean,
    listener: LookupPageListener,
    snackbarHostState: SnackbarHostState,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    SecureWindow()
    BackHandler(onBack = onCancel)

    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.lookup_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    R.string.lookup_target,
                                    state.target.memo.ifBlank { stringResource(R.string.voucher_default_name) },
                                    state.target.last3,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lookup_abort))
                        }
                    },
                    actions = {
                        state.progress?.let {
                            Text(
                                text = stringResource(R.string.lookup_progress, it.current, it.total),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = onSkip) { Text(stringResource(R.string.lookup_skip)) }
                        }
                    },
                )
            },
            snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                StatusBanner(state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LookupWebView(
                        code9 = state.target.code9,
                        agree = termsAgreed,
                        loadKey = state.loadKey,
                        listener = listener,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.phase != RefreshViewModel.Phase.AwaitingCheck) BusyScrim()
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(state: RefreshViewModel.UiState.Active) {
    val awaiting = state.phase == RefreshViewModel.Phase.AwaitingCheck
    Surface(
        color = if (awaiting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.retrying) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.lookup_check_rejected),
                        state.retryMessage?.takeIf { it.isNotBlank() },
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(
                    when (state.phase) {
                        RefreshViewModel.Phase.LoadingPage -> R.string.lookup_status_loading
                        RefreshViewModel.Phase.AwaitingCheck -> R.string.lookup_status_check
                        RefreshViewModel.Phase.Submitting -> R.string.lookup_status_submitting
                        RefreshViewModel.Phase.Saving -> R.string.lookup_status_saving
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (awaiting) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 読み込み・送信・保存の間、WebView を覆ってタップを受け止める（状態の文言は上の帯に出す）。 */
@Composable
private fun BusyScrim() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
