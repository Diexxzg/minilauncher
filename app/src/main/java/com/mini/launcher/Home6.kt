package com.mini.launcher

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Paquete 6 (Hogar y utilidades): tarjeta de visitas, lista de compras, ruido para dormir y modo facil.

private fun visitQr(text: String): BitMatrix? =
    try {
        val h = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0)
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, h)
    } catch (e: Exception) {
        null
    }

@Composable
internal fun QrBox(text: String, sizeDp: Int) {
    val m = remember(text) { visitQr(text) }
    if (m != null) {
        Box(Modifier.size(sizeDp.dp).background(Color.White).padding(10.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = m.width
                val cell = size.width / n
                for (y in 0 until n) for (x in 0 until n) {
                    if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}

/** Boton redondeado con foco visible (contorno grueso), sin animaciones. */
@Composable
internal fun Pill(text: String, sel: Boolean, mod: Modifier = Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val acc = Themes.current.acc
    val shape = RoundedCornerShape(22.dp)
    Box(
        mod
            .clip(shape)
            .background(if (sel) acc else Color(0x22FFFFFF))
            .border(if (f) 3.dp else 1.dp, if (f) Color.White else Color(0x55FFFFFF), shape)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (sel) Color(0xFF10131E) else Color.White, fontSize = 15.sp)
    }
}

// ---------- Tarjeta de visitas ----------

private fun wifiEsc(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

@Composable
fun VisitorCardDialog(prefs: Prefs, onClose: () -> Unit) {
    var ssid by remember { mutableStateOf(prefs.getStr("vSsid", "")) }
    var pass by remember { mutableStateOf(prefs.getStr("vPass", "")) }
    var show by remember { mutableStateOf(false) }
    var edit by remember { mutableIntStateOf(0) }
    ExtraDialog("Tarjeta de visitas", onClose, 680, scroll = true) {
        if (ssid.isEmpty()) {
            Text("Escribe el nombre de tu red Wi-Fi para generar el código QR.", color = Color(0xE6FFFFFF), fontSize = 15.sp)
        } else {
            val code = "WIFI:T:" + (if (pass.isEmpty()) "nopass" else "WPA") + ";S:" + wifiEsc(ssid) + ";P:" + wifiEsc(pass) + ";;"
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                QrBox(code, 210)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bienvenido", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Escanea para conectarte al Wi-Fi", color = Color(0xB3FFFFFF), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Red", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                    Text(ssid, color = Themes.current.acc, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Contraseña", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                    Text(
                        if (pass.isEmpty()) "Sin contraseña" else if (show) pass else "••••••••",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        ActionRow(if (show) "Ocultar contraseña" else "Mostrar contraseña") { show = !show }
        ActionRow("Cambiar nombre de la red") { edit = 1 }
        ActionRow("Cambiar contraseña") { edit = 2 }
        ActionRow("Cerrar") { onClose() }
    }
    if (edit == 1) {
        TextEditDialog("Nombre de la red", ssid, "Tal como aparece en el Wi-Fi", 64, { ssid = it; prefs.putStr("vSsid", it); edit = 0 }, { edit = 0 })
    }
    if (edit == 2) {
        TextEditDialog("Contraseña del Wi-Fi", pass, "Déjala vacía si la red es abierta", 64, { pass = it; prefs.putStr("vPass", it); edit = 0 }, { edit = 0 })
    }
}

// ---------- Lista de compras ----------

/** Lista guardada en Prefs ("shop"): una linea por producto, "0|texto" pendiente o "1|texto" listo. */
object Shop {
    private const val K = "shop"

    @Synchronized
    fun items(p: Prefs): List<Pair<Boolean, String>> =
        p.getStr(K, "").split("\n").filter { it.length > 2 }.map { (it[0] == '1') to it.substring(2) }

    @Synchronized
    fun raw(p: Prefs): String = items(p).joinToString("\n") { (if (it.first) "1|" else "0|") + it.second }

    private fun save(p: Prefs, l: List<Pair<Boolean, String>>) {
        p.putStr(K, l.joinToString("\n") { (if (it.first) "1|" else "0|") + it.second })
    }

    @Synchronized
    fun add(p: Prefs, t: String) {
        val s = t.replace("\n", " ").trim().take(60)
        if (s.isEmpty()) return
        save(p, items(p) + (false to s))
    }

    @Synchronized
    fun toggle(p: Prefs, i: Int) {
        val l = items(p).toMutableList()
        if (i !in l.indices) return
        l[i] = (!l[i].first) to l[i].second
        save(p, l)
    }

    @Synchronized
    fun remove(p: Prefs, i: Int) {
        val l = items(p).toMutableList()
        if (i !in l.indices) return
        l.removeAt(i)
        save(p, l)
    }

    @Synchronized
    fun clearDone(p: Prefs) {
        save(p, items(p).filter { !it.first })
    }
}

@Composable
fun ShoppingDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val holder = remember { arrayOfNulls<PhoneServer>(1) }
    // El servidor del celular vive solo mientras este cuadro esta abierto.
    LaunchedEffect(Unit) {
        val ui = Handler(Looper.getMainLooper())
        val s = PhoneServer(ctx.applicationContext, prefs, { _ -> }, { _ -> }, { }, { ui.post { tick++ } })
        holder[0] = s
        val ok = withContext(Dispatchers.IO) { s.start() }
        if (ok && !s.address().startsWith("sin red")) url = "http://" + s.address() + "/?k=" + s.code
    }
    DisposableEffect(Unit) {
        onDispose { holder[0]?.stop() }
    }
    val items = remember(tick) { Shop.items(prefs) }
    ExtraDialog("Lista de compras", onClose, 760, scroll = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (items.isEmpty()) Text("La lista está vacía.", color = Color(0xB3FFFFFF), fontSize = 15.sp)
                items.forEachIndexed { i, p ->
                    ActionRow((if (p.first) "✓ " else "☐ ") + p.second) {
                        Shop.toggle(prefs, i)
                        tick++
                    }
                }
            }
            if (url.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QrBox(url, 190)
                    Text("Escanea y agrega productos desde el celular", color = Color(0xB3FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(190.dp))
                }
            }
        }
        ActionRow("Agregar producto") { adding = true }
        ActionRow("Quitar los marcados") {
            Shop.clearDone(prefs)
            tick++
        }
        ActionRow("Cerrar") { onClose() }
    }
    if (adding) {
        TextEditDialog("Nuevo producto", "", "", 60, { Shop.add(prefs, it); adding = false; tick++ }, { adding = false })
    }
}

// ---------- Ruido para dormir ----------

/** Ruido generado en la TV (sin archivos): 0 lluvia, 1 marron, 2 blanco. Sigue sonando al cerrar el cuadro. */
object Noise {
    @Volatile
    var playing = false
        private set
    @Volatile
    private var track: AudioTrack? = null
    private val ui = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stop() }

    @Synchronized
    fun start(kind: Int, vol: Float, minutes: Int) {
        stop()
        val rate = 22050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return
        }
        t.setVolume(vol)
        track = t
        playing = true
        if (minutes > 0) ui.postDelayed(stopper, minutes * 60_000L)
        try {
            t.play()
        } catch (e: Exception) {
            stop()
            return
        }
        Thread { loop(t, kind) }.start()
    }

    @Synchronized
    fun stop() {
        ui.removeCallbacks(stopper)
        playing = false
        track = null
    }

    fun setVolume(v: Float) {
        try {
            track?.setVolume(v)
        } catch (e: Exception) {
        }
    }

    private fun loop(t: AudioTrack, kind: Int) {
        val buf = ShortArray(2048)
        val r = java.util.Random()
        var last = 0.0
        var lp = 0.0
        var drop = 0.0
        try {
            while (playing && track === t) {
                for (i in buf.indices) {
                    val w = r.nextDouble() * 2 - 1
                    var v: Double
                    when (kind) {
                        0 -> {
                            lp += 0.5 * (w - lp)
                            if (r.nextInt(3000) == 0) drop = w * 0.8
                            drop *= 0.97
                            v = lp * 1.3 + drop
                        }
                        1 -> {
                            last = (last + 0.02 * w) / 1.02
                            v = last * 3.5
                        }
                        else -> v = w * 0.5
                    }
                    if (v > 1.0) v = 1.0
                    if (v < -1.0) v = -1.0
                    buf[i] = (v * 24000).toInt().toShort()
                }
                t.write(buf, 0, buf.size)
            }
        } catch (e: Exception) {
        } finally {
            try {
                t.stop()
                t.release()
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
fun SleepNoiseDialog(prefs: Prefs, onClose: () -> Unit) {
    var kind by remember { mutableIntStateOf(prefs.getInt("nzKind", 1)) }
    var vol by remember { mutableIntStateOf(prefs.getInt("nzVol", 5)) }
    var mins by remember { mutableIntStateOf(prefs.getInt("nzMin", 30)) }
    var on by remember { mutableStateOf(Noise.playing) }
    var black by remember { mutableStateOf(false) }
    val kinds = listOf("Lluvia", "Ruido marrón", "Ruido blanco")
    val timers = listOf(15, 30, 60, 0)
    fun restart() {
        if (Noise.playing) Noise.start(kind, vol / 10f, mins)
    }
    ExtraDialog("Ruido para dormir", onClose, 620, scroll = true) {
        Text("Sonido", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            kinds.forEachIndexed { i, n ->
                Pill(n, kind == i) {
                    kind = i
                    prefs.putInt("nzKind", i)
                    restart()
                }
            }
        }
        Text("Volumen", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("−", false) {
                vol = maxOf(1, vol - 1)
                prefs.putInt("nzVol", vol)
                Noise.setVolume(vol / 10f)
            }
            Text("" + (vol * 10) + " %", color = Color.White, fontSize = 18.sp)
            Pill("+", false) {
                vol = minOf(10, vol + 1)
                prefs.putInt("nzVol", vol)
                Noise.setVolume(vol / 10f)
            }
        }
        Text("Apagar en", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            timers.forEach { m ->
                Pill(if (m == 0) "Sin límite" else "" + m + " min", mins == m) {
                    mins = m
                    prefs.putInt("nzMin", m)
                    restart()
                }
            }
        }
        ActionRow(if (on) "Detener" else "Iniciar (pantalla en negro)") {
            if (on) {
                Noise.stop()
                on = false
            } else {
                Noise.start(kind, vol / 10f, mins)
                on = Noise.playing
                black = on
            }
        }
        Text("Con «Iniciar» la pantalla queda en negro; Atrás la vuelve a mostrar y el sonido sigue.", color = Color(0x99FFFFFF), fontSize = 12.sp)
        ActionRow("Cerrar") { onClose() }
    }
    if (black) {
        Dialog(onDismissRequest = { black = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

// ---------- Modo facil ----------

@Composable
private fun EasyCard(label: String, mod: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val acc = Themes.current.acc
    val shape = RoundedCornerShape(36.dp)
    Column(
        mod
            .clip(shape)
            .background(if (f) Color(0xFF262B3A) else Color(0xFF1B1D20))
            .border(if (f) 6.dp else 2.dp, if (f) acc else Color(0x55FFFFFF), shape)
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(130.dp).clip(CircleShape).background(if (f) acc else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(label.take(1).uppercase(), color = if (f) Color(0xFF10131E) else Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(label, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Pantalla completa con tres botones enormes. Para salir: mantener Atras 3 segundos. */
@Composable
private fun EasyScreen(prefs: Prefs, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var downAt by remember { mutableLongStateOf(0L) }
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D12))
                .padding(48.dp)
                .onPreviewKeyEvent { e ->
                    if (e.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_BACK) {
                        false
                    } else {
                        val now = SystemClock.uptimeMillis()
                        if (e.type == KeyEventType.KeyDown) {
                            if (downAt == 0L) downAt = now
                            if (now - downAt >= 3000L) {
                                downAt = 0L
                                onExit()
                            }
                        } else if (e.type == KeyEventType.KeyUp) {
                            val st = downAt
                            downAt = 0L
                            if (st != 0L && now - st >= 3000L) onExit()
                        }
                        true
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Modo fácil", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                for (i in 0..2) {
                    val pk = prefs.getStr("easy" + i, "")
                    val label = AppCache.apps.firstOrNull { it.pkg == pk }?.label ?: "Sin elegir"
                    val m = if (i == 0) Modifier.weight(1f).fillMaxHeight().autoFocus() else Modifier.weight(1f).fillMaxHeight()
                    EasyCard(label, m) {
                        val it2 = if (pk.isEmpty()) null else ctx.packageManager.getLaunchIntentForPackage(pk)
                        if (it2 != null) {
                            it2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(it2)
                        } else {
                            toast(ctx, "Elige una app para este botón en Configuración.")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Mantén Atrás 3 segundos para salir", color = Color(0xB3FFFFFF), fontSize = 22.sp)
        }
    }
}

@Composable
fun EasyModeDialog(prefs: Prefs, onClose: () -> Unit) {
    var pick by remember { mutableIntStateOf(-1) }
    var run by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    ExtraDialog("Modo fácil", onClose, 560, scroll = true) {
        Text("Tres botones enormes que abren las apps que elijas. Para salir: mantén Atrás 3 segundos.", color = Color(0xE6FFFFFF), fontSize = 14.sp)
        for (i in 0..2) {
            val pk = remember(tick) { prefs.getStr("easy" + i, "") }
            val label = AppCache.apps.firstOrNull { it.pkg == pk }?.label ?: "Sin elegir"
            ActionRow("Botón " + (i + 1) + ": " + label) { pick = i }
        }
        ActionRow("Abrir modo fácil ahora") { run = true }
        ActionRow("Cerrar") { onClose() }
    }
    if (pick >= 0) {
        AppPickerDialog(
            "Botón " + (pick + 1), prefs.getStr("easy" + pick, ""),
            { prefs.putStr("easy" + pick, it); pick = -1; tick++ },
            { pick = -1 }
        )
    }
    if (run) EasyScreen(prefs) { run = false }
}
