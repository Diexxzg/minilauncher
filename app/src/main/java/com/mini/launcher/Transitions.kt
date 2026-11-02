package com.mini.launcher

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Profundidad de cada pantalla: decide hacia donde se desliza al entrar o salir. */
fun screenDepth(s: String): Int = when (s) {
    "home" -> 0
    "all", "inputs", "settings" -> 1
    else -> 2 // hidden, walls, edit, dock
}

/**
 * Transicion entre pantallas: fundido + deslizamiento corto.
 * Al avanzar (inicio -> todas las apps) entra desde la derecha; al volver, desde la izquierda.
 */
fun screenTransition(from: String, to: String, enabled: Boolean): ContentTransform {
    if (!enabled) return ContentTransform(EnterTransition.None, ExitTransition.None)
    val dir = if (screenDepth(to) >= screenDepth(from)) 1 else -1
    val enter = fadeIn(tween(240, delayMillis = 40)) +
        slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { w -> w / 16 * dir }
    val exit = fadeOut(tween(160)) +
        slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { w -> -(w / 16) * dir }
    return ContentTransform(enter, exit)
}
