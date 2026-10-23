package app.skylark.wallet.ui.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout

/**
 * 入力欄があり、開くと同時にキーボードを出すボトムシート。
 *
 * ModalBottomSheet の既定の insets（safeDrawing）は ime を含むので、キーボードが出るとシートの背が伸び、
 * 止まる位置が作り直される。シートの出現アニメーションの途中だとすぐには移れず、キーボードが上がりきってから
 * シートが遅れて上がる。そこで ime を insets から外し、キーボードの分は配置をずらすだけで持ち上げる。
 * ずらす量は配置のときに毎フレーム読むので、キーボードと同じフレームで動く。
 */
@Composable
fun KeyboardBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.liftAboveKeyboard(),
        contentWindowInsets = { sheetInsets() },
        content = content,
    )
}

/** シートの中身に付ける余白。ステータスバー・ナビゲーションバーの分だけで、キーボードの分は含めない。 */
@Composable
private fun sheetInsets(): WindowInsets =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)

// insets はシートのウィンドウのものを読む必要がある（シートの外で取ると Activity のウィンドウの値になり、
// キーボードのアニメーションの途中経過が届かず一気に動く）。composed にして、シートの中で取り出す
private fun Modifier.liftAboveKeyboard(): Modifier = composed {
    val ime = WindowInsets.ime
    val bottomInsets = sheetInsets()
    val statusBars = WindowInsets.statusBars
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            // ナビゲーションバーの余白はシートの中に付いているので、その分はキーボードの裏に入れてよい
            var lift = (ime.getBottom(this@layout) - bottomInsets.getBottom(this@layout)).coerceAtLeast(0)
            // 背の高いシートが画面の上にはみ出さないようにする
            if (constraints.hasBoundedHeight) {
                val room = constraints.maxHeight - placeable.height - statusBars.getTop(this@layout)
                lift = lift.coerceAtMost(room.coerceAtLeast(0))
            }
            placeable.place(0, -lift)
        }
    }
}
