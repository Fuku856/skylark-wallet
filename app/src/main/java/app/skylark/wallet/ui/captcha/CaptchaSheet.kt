package app.skylark.wallet.ui.captcha

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.skylark.wallet.R
import app.skylark.wallet.ui.common.KeyboardBottomSheet

/** CAPTCHA（画像の番号）を人が読んで入力するシート。 */
@Composable
fun CaptchaSheet(
    state: RefreshViewModel.UiState.Active,
    onSubmit: (String) -> Unit,
    onChangeImage: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    KeyboardBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        CaptchaContent(state, onSubmit, onChangeImage, onSkip, onCancel)
    }
}

@Composable
private fun CaptchaContent(
    state: RefreshViewModel.UiState.Active,
    onSubmit: (String) -> Unit,
    onChangeImage: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val phase = state.phase
    var input by rememberSaveable(state.target.id, state.captchaVersion) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val awaiting = phase is RefreshViewModel.Phase.AwaitingCaptcha
    val canSubmit = awaiting && RefreshViewModel.CAPTCHA_PATTERN.matches(input)

    // 画像の読み込みを待たずにキーボードを出し、シートとキーボードが一緒に上がるようにする。
    // 読み込み中も入力はできるが、送信は画像が表示されるまでできない
    LaunchedEffect(state.target.id, state.captchaVersion) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.captcha_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(
                        R.string.captcha_target,
                        state.target.memo.ifBlank { stringResource(R.string.voucher_default_name) },
                        state.target.last3,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.progress?.let {
                Text(
                    text = stringResource(R.string.captcha_progress, it.current, it.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // CAPTCHA 画像（大きく表示）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Color.White, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = when (phase) {
                    is RefreshViewModel.Phase.AwaitingCaptcha -> phase.image
                    is RefreshViewModel.Phase.Submitting -> phase.image
                    RefreshViewModel.Phase.Loading -> null
                },
                transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                label = "captcha",
            ) { image ->
                if (image == null) {
                    LoadingIndicator(Modifier.size(64.dp))
                } else {
                    Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.captcha_image_description),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .padding(12.dp),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onChangeImage,
            enabled = awaiting,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.captcha_change_image))
        }

        val mismatch = (phase as? RefreshViewModel.Phase.AwaitingCaptcha)?.takeIf { it.mismatch }
        AnimatedVisibility(visible = mismatch != null) {
            Text(
                text = mismatch?.message ?: stringResource(R.string.captcha_mismatch),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { value -> input = value.filter { it.isDigit() }.take(5) },
            label = { Text(stringResource(R.string.captcha_input_label)) },
            supportingText = { Text(stringResource(R.string.captcha_input_hint)) },
            singleLine = true,
            enabled = phase !is RefreshViewModel.Phase.Submitting,
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(input) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.progress != null) {
                TextButton(onClick = onSkip, enabled = phase !is RefreshViewModel.Phase.Submitting) {
                    Text(stringResource(R.string.captcha_skip))
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.captcha_abort)) }
            } else {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            Spacer(Modifier.weight(1f))
            if (phase is RefreshViewModel.Phase.Submitting) {
                ContainedLoadingIndicator(Modifier.size(48.dp))
            } else {
                Button(onClick = { onSubmit(input) }, enabled = canSubmit) {
                    Text(stringResource(R.string.captcha_submit))
                }
            }
        }
    }
}
