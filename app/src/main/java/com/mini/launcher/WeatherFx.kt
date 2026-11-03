package com.mini.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/** 0 nada, 1 lluvia, 2 nubes, 3 estrellas, 4 nieve segun el codigo de clima de Open-Meteo. */
fun fxKind(code: Int, night: Boolean): Int = when (code) {
    in 51..67, in 80..82, in 95..99 -> 1
    in 71..77, 85, 86 -> 4
    1, 2, 3, 45, 48 -> 2
    0 -> if (night) 3 else 0
    else -> 0
}

/**
 * Capa suave de clima sobre el fondo. Va apagada con enabled = false (ajuste apagado o modo ahorro):
 * en ese caso no dibuja ni anima nada.
 */
@Composable
fun WeatherFx(code: Int, night: Boolean, enabled: Boolean) {
    val kind = if (enabled) fxKind(code, night) else 0
    if (kind == 0) return
    val parts = remember(kind) {
        val r = Random(kind)
        List(if (kind == 1) 70 else if (kind == 4) 50 else if (kind == 3) 60 else 4) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 0.6f)
        }
    }
    val t by rememberInfiniteTransition(label = "fx").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (kind == 2) 60000 else if (kind == 3) 4000 else 2500, easing = LinearEasing), RepeatMode.Restart),
        label = "fxT"
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (kind) {
            1 -> for (p in parts) {
                val y = ((p[1] + t * (0.8f + p[2])) % 1f) * h
                val x = p[0] * w
                drawLine(Color(0x44BFD8FF), Offset(x, y), Offset(x - 6f, y + 28f * p[2]), 2f)
            }
            4 -> for (p in parts) {
                val y = ((p[1] + t * 0.3f * p[2]) % 1f) * h
                val x = (p[0] + 0.02f * kotlin.math.sin((t + p[1]) * 6.28f)) * w
                drawCircle(Color(0x66FFFFFF), 2.5f + 2f * p[2], Offset(x, y))
            }
            3 -> for (p in parts) {
                val a = 0.25f + 0.5f * kotlin.math.abs(kotlin.math.sin((t + p[2]) * 3.14f))
                drawCircle(Color.White.copy(alpha = a), 1.5f + 1.5f * p[2], Offset(p[0] * w, p[1] * h * 0.6f))
            }
            else -> for ((i, p) in parts.withIndex()) {
                val x = ((p[0] + t) % 1f) * (w + 600f) - 300f
                val y = h * (0.08f + 0.12f * i)
                drawOval(Color(0x1AFFFFFF), Offset(x, y), Size(520f * p[2] + 200f, 90f * p[2] + 40f))
            }
        }
    }
}
