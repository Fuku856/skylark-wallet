package app.skylark.wallet.diagnostics

import android.util.Log

/**
 * アプリ全体のデバッグログ。裏設定で ON にしたときだけ logcat（タグ [TAG]）に出す。
 *
 * ご優待番号・バーコード番号・Cookie の値・HTML・メモ・バックアップのパスワードは渡さないこと。
 * 不具合調査のため、画像の番号と OCR の認識結果は出力する。念のため、長い数字列はここでも伏せる。
 */
object DebugLog {
    const val TAG = "SkylarkWallet"
    private val LONG_DIGITS = Regex("\\d{9,}")
    private val gate = DebugLogGate { priority, line -> Log.println(priority, TAG, line) }

    /** 設定を読めたら呼ぶ。読むまでのログは保持しておき、ON なら出力、OFF なら捨てる。 */
    fun setEnabled(enabled: Boolean) = gate.resolve(enabled)

    /** [area] はログの分類（lookup / net / voucher など）。 */
    fun log(area: String, message: String) {
        gate.emit(Log.INFO) { "[$area] ${sanitize(message)}" }
    }

    /**
     * 例外のメッセージとスタックトレースを出す。メッセージに利用者のデータが入りうる例外には [causeChain] を使う。
     * `Log.getStackTraceString` は UnknownHostException を含むと空文字を返すので使わない。
     */
    fun error(area: String, message: String, error: Throwable) {
        gate.emit(Log.ERROR) { "[$area] ${sanitize(message)}\n${sanitize(error.stackTraceToString())}" }
    }

    /** 例外と原因のクラス名だけを並べる。メッセージは含めない。 */
    fun causeChain(error: Throwable): String =
        generateSequence(error) { it.cause }.take(MAX_CAUSES).joinToString(" <- ") { it.javaClass.name }

    fun sanitize(message: String): String = message.replace(LONG_DIGITS, "***")

    private const val MAX_CAUSES = 8
}

/**
 * ログの出力を設定に合わせて切り替える。設定は非同期に読むので、読み終えるまでのログは
 * [capacity] 件まで保持し、起動直後のログも失わないようにする。
 */
internal class DebugLogGate(
    private val capacity: Int = 64,
    private val write: (priority: Int, line: String) -> Unit,
) {
    private val pending = ArrayDeque<Pair<Int, String>>()
    private var resolved = false
    private var enabled = false

    @Synchronized
    fun emit(priority: Int, line: () -> String) {
        if (enabled) {
            write(priority, line())
        } else if (!resolved) {
            if (pending.size == capacity) pending.removeFirst()
            pending.addLast(priority to line())
        }
    }

    @Synchronized
    fun resolve(enabled: Boolean) {
        this.enabled = enabled
        resolved = true
        if (enabled) pending.forEach { (priority, line) -> write(priority, line) }
        pending.clear()
    }
}
