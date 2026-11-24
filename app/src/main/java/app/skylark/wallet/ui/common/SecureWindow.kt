package app.skylark.wallet.ui.common

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

/**
 * 表示中だけ FLAG_SECURE を付け、スクリーンショットと「最近使ったアプリ」への映り込みを防ぐ。
 *
 * 照会の画面をバーコード画面の上に重ねたり、画面遷移のアニメーション中に前後の画面が同時に表示されたりするので、
 * 同じウィンドウで使っている画面の数を数え、最後の1つが外れたときだけ FLAG_SECURE を外す。
 */
@Composable
fun SecureWindow() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        SecureWindowCounter.acquire(window)
        onDispose { SecureWindowCounter.release(window) }
    }
}

/** ウィンドウごとの利用数。Compose の DisposableEffect からだけ（メインスレッドで）呼ばれる。 */
internal object SecureWindowCounter {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = counts[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        counts[window] = count + 1
    }

    fun release(window: Window) {
        val count = (counts[window] ?: 0) - 1
        if (count <= 0) {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            counts[window] = count
        }
    }
}
