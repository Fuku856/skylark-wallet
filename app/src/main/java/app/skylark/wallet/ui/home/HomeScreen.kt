package app.skylark.wallet.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.skylark.wallet.ui.settings.SecretSettingsDialog
import app.skylark.wallet.ui.settings.SettingsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.skylark.wallet.R
import app.skylark.wallet.domain.model.MemoNormalizer
import app.skylark.wallet.domain.model.Voucher
import app.skylark.wallet.ui.common.Format
import app.skylark.wallet.ui.lock.BiometricAuth
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    snackbarHostState: SnackbarHostState,
    onRefreshOne: (voucherId: String) -> Unit,
    onRefreshAll: () -> Unit,
    onOpenBarcode: (voucherId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingUnreadable by rememberSaveable { mutableStateOf(false) }
    var showSecretSettings by rememberSaveable { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val resources = LocalResources.current
    // MainActivity は FragmentActivity なので必ずキャストできる
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val reveal: CodeRevealViewModel = viewModel(viewModelStoreOwner = activity)
    // アプリを離れたら閉じる（見るたびに認証する）
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { reveal.voucherId = null }
    val revealCode: (String) -> Unit = { id ->
        if (!BiometricAuth.canAuthenticate(context)) {
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.code_auth_unavailable)) }
        } else {
            BiometricAuth.authenticate(
                activity = activity,
                title = resources.getString(R.string.action_show_code),
                subtitle = resources.getString(R.string.code_prompt_subtitle),
                onSuccess = { reveal.voucherId = id },
                onError = { code, message ->
                    if (code !in BiometricAuth.CANCEL_ERRORS) scope.launch { snackbarHostState.showSnackbar(message.toString()) }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = onOpenSettings,
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showSecretSettings = true
                                },
                            )
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefreshAll,
                modifier = Modifier.fillMaxSize(),
            ) {
                VoucherList(
                    state = state,
                    onRefreshOne = onRefreshOne,
                    onOpenBarcode = onOpenBarcode,
                    onEditMemo = { editing = it },
                    onDelete = { deleting = it },
                    onDeleteUnreadable = { deletingUnreadable = true },
                    onRevealCode = revealCode,
                )
            }

            // よく使うバーコードは FAB に分け、追加・更新はツールバーにまとめる
            val toolbarModifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset)
            val toolbarContent: @Composable RowScope.() -> Unit = {
                TextButton(onClick = { showAdd = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_add))
                }
                TextButton(onClick = onRefreshAll, enabled = state.active.isNotEmpty()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_refresh))
                }
            }
            // FAB は無効にできないので、表示できるバーコードが無いときは出さない
            if (state.hasBarcode) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    floatingActionButton = {
                        FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = { onOpenBarcode(null) }) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = stringResource(R.string.action_barcode))
                        }
                    },
                    modifier = toolbarModifier,
                    content = toolbarContent,
                )
            } else {
                HorizontalFloatingToolbar(expanded = true, modifier = toolbarModifier, content = toolbarContent)
            }
        }
    }

    if (showSecretSettings) {
        SecretSettingsDialog(
            autoCaptchaEnabled = appSettings?.autoCaptcha ?: false,
            onToggleAutoCaptcha = settingsViewModel::setAutoCaptcha,
            onDismiss = { showSecretSettings = false },
        )
    }

    if (showAdd) {
        AddCodeSheet(
            onDismiss = { showAdd = false },
            onRegister = viewModel::register,
            onRegistered = { id ->
                showAdd = false
                onRefreshOne(id)
            },
        )
    }

    val all = state.active + state.used
    editing?.let { id ->
        all.firstOrNull { it.id == id }?.let { voucher ->
            MemoDialog(
                initial = voucher.memo,
                onDismiss = { editing = null },
                onSave = {
                    viewModel.updateMemo(id, it)
                    editing = null
                },
            )
        }
    }
    reveal.voucherId?.let { id ->
        all.firstOrNull { it.id == id }?.let { voucher ->
            CodeDialog(voucher, onDismiss = { reveal.voucherId = null })
        }
    }
    if (deletingUnreadable) {
        AlertDialog(
            onDismissRequest = { deletingUnreadable = false },
            title = { Text(stringResource(R.string.unreadable_delete_title)) },
            text = { Text(stringResource(R.string.unreadable_delete_message, state.unreadable)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUnreadable()
                    deletingUnreadable = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deletingUnreadable = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { id ->
        all.firstOrNull { it.id == id }?.let { voucher ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(R.string.delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.delete_message,
                            voucher.memo.ifBlank { stringResource(R.string.voucher_default_name) },
                            voucher.last3,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(id)
                        deleting = null
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun VoucherList(
    state: HomeViewModel.UiState,
    onRefreshOne: (String) -> Unit,
    onOpenBarcode: (String?) -> Unit,
    onEditMemo: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteUnreadable: () -> Unit,
    onRevealCode: (String) -> Unit,
) {
    var usedExpanded by rememberSaveable { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme

    // 読み込み直後は総残高もカードの残高も 0 円で描き、次のフレームで一斉に実際の値へロールさせる
    var revealed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) revealed = true }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") { HeroHeader(if (revealed) state.total else 0, state.lastUpdatedAt) }

        if (state.unreadable > 0) {
            item(key = "unreadable") { UnreadableNotice(state.unreadable, onDeleteUnreadable) }
        }

        if (!state.loading && state.active.isEmpty() && state.used.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }
        if (state.loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }

        items(state.active, key = { it.id }) { voucher ->
            VoucherCard(
                voucher = voucher,
                now = state.now,
                revealed = revealed,
                onRefresh = { onRefreshOne(voucher.id) },
                onShowBarcode = { onOpenBarcode(voucher.id) },
                onEditMemo = { onEditMemo(voucher.id) },
                onDelete = { onDelete(voucher.id) },
                onRevealCode = { onRevealCode(voucher.id) },
                modifier = Modifier.animateItem(),
            )
        }

        if (state.used.isNotEmpty()) {
            item(key = "used-header") {
                val rotation by animateFloatAsState(if (usedExpanded) 180f else 0f, motion.defaultSpatialSpec())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { usedExpanded = !usedExpanded }
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .animateItem(),
                ) {
                    Text(
                        stringResource(R.string.section_used, state.used.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
                }
            }
            item(key = "used-list") {
                AnimatedVisibility(
                    visible = usedExpanded,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.used.forEach { voucher ->
                            VoucherCard(
                                voucher = voucher,
                                now = state.now,
                                revealed = revealed,
                                onRefresh = { onRefreshOne(voucher.id) },
                                onShowBarcode = { onOpenBarcode(voucher.id) },
                                onEditMemo = { onEditMemo(voucher.id) },
                                onDelete = { onDelete(voucher.id) },
                                onRevealCode = { onRevealCode(voucher.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(total: Int, lastUpdatedAt: Long?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.hero_label), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingYen(total, MaterialTheme.typography.displayLarge)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.yen_unit), style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                text = lastUpdatedAt?.let { stringResource(R.string.hero_updated, Format.dateTime(it)) }
                    ?: stringResource(R.string.hero_not_updated),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 復号できない券があることを知らせる。端末の暗号鍵が無効になると、保存した番号は二度と読めない。 */
@Composable
private fun UnreadableNotice(count: Int, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.unreadable_message, count), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.unreadable_delete_action))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MemoDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var memo by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit_memo)) },
        text = {
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = MemoNormalizer.sanitizeInput(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.add_memo_label)) },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(memo) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 9桁のご優待番号を表示する。ダイアログは別ウィンドウなので、ここで FLAG_SECURE を付けて映り込みを防ぐ。 */
@Composable
private fun CodeDialog(voucher: Voucher, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.code_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(voucher.memo.ifBlank { stringResource(R.string.voucher_default_name) })
                Text(
                    Format.code(voucher.code9),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
