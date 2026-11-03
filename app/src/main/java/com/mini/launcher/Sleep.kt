package com.mini.launcher

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/** Temporizador de sueno (30): al llegar a cero, el inicio pasa a pantalla negra o modo ambiente. */
class SleepTimer {
    private var endAt by mutableLongStateOf(0L)

    val active: Boolean get() = endAt > 0L

    fun start(mins: Int) {
        endAt = SystemClock.elapsedRealtime() + mins * 60_000L
    }

    fun cancel() {
        endAt = 0L
    }

    /** true una sola vez cuando se cumple el tiempo. */
    fun due(now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (endAt in 1..now) {
            endAt = 0L
            return true
        }
        return false
    }

    /** Minutos que faltan, redondeados hacia arriba (0 si no hay temporizador). */
    fun remainingMins(now: Long = SystemClock.elapsedRealtime()): Int =
        if (endAt <= 0L) 0 else (((endAt - now) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
}

val SLEEP_OPTIONS = listOf("Apagado", "15 minutos", "30 minutos", "60 minutos")
val SLEEP_MINS = listOf(0, 15, 30, 60)
val SLEEP_ACTIONS = listOf("Pantalla negra", "Modo ambiente")
