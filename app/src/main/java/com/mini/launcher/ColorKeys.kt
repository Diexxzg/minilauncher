package com.mini.launcher

import androidx.compose.ui.input.key.Key

/**
 * Botones de color del control: 0 rojo, 1 verde, 2 amarillo, 3 azul; null si no es uno de ellos.
 * (Codigos de Android: PROG_RED 183, PROG_GREEN 184, PROG_YELLOW 185, PROG_BLUE 186.)
 */
fun colorKeyIndex(k: Key): Int? = when (k) {
    Key(183) -> 0
    Key(184) -> 1
    Key(185) -> 2
    Key(186) -> 3
    else -> null
}

/** Codigo de accion (ver COLOR_ACTIONS en Cfg.kt) del boton de color pulsado. */
fun colorKeyAction(cfg: Cfg, k: Key): Int? {
    val i = colorKeyIndex(k) ?: return null
    return cfg.colorAction(i)
}
