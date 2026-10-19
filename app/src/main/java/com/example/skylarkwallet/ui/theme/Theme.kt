package com.example.skylarkwallet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightFallback = expressiveLightColorScheme().copy(
    primary = Color(0xFF2E6B30),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1A9),
    onPrimaryContainer = Color(0xFF00210A),
    tertiary = Color(0xFFB0501E),
    tertiaryContainer = Color(0xFFFFDBCB),
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF98D58F),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF14521B),
    onPrimaryContainer = Color(0xFFB4F1A9),
    tertiary = Color(0xFFFFB693),
    tertiaryContainer = Color(0xFF8A3A0A),
)

/** Material 3 Expressive テーマ。Android 12 以降は壁紙由来のダイナミックカラーを使う。 */
@Composable
fun SkylarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFallback
        else -> LightFallback
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = SkylarkTypography,
        content = content,
    )
}
