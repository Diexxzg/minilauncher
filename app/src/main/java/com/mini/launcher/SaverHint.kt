package com.mini.launcher

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Por debajo de estos fotogramas por segundo se considera que la TV va lenta. */
private const val SLOW_FPS = 20

/** Ventanas lentas necesarias (cada una dura 1 s) antes de sugerir el modo ahorro. */
private const val SLOW_WINDOWS = 3

/**
 * Vigila los fotogramas por segundo solo mientras se usa el control (nunca en reposo).
 * Si varias ventanas de 1 s seguidas salen por debajo de SLOW_FPS llama a onSlow una vez.
 */
@Composable
fun SlowWatch(active: Boolean, lastInputMs: () -> Long, onSlow: () -> Unit) {
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var lows = 0
        while (true) {
            delay(1500)
            // Solo se mide si se acaba de pulsar una tecla: en reposo no hay nada que medir.
            if (SystemClock.elapsedRealtime() - lastInputMs() > 2000L) continue
            var frames = 0
            val start = withFrameNanos { it }
            var t = start
            while (t - start < 1_000_000_000L) {
                t = withFrameNanos { it }
                frames++
            }
            if (frames < SLOW_FPS) {
                lows++
            } else if (lows > 0) {
                lows--
            }
            if (lows >= SLOW_WINDOWS) {
                onSlow()
                return@LaunchedEffect
            }
        }
    }
}

