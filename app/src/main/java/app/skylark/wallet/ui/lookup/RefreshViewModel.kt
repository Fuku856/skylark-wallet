package app.skylark.wallet.ui.lookup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.skylark.wallet.R
import app.skylark.wallet.data.local.prefs.SettingsRepository
import app.skylark.wallet.diagnostics.DebugLog
import app.skylark.wallet.domain.model.LookupOutcome
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.domain.repository.VoucherRepository
import app.skylark.wallet.domain.usecase.SaveLookupResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 照会の状態を管理する。Activity スコープで共有し、ホーム・バーコード画面のどちらからでも使う。
 *
 * 照会はサイトの入力画面を WebView（[LookupOverlay]）で開いて行う。9桁の入力・規約の同意・送信・結果の保存・
 * 次の券への移動はアプリが行い、reCAPTCHA のチェック（と画像の選択）は必ず人が行う。
 *
 * 状態遷移: LoadingPage → AwaitingCheck →（チェック完了で自動送信）Submitting → Saving →
 * (成功 → 次の券 / 認証失敗 → LoadingPage で開き直す / エラー → 終了)
 */
@HiltViewModel
class RefreshViewModel @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val settingsRepository: SettingsRepository,
    private val saveLookupResult: SaveLookupResultUseCase,
) : ViewModel() {

    sealed interface Phase {
        /** 入力画面を読み込み、番号を入れている。 */
        data object LoadingPage : Phase

        /** reCAPTCHA のチェックを人に求めている。 */
        data object AwaitingCheck : Phase

        /** チェックが済み、送信した結果ページを待っている。 */
        data object Submitting : Phase

        /** 結果ページを解析・保存している（一括更新では次の券までの間隔もここで待つ）。 */
        data object Saving : Phase
    }

    data class Progress(val current: Int, val total: Int)

    sealed interface UiState {
        data object Idle : UiState
        data class Active(
            val target: Voucher,
            val phase: Phase,
            /** 一括更新中のみ。 */
            val progress: Progress?,
            /** 変わるたびに WebView で入力画面を開き直す。 */
            val loadKey: Int,
            /** 前回の送信で認証が通らなかったときの表示（サイトの文言があれば [retryMessage]）。 */
            val retrying: Boolean = false,
            val retryMessage: String? = null,
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

    /** WebView のページで起きた問題。 */
    enum class PageProblem { Network, Maintenance, Unexpected }

    private enum class Mode { Single, Batch, Official }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null

    /** 照会を始める準備（DB 読み込み）中のジョブ。この間に次の照会を受け付けないために使う。 */
    private var starting: Job? = null
    private var mode = Mode.Single
    private var queue: List<Voucher> = emptyList()
    private var index = 0
    private var succeeded = 0
    private var loadKey = 0

    /** 照会を始めた時点で規約に同意済みか（未同意なら照会自体を始めないので、通常は true）。 */
    var termsAgreed = false
        private set

    /** ネットワークエラー時の「再試行」で、中断した券から再開するための記録。 */
    private data class RetryPoint(val mode: Mode, val queue: List<Voucher>, val index: Int, val succeeded: Int)

    private var retryPoint: RetryPoint? = null

    val isActive: Boolean get() = _state.value is UiState.Active

    /** 照会中、または照会を始める準備中か。 */
    private val isBusy: Boolean get() = isActive || starting?.isActive == true

    /** 1枚を照会する。[official] のときは成功後に公式画面（保存した結果）を開く。 */
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
        val settings = settingsRepository.current()
        if (!settings.termsAgreed) {
            _events.send(Event.TermsRequired)
            return
        }
        termsAgreed = true
        retryPoint = null
        DebugLog.log("lookup", "start targets=${newQueue.size}")
        job?.cancel()
        mode = newMode
        queue = newQueue
        index = startIndex
        succeeded = startSucceeded
        startCurrent()
    }

    private fun currentProgress(): Progress? =
        if (mode == Mode.Batch) Progress(index + 1, queue.size) else null

    /** 今の券の入力画面を開く（WebView が [UiState.Active.loadKey] の変化を見て読み込む）。 */
    private fun startCurrent(retrying: Boolean = false, retryMessage: String? = null) {
        val target = queue.getOrNull(index) ?: return finish()
        loadKey++
        _state.value = UiState.Active(target, Phase.LoadingPage, currentProgress(), loadKey, retrying, retryMessage)
    }

    private fun updatePhase(from: Set<Phase>, to: Phase) {
        val active = _state.value as? UiState.Active ?: return
        if (active.phase !in from) return
        _state.value = active.copy(phase = to)
    }

    /** 入力画面に番号を入れ終え、人のチェックを待つ（送信しようとしてトークンが切れていた場合も戻る）。 */
    fun onInputReady() {
        DebugLog.log("lookup", "input ready")
        updatePhase(setOf(Phase.LoadingPage, Phase.Submitting), Phase.AwaitingCheck)
    }

    /** チェックが済み、送信ボタンを押した。 */
    fun onSubmitting() {
        DebugLog.log("lookup", "check completed, submitting")
        updatePhase(setOf(Phase.LoadingPage, Phase.AwaitingCheck), Phase.Submitting)
    }

    /** 送信後の結果ページ。[cookie] はバーコード画像の取得に使う。 */
    fun onResultPage(html: String, url: String, cookie: String?) {
        val active = _state.value as? UiState.Active ?: return
        if (active.phase != Phase.Submitting) return // 二重処理防止
        val target = active.target
        _state.value = active.copy(phase = Phase.Saving)
        job = viewModelScope.launch {
            try {
                handleOutcome(target, saveLookupResult(target, html, url, cookie))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.error("lookup", "save failed", e)
                _events.send(Event.Failed(R.string.error_unknown, batchProgress = batchProgress()))
                finish()
            }
        }
    }

    private suspend fun handleOutcome(target: Voucher, outcome: LookupOutcome) {
        when (outcome) {
            is LookupOutcome.Success -> {
                DebugLog.log("lookup", "outcome=success")
                succeeded++
                when (mode) {
                    // 一括更新では最後にまとめて知らせる
                    Mode.Batch -> Unit
                    Mode.Single -> _events.send(Event.Updated(target, outcome.result.balance))
                    Mode.Official -> {
                        _events.send(Event.Updated(target, outcome.result.balance))
                        _events.send(Event.OpenOfficial(target.id))
                    }
                }
                next(afterSubmit = true)
            }
            is LookupOutcome.CaptchaMismatch -> {
                // 自動ではやり直さず、入力画面を開き直して人にもう一度チェックしてもらう
                DebugLog.log("lookup", "outcome=check_rejected message=${outcome.message}")
                startCurrent(retrying = true, retryMessage = outcome.message)
            }
            is LookupOutcome.InvalidCode -> {
                DebugLog.log("lookup", "outcome=invalid_code")
                _events.send(Event.Failed(R.string.error_invalid_code, outcome.message))
                if (mode == Mode.Batch) next(afterSubmit = true) else finish()
            }
            LookupOutcome.Maintenance -> {
                DebugLog.log("lookup", "outcome=maintenance")
                _events.send(Event.Failed(R.string.error_maintenance, batchProgress = batchProgress()))
                finish()
            }
            LookupOutcome.ParseFailure -> {
                DebugLog.log("lookup", "outcome=parse_failure")
                _events.send(Event.Failed(R.string.error_parse, batchProgress = batchProgress()))
                finish()
            }
        }
    }

    /** WebView でページを開けなかった、または想定外のページだった。 */
    fun onPageProblem(problem: PageProblem) {
        if (!isActive) return
        DebugLog.log("lookup", "page problem=$problem")
        job?.cancel()
        val progress = batchProgress()
        val event = when (problem) {
            PageProblem.Network -> {
                retryPoint = RetryPoint(mode, queue, index, succeeded)
                Event.Failed(R.string.error_network, canRetry = true, batchProgress = progress)
            }
            PageProblem.Maintenance -> Event.Failed(R.string.error_maintenance, batchProgress = progress)
            PageProblem.Unexpected -> Event.Failed(R.string.error_parse, batchProgress = progress)
        }
        _events.trySend(event)
        finish()
    }

    /** 一括更新で今の券を飛ばして次へ進む。 */
    fun skip() {
        if (!isActive) return
        job?.cancel()
        job = viewModelScope.launch { next(afterSubmit = false) }
    }

    /** 照会を中止する（一括更新ではキュー全体を中止する）。 */
    fun cancel() {
        if (!isActive) return
        job?.cancel()
        finish(notify = mode == Mode.Batch && succeeded > 0)
    }

    /**
     * 次の券へ進む。一括更新で送信の直後なら、連続して送信しないよう [BATCH_INTERVAL_MILLIS] 待つ。
     * 待っている間は [Phase.Saving] のまま。
     */
    private suspend fun next(afterSubmit: Boolean) {
        // 待っている間にスキップされたら券を2枚進めないよう、待ってから進める
        if (afterSubmit && index + 1 < queue.size) delay(BATCH_INTERVAL_MILLIS)
        index++
        if (index >= queue.size) finish(notify = mode == Mode.Batch) else startCurrent()
    }

    private fun finish(notify: Boolean = false) {
        if (notify) _events.trySend(Event.BatchFinished(succeeded, queue.size))
        _state.value = UiState.Idle
        queue = emptyList()
    }

    /** 一括更新が途中で止まるとき、それまでに更新できた枚数（1枚も無ければ null）。 */
    private fun batchProgress(): Event.BatchFinished? =
        if (mode == Mode.Batch && succeeded > 0) Event.BatchFinished(succeeded, queue.size) else null

    companion object {
        /** 一括更新で、送信してから次の券の入力画面を開くまでの最短の間隔。 */
        const val BATCH_INTERVAL_MILLIS = 2_000L
    }
}
