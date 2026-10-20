package com.example.skylarkwallet.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigationevent.NavigationEvent
import com.example.skylarkwallet.R
import com.example.skylarkwallet.domain.model.AppSettings
import com.example.skylarkwallet.ui.barcode.BarcodeScreen
import com.example.skylarkwallet.ui.barcode.BarcodeViewModel
import com.example.skylarkwallet.ui.captcha.CaptchaSheet
import com.example.skylarkwallet.ui.captcha.RefreshViewModel
import com.example.skylarkwallet.ui.common.Format
import com.example.skylarkwallet.ui.home.HomeScreen
import com.example.skylarkwallet.ui.onboarding.TermsScreen
import com.example.skylarkwallet.ui.onboarding.TermsWebViewScreen
import com.example.skylarkwallet.ui.settings.SettingsScreen
import com.example.skylarkwallet.ui.settings.SettingsViewModel
import com.example.skylarkwallet.ui.webview.OfficialViewModel
import com.example.skylarkwallet.ui.webview.OfficialWebViewScreen
import kotlinx.coroutines.launch

private const val PREDICTIVE_BACK_MILLIS = 200

/** 予測型戻るのプレビューに使う減速カーブ（Android のデザインガイドで SystemUI に合わせるとされる値）。 */
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private object Routes {
    const val TERMS = "terms"
    const val TERMS_WEB = "terms_web"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val BARCODE = "barcode?${BarcodeViewModel.ARG_ID}={${BarcodeViewModel.ARG_ID}}"
    const val OFFICIAL = "official/{${OfficialViewModel.ARG_ID}}"

    fun barcode(id: String?) = "barcode?${BarcodeViewModel.ARG_ID}=${id.orEmpty()}"
    fun official(id: String) = "official/$id"
}

@Composable
fun SkylarkRoot(settings: AppSettings, refreshViewModel: RefreshViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val refreshState by refreshViewModel.state.collectAsStateWithLifecycle()

    // 照会の結果を Snackbar や画面遷移に反映する
    LaunchedEffect(refreshViewModel) {
        refreshViewModel.events.collect { event ->
            when (event) {
                is RefreshViewModel.Event.Updated -> scope.launch {
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.refresh_updated,
                            event.voucher.memo.ifBlank { resources.getString(R.string.voucher_default_name) },
                            Format.yen(event.balance),
                        ),
                    )
                }
                is RefreshViewModel.Event.BatchFinished -> scope.launch {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.refresh_batch_finished, event.succeeded, event.total),
                    )
                }
                is RefreshViewModel.Event.Failed -> scope.launch {
                    val message = listOfNotNull(
                        resources.getString(event.message),
                        event.detail?.takeIf { it.isNotBlank() },
                        event.batchProgress?.let {
                            resources.getString(R.string.refresh_batch_finished, it.succeeded, it.total)
                        },
                    ).joinToString("\n")
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = if (event.canRetry) resources.getString(R.string.retry) else null,
                        duration = if (event.canRetry) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) refreshViewModel.retry()
                }
                is RefreshViewModel.Event.OpenOfficial -> navController.navigate(Routes.official(event.voucherId))
                RefreshViewModel.Event.TermsRequired -> navController.navigate(Routes.TERMS)
            }
        }
    }

    val motion = MaterialTheme.motionScheme
    val slideSpec = motion.defaultSpatialSpec<IntOffset>()
    val fadeSpec = motion.defaultEffectsSpec<Float>()
    // 予測型戻るではジェスチャーの進み具合がそのまま再生位置になり、指を離すと残りを一定速度で再生する。
    // 動き始めから縮んで見えるよう減速カーブを通し、離した後の残りがすぐ終わるよう全体を短くする
    val predictiveScaleSpec = tween<Float>(PREDICTIVE_BACK_MILLIS, easing = PredictiveBackEasing)
    val predictiveOffsetSpec = tween<IntOffset>(PREDICTIVE_BACK_MILLIS, easing = PredictiveBackEasing)
    // ジェスチャーの途中では透けないよう、終わり際（指を離した後）だけで消す
    val predictiveFadeSpec = tween<Float>(
        durationMillis = PREDICTIVE_BACK_MILLIS * 3 / 10,
        delayMillis = PREDICTIVE_BACK_MILLIS * 7 / 10,
        easing = LinearEasing,
    )

    // 開始画面は最初の表示時に決める（同意後に NavHost のグラフを作り直さないため）
    val startDestination = remember { if (settings.termsPrompted) Routes.HOME else Routes.TERMS }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(slideSpec) { it / 4 } + fadeIn(fadeSpec) },
        exitTransition = { fadeOut(fadeSpec) },
        popEnterTransition = { fadeIn(fadeSpec) },
        popExitTransition = { slideOutHorizontally(slideSpec) { it / 4 } + fadeOut(fadeSpec) },
        // 戻るジェスチャー中は、戻り先の画面を後ろに出したまま今の画面を縮め、指の動く向きへ少し寄せる
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { swipeEdge ->
            val direction = if (swipeEdge == NavigationEvent.EDGE_RIGHT) -1 else 1
            scaleOut(predictiveScaleSpec, targetScale = 0.9f) +
                slideOutHorizontally(predictiveOffsetSpec) { direction * it / 8 } +
                fadeOut(predictiveFadeSpec)
        },
    ) {
        composable(Routes.TERMS) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            // 初回起動では TERMS が開始画面で戻り先が無いため、pop せずに HOME へ置き換える
            val leave = {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.TERMS) { inclusive = true } }
                }
            }
            TermsScreen(
                alreadyAgreed = settings.termsAgreed,
                onOpenTerms = { navController.navigate(Routes.TERMS_WEB) },
                onAgree = {
                    settingsViewModel.setTermsAgreed(true)
                    leave()
                },
                onDecline = {
                    settingsViewModel.setTermsAgreed(false)
                    leave()
                },
            )
        }
        composable(Routes.TERMS_WEB) {
            TermsWebViewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                snackbarHostState = snackbarHostState,
                onRefreshOne = { refreshViewModel.refreshOne(it) },
                onRefreshAll = refreshViewModel::refreshAll,
                onOpenBarcode = { navController.navigate(Routes.barcode(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.BARCODE,
            arguments = listOf(
                navArgument(BarcodeViewModel.ARG_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            BarcodeScreen(
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onOpenSaved = { navController.navigate(Routes.official(it)) },
                onOpenLatest = { refreshViewModel.refreshOne(it, official = true) },
            )
        }
        composable(
            Routes.OFFICIAL,
            arguments = listOf(navArgument(OfficialViewModel.ARG_ID) { type = NavType.StringType }),
        ) {
            OfficialWebViewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTerms = { navController.navigate(Routes.TERMS) },
            )
        }
    }

    (refreshState as? RefreshViewModel.UiState.Active)?.let { active ->
        CaptchaSheet(
            state = active,
            onSubmit = refreshViewModel::submit,
            onChangeImage = refreshViewModel::changeCaptcha,
            onSkip = refreshViewModel::skip,
            onCancel = refreshViewModel::cancel,
        )
    }
}
