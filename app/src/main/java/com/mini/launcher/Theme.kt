package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
        AppTheme("Grafito", Color(0xFFE0E0E0), Color(0xFFA8A8A8), Color(0xCC202020), Color(0xCC141414), Color(0x55FFFFFF), Color(0xDDFFFFFF)),
        AppTheme("Aurora", Color(0xFF7CF5C8), Color(0xFF3FBF98), Color(0xCC10241F), Color(0xCC0A1713), Color(0x5560F0C0), Color(0xDDB5FBE3)),
        AppTheme("Cereza", Color(0xFFFF6B7A), Color(0xFFD63C4E), Color(0xCC261217), Color(0xCC170B0E), Color(0x55FF5266), Color(0xDDFFB0B8)),
        AppTheme("Hielo", Color(0xFFA8E1FF), Color(0xFF6FB6E6), Color(0xCC15212B), Color(0xCC0D151C), Color(0x558ED0FF), Color(0xDDD2EEFF)),
        AppTheme("Dorado", Color(0xFFFFD36B), Color(0xFFD9A63A), Color(0xCC272010), Color(0xCC17130A), Color(0x55FFC94D), Color(0xDDFFE6A8)),
        AppTheme("Neón verde", Color(0xFF39FF7A), Color(0xFF14B85A), Color(0xCC0D1A12), Color(0xCC08100B), Color(0x5539FF7A), Color(0xDD9CFFC0)),
        AppTheme("Neón violeta", Color(0xFFA78BFA), Color(0xFF7C5CF0), Color(0xCC15101F), Color(0xCC0D0A14), Color(0x55A78BFA), Color(0xDDD3C6FF)),
        AppTheme("Neón rojo", Color(0xFFFF3B4E), Color(0xFFC4182B), Color(0xCC1F0D10), Color(0xCC140709), Color(0x55FF3B4E), Color(0xDDFFA3AB))
    )

    var current by mutableStateOf(list[0])

    /** custom != 0: color de acento propio (ARGB) en lugar de uno de los temas de la lista. */
    fun set(i: Int, custom: Int = 0) {
        current = if (custom != 0) customTheme(custom) else list[i.coerceIn(0, list.lastIndex)]
    }

    /**
     * Indice del tema a usar. Con el tema automatico: Oceano de dia (7:00 a 18:59) y
     * Atardecer (calido) de noche; si no, el tema elegido.
     */
    fun pick(cfg: Cfg, hour: Int): Int =
        if (cfg.autoTheme) (if (hour in 7..18) 1 else 3) else cfg.theme
}

/** Tema armado a partir de un solo color de acento. */
fun customTheme(argb: Int): AppTheme {
    val c = Color(argb)
    return AppTheme(
        "Propio", c, lerp(c, Color.Black, 0.25f),
        Color(0xCC1B1D20), Color(0xCC121416),
        c.copy(alpha = 0.33f), lerp(c, Color.White, 0.5f).copy(alpha = 0.87f)
    )
}
