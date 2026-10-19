package com.example.skylarkwallet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.skylarkwallet.R
import com.example.skylarkwallet.domain.model.CodeNormalizer
import com.example.skylarkwallet.domain.model.MemoNormalizer
import com.example.skylarkwallet.domain.usecase.RegisterVoucherUseCase
import kotlinx.coroutines.launch

/**
 * 9桁のご優待番号を登録するシート。登録後はそのまま照会（CAPTCHA）へ進む。
 */
@Composable
fun AddCodeSheet(
    onDismiss: () -> Unit,
    onRegister: suspend (code: String, memo: String) -> RegisterVoucherUseCase.Result,
    onRegistered: (voucherId: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var code by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = CodeNormalizer.isValid(code) && !busy

    // シートの表示と同時にキーボードを出し、シートとキーボードが一緒に上がるようにする
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun register() {
        if (!ready) return
        busy = true
        scope.launch {
            when (val result = onRegister(code, memo)) {
                is RegisterVoucherUseCase.Result.Registered -> onRegistered(result.voucher.id)
                is RegisterVoucherUseCase.Result.Duplicate -> error = R.string.add_error_duplicate
                RegisterVoucherUseCase.Result.InvalidFormat -> error = R.string.add_error_format
            }
            busy = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.add_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = CodeNormalizer.normalize(it).take(CodeNormalizer.CODE_LENGTH)
                    error = null
                },
                label = { Text(stringResource(R.string.add_code_label)) },
                supportingText = {
                    Text(
                        error?.let { stringResource(it) }
                            ?: stringResource(R.string.add_code_count, code.length, CodeNormalizer.CODE_LENGTH),
                    )
                },
                isError = error != null,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                // Password にするとパスワードマネージャがログインフォームと見なし、次のメモ欄に保存済みの値を入れることがある
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()
                                .orEmpty()
                            code = CodeNormalizer.normalize(text).take(CodeNormalizer.CODE_LENGTH)
                            error = null
                        }
                    }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.add_paste))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = MemoNormalizer.sanitizeInput(it) },
                label = { Text(stringResource(R.string.add_memo_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { register() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { register() }, enabled = ready) {
                    Text(stringResource(R.string.add_submit))
                }
            }
        }
    }
}
