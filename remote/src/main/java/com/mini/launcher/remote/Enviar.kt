package com.mini.launcher.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun meta(ctx: Context, u: Uri): Pair<String, Long> {
    var name = "fondo"
    var size = -1L
    runCatching {
        ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
    }
    if (size <= 0) runCatching { ctx.contentResolver.openAssetFileDescriptor(u, "r")?.use { size = it.length } }
    if (!name.contains(".")) {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(ctx.contentResolver.getType(u))
        if (ext != null) name = "$name.$ext"
    }
    return Pair(name, size)
}

private fun fmt(b: Long): String = if (b >= 1_048_576) (b / 1_048_576).toString() + " MB" else (b / 1024).coerceAtLeast(1).toString() + " KB"

@Composable
internal fun EnviarScreen(cn: Conn) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var txt by rememberSaveable { mutableStateOf("") }
    var fUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fName by rememberSaveable { mutableStateOf("") }
    var fSize by rememberSaveable { mutableStateOf(0L) }
    var prog by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(true) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) {
            val m = meta(ctx, u)
            fUri = u
            fName = m.first
            fSize = m.second
            msg = ""
        }
    }

    fun show(r: Result<String?>, okText: String) {
        val e = r.getOrNull()
        good = r.isSuccess && e == null
        msg = if (r.isFailure) "Sin respuesta de la TV." else e ?: okText
    }

    Title("Enviar a la TV", "Elige qué quieres mandar.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip("Enlace", tab == 0, Modifier.weight(1f)) { tab = 0; msg = "" }
        Chip("Texto", tab == 1, Modifier.weight(1f)) { tab = 1; msg = "" }
        Chip("Fondo", tab == 2, Modifier.weight(1f)) { tab = 2; msg = "" }
    }

    Glass(Modifier.fillMaxWidth(), radius = 30.dp) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tab) {
                0 -> {
                    Box(Modifier.padding(top = 6.dp)) { IconBox("↗", 110.dp, 32.dp, 44.sp) }
                    Text("Enlace a la TV", color = TXT, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Text("Se abre en el navegador de la TV", color = MUT, fontSize = 14.sp)
                    GlassField(url, { url = it }, "https://…", glyph = "↗", keyboard = androidx.compose.ui.text.input.KeyboardType.Uri)
                    PrimaryBtn("Abrir en la TV", enabled = url.isNotBlank() && !busy) {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { cn.open(url) } }
                            busy = false
                            show(r, "Se abrió en la TV.")
                        }
                    }
                    Text("Se abre al instante en la TV.", color = ACCL, fontSize = 13.sp)
                }
                1 -> {
                    Box(Modifier.padding(top = 6.dp)) { IconBox("T", 110.dp, 32.dp, 44.sp) }
                    Text("Texto a la TV", color = TXT, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Text("Se escribe en la búsqueda del launcher", color = MUT, fontSize = 14.sp)
                    GlassField(txt, { txt = it }, "Escribe aquí", singleLine = false, minHeight = 120.dp)
                    PrimaryBtn("Enviar a la TV", enabled = txt.isNotBlank() && !busy) {
                        busy = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { cn.text(txt) } }
                            busy = false
                            show(r, "Texto enviado a la TV.")
                        }
                    }
                }
                else -> {
                    Box(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(32.dp)).clickable(enabled = !busy) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { IconBox("+", 110.dp, 32.dp, 48.sp) }
                    Text("Fondo de la TV", color = TXT, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Text("Foto o video de tu galería", color = MUT, fontSize = 14.sp)
                    if (fUri != null) {
                        val shape = RoundedCornerShape(22.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(Color0).border(1.dp, EDGE, shape).padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fName, color = TXT, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(fmt(fSize), color = MUT, fontSize = 13.sp)
                        }
                    }
                    if (busy) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Subiendo", color = TXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("$prog%", color = ACCL, fontSize = 14.sp)
                        }
                        GlassSlider(prog / 100f, Modifier.fillMaxWidth(), interactive = false)
                    }
                    PrimaryBtn("Subir fondo", enabled = fUri != null && !busy) {
                        val u = fUri ?: return@PrimaryBtn
                        busy = true
                        prog = 0
                        msg = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                runCatching {
                                    cn.upload(fName, fSize, { ctx.contentResolver.openInputStream(u)!! }) { p -> prog = p }
                                }
                            }
                            busy = false
                            show(r, "Fondo subido a la TV.")
                        }
                    }
                }
            }
            if (msg.isNotEmpty()) {
                Text(msg, color = if (good) TEAL else RED, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0xFF0E0D14)
