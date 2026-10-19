package com.example.skylarkwallet.ui.lock

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
}
