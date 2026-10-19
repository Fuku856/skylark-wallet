package com.example.skylarkwallet.ui.captcha

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.skylarkwallet.R
import com.example.skylarkwallet.data.local.prefs.SettingsRepository
import com.example.skylarkwallet.data.remote.SiteMaintenanceException
import com.example.skylarkwallet.data.remote.UnexpectedPageException
import com.example.skylarkwallet.domain.model.LookupOutcome
import com.example.skylarkwallet.domain.model.Voucher
import com.example.skylarkwallet.domain.repository.LookupSession
import com.example.skylarkwallet.domain.repository.VoucherRepository
import com.example.skylarkwallet.domain.usecase.StartRefreshUseCase
import com.example.skylarkwallet.domain.usecase.SubmitCaptchaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * 照会（CAPTCHA 手入力）の状態を管理する。Activity スコープで共有し、ホーム・バーコード画面のどちらからでも使う。
 *
 * 状態遷移: Loading → AwaitingCaptcha → Submitting → (成功 → 次の券 / CAPTCHA 不一致 → AwaitingCaptcha / エラー → 終了)
 */
@HiltViewModel
class RefreshViewModel @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val settingsRepository: SettingsRepository,
    private val startRefresh: StartRefreshUseCase,
    private val submitCaptcha: SubmitCaptchaUseCase,
) : ViewModel() {

    sealed interface Phase {
        data object Loading : Phase
        data class AwaitingCaptcha(val image: ImageBitmap, val mismatch: Boolean, val message: String?) : Phase
        data class Submitting(val image: ImageBitmap) : Phase
    }

    data class Progress(val current: Int, val total: Int)

    sealed interface UiState {
        data object Idle : UiState
        data class Active(
            val target: Voucher,
            val phase: Phase,
            /** 一括更新中のみ。 */
            val progress: Progress?,
            /** 入力欄をクリアするためのカウンタ。新しい画像を表示するたびに増える。 */
            val captchaVersion: Int,
        ) : UiState
    }

    sealed interface Event {
        data class Updated(val voucher: Voucher, val balance: Int) : Event
        data class BatchFinished(val succeeded: Int, val total: Int) : Event
        data class Failed(@StringRes val message: Int, val detail: String? = null, val canRetry: Boolean = false) : Event
        data class OpenOfficial(val voucherId: String) : Event
        data object TermsRequired : Event
    }

    private enum class Mode { Single, Batch, Official }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null
    private var session: LookupSession? = null
    private var mode = Mode.Single
    private var queue: List<Voucher> = emptyList()
    private var index = 0
    private var succeeded = 0
    private var captchaVersion = 0

    /** ネットワークエラー時の「再試行」で、中断した券から再開するための記録。 */
    private var retryPoint: Triple<Mode, List<Voucher>, Int>? = null

    val isActive: Boolean get() = _state.value is UiState.Active

    /** 1枚を照会する。[official] のときは成功後に公式画面（WebView）を開く。 */
    fun refreshOne(voucherId: String, official: Boolean = false) {
        if (isActive) return
        viewModelScope.launch {
            val voucher = voucherRepository.getVoucher(voucherId) ?: return@launch
            begin(if (official) Mode.Official else Mode.Single, listOf(voucher), 0)
        }
    }

    /** 使用済み・期限切れを除いた券を期限の近い順に1枚ずつ照会する。 */
    fun refreshAll() {
        if (isActive) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val targets = voucherRepository.getVouchers()
                .filter { it.isRefreshTarget(now) }
                .sortedWith(Voucher.ExpiryOrder)
            if (targets.isEmpty()) {
                _events.send(Event.Failed(R.string.refresh_nothing))
                return@launch
            }
            begin(Mode.Batch, targets, 0)
        }
    }

    fun retry() {
        val (retryMode, retryQueue, retryIndex) = retryPoint ?: return
        if (isActive) return
        viewModelScope.launch { begin(retryMode, retryQueue, retryIndex) }
    }

    private suspend fun begin(newMode: Mode, newQueue: List<Voucher>, startIndex: Int) {
        if (!settingsRepository.current().termsAgreed) {
            _events.send(Event.TermsRequired)
            return
        }
        retryPoint = null
        mode = newMode
        queue = newQueue
        index = startIndex
        succeeded = 0
        startCurrent()
    }

    private fun currentProgress(): Progress? =
        if (mode == Mode.Batch) Progress(index + 1, queue.size) else null

    private fun startCurrent() {
        val target = queue.getOrNull(index) ?: return finish()
        closeSession()
        _state.value = UiState.Active(target, Phase.Loading, currentProgress(), captchaVersion)
        job = viewModelScope.launch {
            try {
                val started = startRefresh()
                session = started.session
                showCaptcha(started.captcha, mismatch = false, message = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 同じセッションで別の CAPTCHA 画像を取得する（画像交換）。 */
    fun changeCaptcha() {
        val active = _state.value as? UiState.Active ?: return
        if (active.phase !is Phase.AwaitingCaptcha) return
        val current = session ?: return
        _state.value = active.copy(phase = Phase.Loading)
        job = viewModelScope.launch {
            try {
                showCaptcha(current.fetchCaptcha(), mismatch = false, message = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun submit(captcha: String) {
        val active = _state.value as? UiState.Active ?: return
        val phase = active.phase as? Phase.AwaitingCaptcha ?: return // 二重送信防止
        val current = session ?: return
        if (!CAPTCHA_PATTERN.matches(captcha)) return
        _state.value = active.copy(phase = Phase.Submitting(phase.image))
        job = viewModelScope.launch {
            try {
                when (val outcome = submitCaptcha(current, active.target, captcha)) {
                    is LookupOutcome.Success -> {
                        succeeded++
                        // 一括更新では最後にまとめて知らせる
                        if (mode != Mode.Batch) _events.send(Event.Updated(active.target, outcome.result.balance))
                        if (mode == Mode.Official) {
                            _events.send(Event.OpenOfficial(active.target.id))
                        }
                        next()
                    }
                    is LookupOutcome.CaptchaMismatch ->
                        showCaptcha(current.fetchCaptcha(), mismatch = true, message = outcome.message)
                    is LookupOutcome.InvalidCode -> {
                        _events.send(Event.Failed(R.string.error_invalid_code, outcome.message))
                        if (mode == Mode.Batch) next() else finish()
                    }
                    LookupOutcome.Maintenance -> {
                        _events.send(Event.Failed(R.string.error_maintenance))
                        finish()
                    }
                    LookupOutcome.ParseFailure -> {
                        _events.send(Event.Failed(R.string.error_parse))
                        finish()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 一括更新で今の券を飛ばして次へ進む。 */
    fun skip() {
        if (!isActive) return
        job?.cancel()
        next()
    }

    /** 照会を中止する（一括更新ではキュー全体を中止する）。 */
    fun cancel() {
        if (!isActive) return
        job?.cancel()
        finish(notify = mode == Mode.Batch && succeeded > 0)
    }

    private fun next() {
        index++
        if (index < queue.size) startCurrent() else finish(notify = mode == Mode.Batch)
    }

    private fun finish(notify: Boolean = false) {
        closeSession()
        if (notify) _events.trySend(Event.BatchFinished(succeeded, queue.size))
        _state.value = UiState.Idle
        queue = emptyList()
    }

    private suspend fun showCaptcha(bytes: ByteArray, mismatch: Boolean, message: String?) {
        val image = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } ?: throw UnexpectedPageException("captcha image could not be decoded")
        val active = _state.value as? UiState.Active ?: return
        captchaVersion++
        _state.value = active.copy(
            phase = Phase.AwaitingCaptcha(image, mismatch, message),
            captchaVersion = captchaVersion,
        )
    }

    private suspend fun fail(e: Exception) {
        val event = when (e) {
            is SiteMaintenanceException -> Event.Failed(R.string.error_maintenance)
            is UnexpectedPageException -> Event.Failed(R.string.error_parse)
            is IOException -> {
                retryPoint = Triple(mode, queue, index)
                Event.Failed(R.string.error_network, canRetry = true)
            }
            else -> Event.Failed(R.string.error_unknown)
        }
        _events.send(event)
        finish()
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    override fun onCleared() {
        closeSession()
    }

    companion object {
        /** 画像の番号は4〜5桁の半角数字。 */
        val CAPTCHA_PATTERN = Regex("^\\d{4,5}$")
    }
}
