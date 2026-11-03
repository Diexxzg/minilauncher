package com.mini.launcher

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estilos de ficha del dock. */
val TILE_STYLES = listOf("Banner", "Banner 16:9", "Icono cuadrado", "Círculo")

/** Estilos de foco de las fichas del dock. */
val FOCUS_STYLES = listOf("Clásico (aro y brillo)", "Aro grueso", "Solo brillo", "Solo escala")

/** Patron de fecha para cada formato (se escribe con el idioma es-VE). */
fun datePattern(i: Int): String = when (i) {
    1 -> "EEE, d MMM"
    2 -> "dd/MM/yyyy"
    3 -> "yyyy-MM-dd"
    4 -> "d 'de' MMMM 'de' yyyy"
    else -> "EEEE, d 'de' MMMM"
}

/** Cada formato escrito con la fecha de hoy, para que el selector muestre un ejemplo real. */
fun dateFormatLabels(): List<String> {
    val now = Date()
    return (0..4).map { i ->
        SimpleDateFormat(datePattern(i), Locale.forLanguageTag("es-VE"))
            .format(now).replaceFirstChar { it.uppercase() }
    }
}

/** Ancho y alto base (dp, sin escala) de una ficha del dock segun su estilo. */
fun tileDims(style: Int): Pair<Int, Int> = when (style) {
    1 -> Pair(144, 81)
    2, 3 -> Pair(84, 84)
    else -> Pair(144, 84)
}
