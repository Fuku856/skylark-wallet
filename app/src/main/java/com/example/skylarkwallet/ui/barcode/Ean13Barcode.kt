package com.example.skylarkwallet.ui.barcode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.EAN13Writer
import kotlin.math.floor

/**
 * JAN-13 を ZXing の [EAN13Writer] でモジュール列に変換し、Canvas に矩形で描く（ベクター描画）。
 * 背景は純白、左右に十分なクワイエットゾーンを取る。
 */
@Composable
fun Ean13Barcode(code: String, modifier: Modifier = Modifier) {
    val modules = remember(code) { encodeModules(code) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.White)
            .padding(vertical = 24.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            if (modules == null) return@Canvas
            // 左右に各 QUIET_ZONE モジュール分の余白を足して横幅に収める。
            // 読み取り精度のため、モジュール幅は整数ピクセルにそろえてにじみを防ぐ
            val totalModules = modules.size + QUIET_ZONE * 2
            val moduleWidth = floor(size.width / totalModules).coerceAtLeast(1f)
            var x = floor((size.width - modules.size * moduleWidth) / 2f)
            var i = 0
            while (i < modules.size) {
                if (modules[i]) {
                    var run = 1
                    while (i + run < modules.size && modules[i + run]) run++
                    drawRect(Color.Black, topLeft = Offset(x, 0f), size = Size(moduleWidth * run, size.height))
                    x += moduleWidth * run
                    i += run
                } else {
                    x += moduleWidth
                    i++
                }
            }
        }
        Text(
            text = code.chunkedForDisplay(),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, letterSpacing = 2.sp),
            color = Color.Black,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val QUIET_ZONE = 11

/** 13桁から黒白のモジュール列を作る（クワイエットゾーンは含まない）。不正なコードなら null。 */
private fun encodeModules(code: String): BooleanArray? = runCatching {
    val matrix = EAN13Writer().encode(code, BarcodeFormat.EAN_13, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    BooleanArray(matrix.width) { matrix.get(it, 0) }
}.getOrNull()

/** 「6 674577 439187」のように JAN の表記に合わせて区切る。 */
private fun String.chunkedForDisplay(): String =
    if (length == 13) "${substring(0, 1)} ${substring(1, 7)} ${substring(7)}" else this
