package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

/** Texto del ultimo Release de GitHub (llamar fuera del hilo principal). Null si no hay red. */
fun fetchReleaseNotes(): Pair<String, String>? {
    val raw = httpGet("https://api.github.com/repos/Diexxzg/minilauncher/releases/latest") ?: return null
    return try {
        val o = JSONObject(raw)
        val name = o.optString("name").ifBlank { o.optString("tag_name") }
        val body = o.optString("body").ifBlank { "Sin notas." }
        name to body
    } catch (e: Exception) {
        null
    }
}

/** Pantalla "Novedades": el texto se recorre con Arriba y Abajo; OK o Atras cierran. */
@Composable
fun WhatsNewDialog(title: String, body: String, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .heightIn(max = 460.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Novedades · $title", color = Color.White, fontSize = 20.sp)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // Los textos del Release usan "- " al inicio de cada linea: se muestran como puntos.
                for (line in body.lines()) {
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("##")) continue
                    Text(
                        if (t.startsWith("- ")) "• " + t.drop(2) else t,
                        color = Color(0xE6FFFFFF), fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
            ActionRow("Cerrar", Modifier.autoFocus()) { onClose() }
        }
    }
}

