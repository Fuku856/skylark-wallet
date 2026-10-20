package com.example.skylarkwallet.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * ご優待番号を表示中の券。認証の途中でロック画面に切り替わり、ホーム画面が作り直されても失わないよう Activity にひも付ける。
 * プロセスが終了したら保存しない（次に見るときは必ず認証する）。
 */
class CodeRevealViewModel : ViewModel() {
    var voucherId by mutableStateOf<String?>(null)
}
