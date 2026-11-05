package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Las apps mas abiertas de la semana: (nombre, aperturas), de mayor a menor. */
fun topWeek(prefs: Prefs, apps: List<AppInfo>, max: Int = 6): List<Pair<String, Int>> {
    val names = apps.associate { it.pkg to it.label }
    return prefs.weekUsage().entries
        .filter { names.containsKey(it.key) && it.value > 0 }
        .sortedByDescending { it.value }
        .take(max)
        .map { names.getValue(it.key) to it.value }
}

/** Apps instaladas que abriste alguna vez pero no en los ultimos `days` dias (las mas antiguas primero). */
fun staleApps(prefs: Prefs, apps: List<AppInfo>, days: Int = 30, max: Int = 3): List<Pair<String, Int>> {
    val now = System.currentTimeMillis()
    val last = prefs.lastUsedMap()
    return apps.mapNotNull { a ->
        val t = last[a.pkg] ?: return@mapNotNull null
        val d = ((now - t) / 86_400_000L).toInt()
        if (d >= days) a.label to d else null
    }.sortedByDescending { it.second }.take(max)
}

/** Grafica de barras horizontales sencilla; no recibe foco (es solo lectura). */
@Composable
fun UsageChart(items: List<Pair<String, Int>>, color: Color) {
    if (items.isEmpty()) {
        Text("Aún no hay aperturas esta semana.", color = Color(0xFFB0B8C8), fontSize = 14.sp)
        return
    }
    val top = items.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((name, n) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name, color = Color.White, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp)
                )
                Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0x22FFFFFF))) {
                    Box(
                        Modifier.fillMaxWidth(n.toFloat() / top).height(14.dp)
                            .clip(RoundedCornerShape(7.dp)).background(color)
                    )
                }
                Text(n.toString(), color = Color(0xFFB0B8C8), fontSize = 13.sp, modifier = Modifier.width(36.dp))
            }
        }
    }
}
