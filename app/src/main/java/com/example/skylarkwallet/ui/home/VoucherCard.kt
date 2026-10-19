package com.example.skylarkwallet.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.skylarkwallet.R
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.ui.common.Format

@Composable
fun VoucherCard(
    voucher: Voucher,
    now: Long,
    onRefresh: () -> Unit,
    onShowBarcode: () -> Unit,
    onEditMemo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(voucher.id) { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme

    ElevatedCard(
        onClick = { if (!voucher.isChecked) onRefresh() else expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .then(if (voucher.isUsed) Modifier.desaturate() else Modifier)
            .animateContentSize(motion.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = voucher.memo.ifBlank { stringResource(R.string.voucher_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.voucher_last3, voucher.last3),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (voucher.balance != null) {
                    Text(
                        text = stringResource(R.string.yen_format_text, Format.yen(voucher.balance)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.balance_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val days = voucher.daysUntilExpiry(now)
                when {
                    voucher.isExpired(now) -> Pill(
                        stringResource(R.string.badge_expired),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                    !voucher.isUsed && voucher.isExpiringSoon(now) && days != null -> Pill(
                        stringResource(R.string.badge_expiring, days),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (voucher.needsRefresh(now)) {
                    Pill(
                        stringResource(R.string.badge_stale),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                if (voucher.lastError != null) {
                    Pill(
                        stringResource(R.string.badge_error),
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.onError,
                    )
                }
                if (voucher.isUsed) {
                    Pill(
                        stringResource(R.string.badge_used),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            voucher.expiryAt?.let {
                Text(
                    stringResource(R.string.voucher_expiry, Format.dateTime(it)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = voucher.lastUpdatedAt?.let { stringResource(R.string.voucher_checked_at, Format.dateTime(it)) }
                    ?: stringResource(R.string.voucher_tap_to_check),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(
                visible = expanded || !voucher.isChecked,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    voucher.lastError?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_refresh))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (voucher.barcode13 != null) {
                            FilledTonalButton(onClick = onShowBarcode) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_barcode))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEditMemo) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit_memo))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val DesaturatePaint = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })
    alpha = 0.8f
}

/** 使用済みの券の彩度を落とす。 */
private fun Modifier.desaturate(): Modifier = drawWithContent {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), DesaturatePaint)
        drawContent()
        canvas.restore()
    }
}
