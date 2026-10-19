package com.example.skylarkwallet.ui.barcode

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.skylarkwallet.R
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.ui.common.Format
import com.example.skylarkwallet.ui.common.SecureWindow

@Composable
fun BarcodeScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSaved: (voucherId: String) -> Unit,
    onOpenLatest: (voucherId: String) -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.barcode_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> LoadingIndicator()
                state.vouchers.isEmpty() -> Text(
                    stringResource(R.string.barcode_empty),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                else -> BarcodePager(state, onOpenSaved, onOpenLatest)
            }
        }
    }
}

@Composable
private fun BarcodePager(
    state: BarcodeViewModel.UiState,
    onOpenSaved: (String) -> Unit,
    onOpenLatest: (String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.vouchers.size }
    var bright by rememberSaveable { mutableStateOf(state.autoBrightness) }
    var officialFor by remember { mutableStateOf<Voucher?>(null) }

    ScreenBrightness(full = bright)

    val scale by animateFloatAsState(
        targetValue = if (bright) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "barcodeScale",
    )

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f),
        ) { page ->
            val voucher = state.vouchers[page]
            BarcodePage(
                voucher = voucher,
                scale = scale,
                onToggleBrightness = { bright = !bright },
                onOfficial = { officialFor = voucher },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (state.vouchers.size > 1) {
                Text(
                    stringResource(R.string.captcha_progress, pagerState.currentPage + 1, state.vouchers.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(16.dp))
            }
            IconToggleButton(checked = bright, onCheckedChange = { bright = it }) {
                Icon(
                    if (bright) Icons.Rounded.BrightnessHigh else Icons.Rounded.BrightnessLow,
                    contentDescription = stringResource(R.string.barcode_brightness),
                )
            }
        }
    }

    officialFor?.let { voucher ->
        AlertDialog(
            onDismissRequest = { officialFor = null },
            title = { Text(stringResource(R.string.official_title)) },
            text = { Text(stringResource(R.string.official_message)) },
            confirmButton = {
                TextButton(onClick = {
                    officialFor = null
                    onOpenLatest(voucher.id)
                }) { Text(stringResource(R.string.official_latest)) }
            },
            dismissButton = {
                TextButton(
                    enabled = voucher.hasResultHtml,
                    onClick = {
                        officialFor = null
                        onOpenSaved(voucher.id)
                    },
                ) { Text(stringResource(R.string.official_saved)) }
            },
        )
    }
}

@Composable
private fun BarcodePage(
    voucher: Voucher,
    scale: Float,
    onToggleBrightness: () -> Unit,
    onOfficial: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                voucher.memo.ifBlank { stringResource(R.string.voucher_default_name) },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.voucher_last3, voucher.last3),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            voucher.balance?.let {
                Text(
                    stringResource(R.string.yen_format_text, Format.yen(it)),
                    style = MaterialTheme.typography.displayMedium,
                )
            }
            Spacer(Modifier.height(8.dp))

            val barcode = voucher.barcode13
            if (barcode != null) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleBrightness,
                        ),
                ) {
                    Ean13Barcode(barcode, Modifier.fillMaxWidth())
                }
            }
            Text(
                stringResource(R.string.barcode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            voucher.expiryAt?.let {
                Text(stringResource(R.string.voucher_expiry, Format.dateTime(it)), style = MaterialTheme.typography.bodyMedium)
            }
            voucher.balanceCheckedAt?.let {
                Text(
                    stringResource(R.string.barcode_checked_at, Format.dateTime(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onOfficial, shape = CircleShape) {
                Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.official_button))
            }
        }
    }
}

/** 画面輝度を MAX にする。OFF にしたときと画面を離れるときは必ずシステム設定に戻す。 */
@Composable
private fun ScreenBrightness(full: Boolean) {
    val window = LocalActivity.current?.window ?: return
    LaunchedEffect(full) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (full) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    DisposableEffect(window) {
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
