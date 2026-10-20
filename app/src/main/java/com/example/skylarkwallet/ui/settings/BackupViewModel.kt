package com.example.skylarkwallet.ui.settings

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skylarkwallet.R
import com.example.skylarkwallet.data.backup.BackupException
import com.example.skylarkwallet.data.backup.BackupManager
import com.example.skylarkwallet.domain.model.ImportSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Working : State

        /** 書き出しに成功した。[password] は利用者にメモしてもらうまで表示し続ける。 */
        data class Exported(val password: String, val count: Int) : State

        /** 読み込むファイルを選んだので、パスワードの入力を待つ。 */
        data class AskPassword(val uri: Uri, val wrongPassword: Boolean = false) : State
        data class Imported(val summary: ImportSummary) : State
        data class Failed(@StringRes val message: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun export(uri: Uri) = work {
        val result = manager.export(uri)
        State.Exported(result.password, result.count)
    }

    fun selectImportFile(uri: Uri) {
        _state.value = State.AskPassword(uri)
    }

    fun import(uri: Uri, password: String) = work(onWrongPassword = { State.AskPassword(uri, wrongPassword = true) }) {
        State.Imported(manager.import(uri, password))
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private fun work(onWrongPassword: () -> State = { State.Failed(R.string.backup_error_corrupted) }, block: suspend () -> State) {
        _state.value = State.Working
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: BackupException.WrongPasswordOrCorrupted) {
                onWrongPassword()
            } catch (e: BackupException.NotBackupFile) {
                State.Failed(R.string.backup_error_not_backup)
            } catch (e: BackupException.UnsupportedVersion) {
                State.Failed(R.string.backup_error_version)
            } catch (e: IOException) {
                Log.w(TAG, "backup io failed: ${e.javaClass.simpleName}")
                State.Failed(R.string.backup_error_io)
            } catch (e: SecurityException) {
                Log.w(TAG, "backup io failed: ${e.javaClass.simpleName}")
                State.Failed(R.string.backup_error_io)
            }
        }
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}
