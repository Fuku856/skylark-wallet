package app.skylark.wallet.ui.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * 生体認証ロックを解除済みか。画面回転などで Activity が作り直されても保つため ViewModel に置く。
 * プロセスが終了したら保存しない（次の起動では必ずロックする）。
 */
class LockViewModel : ViewModel() {
    var unlocked by mutableStateOf(false)

    /**
     * アプリ内の認証ダイアログが結果を待っているか。Android 9・10 では PIN・パターンの入力画面が別のタスクで開き、
     * その間に onStop でロックがかかる。戻ってきたときに、その結果を待たずにロック画面から認証を重ねて出さないために使う。
     */
    var authenticating by mutableStateOf(false)
}
