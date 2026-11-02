package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Tema de color: acento de los paneles, fondo del cristal y color del foco. */
class AppTheme(
    val name: String,
    val acc: Color,
    val accDim: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val focusBg: Color,
    val focusBorder: Color
)

object Themes {
    val list = listOf(
        AppTheme("Medianoche", Color(0xFF9FB4FF), Color(0xFF6F86D6), Color(0xCC1B1D20), Color(0xCC121416), Color(0x55FFFFFF), Color(0xDDFFFFFF)),
        AppTheme("Océano", Color(0xFF5CD6E6), Color(0xFF2FA4B8), Color(0xCC12212A), Color(0xCC0C1620), Color(0x553FC8E0), Color(0xDD8FEAF5)),
        AppTheme("Esmeralda", Color(0xFF6EE7A8), Color(0xFF38B67A), Color(0xCC12231B), Color(0xCC0C1712), Color(0x5540D890), Color(0xDD9AF2C6)),
        AppTheme("Atardecer", Color(0xFFFFB067), Color(0xFFE08A3C), Color(0xCC261A14), Color(0xCC17100C), Color(0x55FF9A4D), Color(0xDDFFCB9A)),
        AppTheme("Rosa", Color(0xFFFF8FC7), Color(0xFFE0629F), Color(0xCC261520), Color(0xCC170E14), Color(0x55FF6FB5), Color(0xDDFFB8DC)),
        AppTheme("Violeta", Color(0xFFC3A3FF), Color(0xFF9873E0), Color(0xCC1D1827), Color(0xCC130F1B), Color(0x55A98AF5), Color(0xDDD9C6FF)),
        AppTheme("Grafito", Color(0xFFE0E0E0), Color(0xFFA8A8A8), Color(0xCC202020), Color(0xCC141414), Color(0x55FFFFFF), Color(0xDDFFFFFF))
    )

    var current by mutableStateOf(list[0])

    fun set(i: Int) {
        current = list[i.coerceIn(0, list.lastIndex)]
    }
}
