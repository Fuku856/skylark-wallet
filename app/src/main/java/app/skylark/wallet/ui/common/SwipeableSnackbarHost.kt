package app.skylark.wallet.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance

/**
 * 横にスワイプして消せる SnackbarHost。
 *
 * Snackbar の既定の色は inverseSurface で、ダークテーマでは明るい帯になって浮くため、
 * ダークテーマのときは画面と同じ系統の暗い面の色で出す。
 */
@Composable
fun SwipeableSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        // 次の Snackbar がスワイプ済みの状態を引き継がないよう、表示ごとに状態を作り直す
        key(data) {
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) data.dismiss()
            }
            SwipeToDismissBox(state = dismissState, backgroundContent = {}) {
                val colors = MaterialTheme.colorScheme
                if (colors.surface.luminance() < 0.5f) {
                    Snackbar(
                        snackbarData = data,
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface,
                        actionColor = colors.primary,
                        dismissActionContentColor = colors.onSurfaceVariant,
                    )
                } else {
                    Snackbar(data)
                }
            }
        }
    }
}
