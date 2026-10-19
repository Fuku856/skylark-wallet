package com.example.skylarkwallet.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.example.skylarkwallet.ui.common.Format

/** 金額（円の単位なし）。値が変わったときに上下にロールさせる。 */
@Composable
fun RollingYen(value: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(motion.defaultSpatialSpec()) { if (up) it else -it } + fadeIn(motion.defaultEffectsSpec()))
                .togetherWith(
                    slideOutVertically(motion.defaultSpatialSpec()) { if (up) -it else it } +
                        fadeOut(motion.defaultEffectsSpec()),
                )
        },
        label = "yen",
        modifier = modifier,
    ) { shown ->
        Text(Format.yen(shown), style = style)
    }
}
