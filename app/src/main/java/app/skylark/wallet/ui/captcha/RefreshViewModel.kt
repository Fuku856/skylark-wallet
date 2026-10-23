package app.skylark.wallet.ui.captcha

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.skylark.wallet.R
import app.skylark.wallet.data.captcha.CaptchaSolver
import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.data.remote.SiteMaintenanceException
import app.skylark.wallet.data.remote.UnexpectedPageException
import app.skylark.wallet.domain.model.LookupOutcome
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.domain.repository.LookupSession
import app.skylark.wallet.domain.repository.VoucherRepository
import app.skylark.wallet.domain.usecase.StartRefreshUseCase
import app.skylark.wallet.domain.usecase.SubmitCaptchaUseCase
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
    private val captchaSolver: CaptchaSolver,
) : ViewModel() {

    sealed interface Phase {
        data object Loading : Phase
        data class AwaitingCaptcha(val image: ImageBitmap, val mismatch: Boolean, val message: String?) : Phase
        data class Submitting(val image: ImageBitmap) : Phase
        /** 自動再試行が3回失敗したときのポップアップ待機フェーズ */
        data class AutoCaptchaLimitReached(val lastImage: ImageBitmap) : Phase
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
        data class Failed(
            @StringRes val message: Int,
            val detail: String? = null,
            val canRetry: Boolean = false,
            /** 一括更新が途中で止まったときの、それまでの成功数。 */
            val batchProgress: BatchFinished? = null,
        ) : Event
        data class OpenOfficial(val voucherId: String) : Event
        data object TermsRequired : Event
    }

    private enum class Mode { Single, Batch, Official }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null

    /** 照会を始める準備（DB 読み込み）中のジョブ。この間に次の照会を受け付けないために使う。 */
    private var starting: Job? = null
    private var session: LookupSession? = null
    private var mode = Mode.Single
    private var queue: List<Voucher> = emptyList()
    private var index = 0
    private var succeeded = 0
    private var captchaVersion = 0
    private var autoRetryCount = 0

    /** ネットワークエラー時の「再試行」で、中断した券から再開するための記録。 */
    private data class RetryPoint(val mode: Mode, val queue: List<Voucher>, val index: Int, val succeeded: Int)

    private var retryPoint: RetryPoint? = null

    val isActive: Boolean get() = _state.value is UiState.Active

    /** 照会中、または照会を始める準備中か。 */
    private val isBusy: Boolean get() = isActive || starting?.isActive == true

    /** 1枚を照会する。[official] のときは成功後に公式画面（WebView）を開く。 */
    fun refreshOne(voucherId: String, official: Boolean = false) {
        if (isBusy) return
        starting = viewModelScope.launch {
            val voucher = voucherRepository.getVoucher(voucherId) ?: return@launch
            begin(if (official) Mode.Official else Mode.Single, listOf(voucher), 0)
        }
    }

    /** 使用済み・期限切れを除いた券を期限の近い順に1枚ずつ照会する。 */
    fun refreshAll() {
        if (isBusy) return
        starting = viewModelScope.launch {
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
        val point = retryPoint ?: return
        if (isBusy) return
        starting = viewModelScope.launch { begin(point.mode, point.queue, point.index, point.succeeded) }
    }

    private suspend fun begin(newMode: Mode, newQueue: List<Voucher>, startIndex: Int, startSucceeded: Int = 0) {
        if (!settingsRepository.current().termsAgreed) {
            _events.send(Event.TermsRequired)
            return
        }
        retryPoint = null
        job?.cancel()
        mode = newMode
        queue = newQueue
        index = startIndex
        succeeded = startSucceeded
        startCurrent()
    }

    private fun currentProgress(): Progress? =
        if (mode == Mode.Batch) Progress(index + 1, queue.size) else null

    private fun startCurrent() {
        val target = queue.getOrNull(index) ?: return finish()
        closeSession()
        autoRetryCount = 0
        _state.value = UiState.Active(target, Phase.Loading, currentProgress(), captchaVersion)
        job = viewModelScope.launch {
            try {
                val started = startRefresh()
                session = started.session
                val autoCaptcha = settingsRepository.current().autoCaptcha
                if (autoCaptcha) {
                    tryAutoSolve(started.captcha, started.session, target)
                } else {
                    showCaptcha(started.captcha, mismatch = false, message = null)
                }
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
        executeSubmit(current, active.target, captcha, phase.image, isAuto = false)
    }

    private fun executeSubmit(
        current: LookupSession,
        target: Voucher,
        captcha: String,
        image: ImageBitmap,
        isAuto: Boolean,
    ) {
        val active = _state.value as? UiState.Active ?: return
        _state.value = active.copy(phase = Phase.Submitting(image))
        job = viewModelScope.launch {
            try {
                when (val outcome = submitCaptcha(current, target, captcha)) {
                    is LookupOutcome.Success -> {
                        succeeded++
                        // 一括更新では最後にまとめて知らせる
                        if (mode != Mode.Batch) _events.send(Event.Updated(target, outcome.result.balance))
                        if (mode == Mode.Official) {
                            _events.send(Event.OpenOfficial(target.id))
                        }
                        next()
                    }
                    is LookupOutcome.CaptchaMismatch -> {
                        if (isAuto) {
                            handleAutoCaptchaFailure(image, current)
                        } else {
                            showCaptcha(current.fetchCaptcha(), mismatch = true, message = outcome.message)
                        }
                    }
                    is LookupOutcome.InvalidCode -> {
                        _events.send(Event.Failed(R.string.error_invalid_code, outcome.message))
                        if (mode == Mode.Batch) next() else finish()
                    }
                    LookupOutcome.Maintenance -> {
                        _events.send(Event.Failed(R.string.error_maintenance, batchProgress = batchProgress()))
                        finish()
                    }
                    LookupOutcome.ParseFailure -> {
                        _events.send(Event.Failed(R.string.error_parse, batchProgress = batchProgress()))
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

    private suspend fun tryAutoSolve(bytes: ByteArray, currentSession: LookupSession, target: Voucher) {
        val image = decodeImageBitmap(bytes)
        when (val solveResult = captchaSolver.solve(bytes)) {
            is CaptchaSolver.SolveResult.Success -> {
                executeSubmit(currentSession, target, solveResult.digits, image, isAuto = true)
            }
            is CaptchaSolver.SolveResult.Failure -> {
                handleAutoCaptchaFailure(image, currentSession)
            }
        }
    }

    private suspend fun handleAutoCaptchaFailure(lastImage: ImageBitmap, currentSession: LookupSession) {
        val active = _state.value as? UiState.Active ?: return
        if (autoRetryCount < MAX_AUTO_RETRIES) {
            autoRetryCount++
            _state.value = active.copy(phase = Phase.Loading)
            try {
                val newBytes = currentSession.fetchCaptcha()
                tryAutoSolve(newBytes, currentSession, active.target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        } else {
            // 3回自動再試行しても失敗した場合は一旦中断してポップアップを表示
            _state.value = active.copy(phase = Phase.AutoCaptchaLimitReached(lastImage))
        }
    }

    /** 自動再試行が3回失敗したポップアップで「再試行」を押したとき */
    fun retryAutoCaptcha() {
        val active = _state.value as? UiState.Active ?: return
        if (active.phase !is Phase.AutoCaptchaLimitReached) return
        val current = session ?: return
        autoRetryCount = 0
        _state.value = active.copy(phase = Phase.Loading)
        job = viewModelScope.launch {
            try {
                val newBytes = current.fetchCaptcha()
                tryAutoSolve(newBytes, current, active.target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 自動再試行が3回失敗したポップアップで「手動で入力」を押したとき */
    fun fallbackToManual() {
        val active = _state.value as? UiState.Active ?: return
        val limitPhase = active.phase as? Phase.AutoCaptchaLimitReached ?: return
        autoRetryCount = 0
        captchaVersion++
        _state.value = active.copy(
            phase = Phase.AwaitingCaptcha(limitPhase.lastImage, mismatch = true, message = null),
            captchaVersion = captchaVersion,
        )
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
        autoRetryCount = 0
        if (notify) _events.trySend(Event.BatchFinished(succeeded, queue.size))
        _state.value = UiState.Idle
        queue = emptyList()
    }

    private suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
        withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } ?: throw UnexpectedPageException("captcha image could not be decoded")

    private suspend fun showCaptcha(bytes: ByteArray, mismatch: Boolean, message: String?) {
        val image = decodeImageBitmap(bytes)
        val active = _state.value as? UiState.Active ?: return
        captchaVersion++
        _state.value = active.copy(
            phase = Phase.AwaitingCaptcha(image, mismatch, message),
            captchaVersion = captchaVersion,
        )
    }

    /** 一括更新が途中で止まるとき、それまでに更新できた枚数（1枚も無ければ null）。 */
    private fun batchProgress(): Event.BatchFinished? =
        if (mode == Mode.Batch && succeeded > 0) Event.BatchFinished(succeeded, queue.size) else null

    private suspend fun fail(e: Exception) {
        val progress = batchProgress()
        val event = when (e) {
            is SiteMaintenanceException -> Event.Failed(R.string.error_maintenance, batchProgress = progress)
            is UnexpectedPageException -> Event.Failed(R.string.error_parse, batchProgress = progress)
            is IOException -> {
                retryPoint = RetryPoint(mode, queue, index, succeeded)
                Event.Failed(R.string.error_network, canRetry = true, batchProgress = progress)
            }
            else -> Event.Failed(R.string.error_unknown, batchProgress = progress)
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
        autoRetryCount = 0
    }

    companion object {
        /** 画像の番号は4〜5桁の半角数字。 */
        val CAPTCHA_PATTERN = Regex("^\\d{4,5}$")
        /** 自動再試行の最大回数（3回まで勝手に再試行）。 */
        const val MAX_AUTO_RETRIES = 3
    }
}
