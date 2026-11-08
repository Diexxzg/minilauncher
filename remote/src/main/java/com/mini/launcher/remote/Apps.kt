package com.mini.launcher.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TILES = listOf(
    0xFFD23A3A, 0xFF2FA866, 0xFF3A7BD5, 0xFFE0902F, 0xFF2BB5C8, 0xFFD6B22E, 0xFF9B5DE0, 0xFFE0629F
)

@Composable
internal fun AppsScreen(cn: Conn) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<TvApp>?>(null) }
    var err by remember { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var favs by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(cn.host, cn.token) {
        val r = withContext(Dispatchers.IO) { runCatching { cn.apps() } }
        apps = r.getOrNull()
        err = if (r.isFailure) "Sin respuesta de la TV." else if (r.getOrNull() == null) "La TV no dio la lista de apps." else ""
    }

    Title("Apps de la TV", "Toca una app para abrirla en la TV.")
    GlassField(q, { q = it }, "Buscar app", glyph = "🔍")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip("Favoritas", favs, Modifier.padding(0.dp).weight(1f)) { favs = true }
        Chip("Todas", !favs, Modifier.weight(1f)) { favs = false }
    }
    if (err.isNotEmpty()) Text(err, color = RED, fontSize = 14.sp)
    val shown = (apps ?: emptyList())
        .filter { (!favs || it.fav) && it.label.contains(q.trim(), ignoreCase = true) }
        .sortedBy { it.label.lowercase() }
    if (apps != null && shown.isEmpty()) {
        Text(if (favs && q.isBlank()) "No hay favoritas. Mira en «Todas»." else "Nada coincide.", color = MUT, fontSize = 14.sp)
    }
    shown.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { a ->
                val c = Color(TILES[(a.pkg.hashCode() and 0x7fffffff) % TILES.size])
                Glass(Modifier.weight(1f).aspectRatio(1f), radius = 24.dp, onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { cn.launch(a.pkg) } }
                        err = r.fold({ it ?: "" }, { "Sin respuesta de la TV." })
                    }
                }) {
                    Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(
                            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.6f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.label.take(1).uppercase(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(a.label, color = TXT, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
