package com.mini.launcher.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MandoScreen(cn: Conn) {
    val scope = rememberCoroutineScope()
    var vol by remember { mutableStateOf(0.5f) }
    var mute by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    fun apply(v: Vol?) {
        if (v != null) {
            vol = v.pct / 100f
            mute = v.mute
        }
    }

    LaunchedEffect(cn.host, cn.token) {
        apply(withContext(Dispatchers.IO) { runCatching { cn.state() }.getOrNull() })
    }

    suspend fun key(n: String) {
        val r = withContext(Dispatchers.IO) { runCatching { cn.key(n) } }
        note = r.fold({ it ?: "" }, { "Sin respuesta de la TV." })
    }

    fun sendVol(arg: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { cn.vol(arg) } }
            if (r.isFailure) note = "Sin respuesta de la TV." else apply(r.getOrNull())
        }
    }

    Title("Mando de la TV", "Flechas y OK: solo dentro del launcher.")

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyBtn("↩", "Atrás", Modifier.weight(1f)) { scope.launch { key("back") } }
        KeyBtn("⌂", "Inicio", Modifier.weight(1f)) { scope.launch { key("home") } }
        KeyBtn("⊘", if (mute) "Activar" else "Silencio", Modifier.weight(1f)) { sendVol("d=mute") }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🔊", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text("Volumen", color = TXT, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(if (mute) "Silenciado" else (vol * 100).toInt().toString() + "%", color = ACCL, fontSize = 14.sp)
    }

    Glass(Modifier.fillMaxWidth(), radius = 30.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundKey("−") { sendVol("d=down") }
            GlassSlider(
                vol,
                Modifier.weight(1f).padding(horizontal = 4.dp),
                onChange = { vol = it },
                onDone = { sendVol("p=" + (it * 100).toInt()) }
            )
            RoundKey("+") { sendVol("d=up") }
        }
    }

    if (note.isNotEmpty()) Text(note, color = RED, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))

    Spacer(Modifier.height(6.dp))
    DPad { n -> key(n) }
    Text(
        "El volumen puede no responder si tu TV lo controla por HDMI o barra de sonido.",
        color = MUT, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun KeyBtn(glyph: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Glass(modifier.height(96.dp), radius = 24.dp, onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(glyph, color = TXT, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = TXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RoundKey(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF1A1823)).border(1.dp, EDGE, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = TXT, fontSize = 24.sp)
    }
}

private fun dirAt(o: Offset, w: Float): String? {
    val dx = o.x - w / 2
    val dy = o.y - w / 2
    val r = kotlin.math.sqrt(dx * dx + dy * dy)
    return when {
        r < w * 0.2f -> "ok"
        r > w * 0.5f -> null
        kotlin.math.abs(dx) > kotlin.math.abs(dy) -> if (dx > 0) "right" else "left"
        else -> if (dy > 0) "down" else "up"
    }
}

/** Cruceta circular: mantener una flecha pulsada la repite. OK se envía una sola vez. */
@Composable
private fun DPad(onKey: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val send = rememberUpdatedState(onKey)
    var pressed by remember { mutableStateOf<String?>(null) }
    BoxWithConstraints(
        Modifier.fillMaxWidth(0.9f).aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { o ->
                    val d = dirAt(o, size.width.toFloat())
                    if (d != null) {
                        pressed = d
                        val job = scope.launch {
                            send.value(d)
                            if (d != "ok") {
                                delay(450)
                                while (true) {
                                    send.value(d)
                                    delay(130)
                                }
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = null
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        val s = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val c = Offset(d / 2, d / 2)
            drawCircle(Color(0xFF0E0D14), radius = d / 2)
            drawCircle(Color(0xFF2C2540), radius = d / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            val rIn = d * 0.27f
            val rOut = d * 0.47f
            val mid = (rIn + rOut) / 2
            listOf("up" to -90f, "right" to 0f, "down" to 90f, "left" to 180f).forEach { (k, a) ->
                drawArc(
                    color = if (pressed == k) Color(0xFF4B3A8C) else Color(0xFF2B2931),
                    startAngle = a - 43f,
                    sweepAngle = 86f,
                    useCenter = false,
                    topLeft = Offset(c.x - mid, c.y - mid),
                    size = Size(mid * 2, mid * 2),
                    style = Stroke(width = rOut - rIn)
                )
            }
            drawCircle(Color(0xFF0A0910), radius = d * 0.235f)
        }
        Text("▲", color = Color(0xFFE6DDFF), fontSize = 22.sp, modifier = Modifier.offset(y = -(s * 0.37f)))
        Text("▼", color = Color(0xFFE6DDFF), fontSize = 22.sp, modifier = Modifier.offset(y = s * 0.37f))
        Text("◀", color = Color(0xFFE6DDFF), fontSize = 22.sp, modifier = Modifier.offset(x = -(s * 0.37f)))
        Text("▶", color = Color(0xFFE6DDFF), fontSize = 22.sp, modifier = Modifier.offset(x = s * 0.37f))
        Box(
            Modifier.size(s * 0.38f).clip(CircleShape)
                .background(if (pressed == "ok") Brush.verticalGradient(listOf(P1, P1)) else GRAD)
                .border(1.dp, Color(0x50FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OK", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Seleccionar", color = Color(0xFFE6DDFF), fontSize = 11.sp)
            }
        }
    }
}
