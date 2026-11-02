package com.mini.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Esqueleto de carga: una forma atenuada con un brillo suave que la recorre.
 * Se usa mientras cargan las apps o el clima, en vez de dejar un hueco vacio.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    return this.drawBehind {
        drawRect(Color(0x1AFFFFFF))
        val w = size.width
        val start = -w + 2f * w * x.value
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0x26FFFFFF), Color.Transparent),
                startX = start,
                endX = start + w
            )
        )
    }
}

/** Rectangulo con esquinas redondeadas y brillo animado. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())
}

/** Ficha vacia del mismo tamano que una ficha del dock. */
@Composable
fun SkeletonTile(scale: Float, corner: Int) {
    SkeletonBox(Modifier.size((144 * scale).dp, (84 * scale).dp), corner.dp)
}
