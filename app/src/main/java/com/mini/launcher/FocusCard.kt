package com.mini.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ultimo uso de cada app desde el launcher (se guarda en Prefs como pkg:milisegundos). */
fun Prefs.lastUsedMap(): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (part in getStr("lastUsed", "").split(",")) {
        val p = part.split(":")
        if (p.size == 2) {
            val v = p[1].toLongOrNull()
            if (v != null) out[p[0]] = v
        }
    }
    return out
}

/** Anota que la app se abrio ahora. Solo se guardan las 60 mas recientes. */
fun Prefs.markUsed(pkg: String): Map<String, Long> {
    val m = HashMap(lastUsedMap())
    m[pkg] = System.currentTimeMillis()
    val keep = m.entries.sortedByDescending { it.value }.take(60)
    putStr("lastUsed", keep.joinToString(",") { it.key + ":" + it.value })
    return keep.associate { it.key to it.value }
}

/** "Usada hace 5 min", "Usada ayer"... Si no hay registro: "Sin uso registrado". */
fun agoText(then: Long?, now: Long = System.currentTimeMillis()): String {
    if (then == null) return "Sin uso registrado"
    val min = ((now - then) / 60_000L).coerceAtLeast(0L)
    return when {
        min < 1L -> "Usada hace un momento"
        min < 60L -> "Usada hace " + min + " min"
        min < 24L * 60L -> "Usada hace " + (min / 60L) + " h"
        min < 48L * 60L -> "Usada ayer"
        else -> "Usada hace " + (min / (24L * 60L)) + " días"
    }
}

/**
 * Tarjeta bajo el dock: nombre de la app enfocada (tambien en las fichas con banner, que
 * no llevan texto), notificaciones sin leer y cuando se uso por ultima vez.
 * Siempre ocupa el mismo alto para que el inicio no salte al mover el foco.
 */
@Composable
fun FocusCard(app: AppInfo?, unread: Int, lastUsed: Long?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(30.dp),
        contentAlignment = Alignment.Center
    ) {
        if (app != null) {
            val extra = ArrayList<String>()
            if (unread > 0) extra.add(if (unread == 1) "1 sin leer" else unread.toString() + " sin leer")
            extra.add(agoText(lastUsed))
            val txt = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = Color.White)) {
                    append(app.label)
                }
                withStyle(SpanStyle(color = Color(0xCCFFFFFF), fontSize = 14.sp)) {
                    append("   ·   " + extra.joinToString("   ·   "))
                }
            }
            Text(
                txt,
                color = Color.White,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 8f)),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
