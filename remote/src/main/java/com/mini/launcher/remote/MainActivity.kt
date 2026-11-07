package com.mini.launcher.remote

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF0C0F18)
private val CARD = Color(0xFF181E2E)
private val LINE = Color(0xFF3C4B6E)
private val ACC = Color(0xFF466EE6)
private val TX = Color(0xFFF0F4FF)
private val MUTED = Color(0xFF96A0B9)
private val OK = Color(0xFF28AA6E)
private val BAD = Color(0xFFD25A5A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ControlApp() } }
    }
}

@Composable
private fun Panel(border: Color = LINE, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(CARD).border(1.dp, border, shape).padding(18.dp),
        content = content
    )
}

@Composable
private fun Option(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(CARD).border(1.dp, LINE, shape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(ACC))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = TX, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = MUTED, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Btn(text: String, filled: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val look = if (filled) Modifier.background(if (enabled) ACC else Color(0xFF2A3350)) else Modifier.border(1.dp, LINE, shape)
    Box(
        modifier.clip(shape).then(look).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Color.White else TX, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControlApp() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(store.get("token")) }
    var tvName by remember { mutableStateOf(store.get("tvName")) }
    var host by remember { mutableStateOf(store.get("host")) }
    var port by remember { mutableStateOf(store.get("port").toIntOrNull() ?: 8080) }
    // 0 sin emparejar, 1 comprobando, 2 conectada, 3 sin respuesta, 4 la TV revoco este celular
    var link by remember { mutableStateOf(if (token.isEmpty()) 0 else 1) }
    val found = remember { mutableStateListOf<Tv>() }
    var target by remember { mutableStateOf<Tv?>(null) }
    var pin by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var ipText by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current

    fun check() {
        if (token.isEmpty()) {
            link = 0
            return
        }
        link = 1
        scope.launch {
            val code = withContext(Dispatchers.IO) { runCatching { Api.ping(host, port, token) }.getOrNull() }
            link = when (code) {
                200 -> 2
                401, 403 -> 4
                else -> 3
            }
        }
    }

    fun doPair(tv: Tv, p: String) {
        if (busy) return
        busy = true
        msg = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Api.pair(tv.host, tv.port, p, Build.MODEL) } }
            busy = false
            val t = r.getOrNull()
            if (t != null) {
                store.put("token", t.first)
                store.put("host", tv.host)
                store.put("port", tv.port.toString())
                store.put("tvName", t.second)
                token = t.first
                host = tv.host
                port = tv.port
                tvName = t.second
                pin = ""
                target = null
                link = 2
                msg = "Emparejada con " + t.second + "."
            } else {
                msg = r.exceptionOrNull()?.message ?: "No se pudo emparejar."
            }
        }
    }

    fun scan() {
        val o = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(ctx, o).startScan()
            .addOnSuccessListener { b ->
                val r = Api.parseQr(b.rawValue ?: "")
                if (r == null) {
                    msg = "Ese QR no es de One Piece."
                } else {
                    target = r.first
                    doPair(r.first, r.second)
                }
            }
            .addOnFailureListener { msg = "No se pudo abrir el escáner (necesita Google Play Services)." }
    }

    LaunchedEffect(Unit) { check() }

    DisposableEffect(Unit) {
        val ui = Handler(Looper.getMainLooper())
        val fi = Finder(ctx) { tv -> ui.post { if (found.none { it.host == tv.host }) found.add(tv) } }
        fi.start()
        onDispose { fi.stop() }
    }

    LaunchedEffect(found.size) {
        if (target == null && found.isNotEmpty()) target = found[0]
    }

    Column(
        Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("One Piece Control", color = TX, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Conexión con tu TV", color = MUTED, fontSize = 16.sp)

        if (token.isNotEmpty()) {
            val dot = when (link) {
                2 -> OK
                3 -> Color(0xFFD2A03C)
                4 -> BAD
                else -> MUTED
            }
            val txt = when (link) {
                1 -> "Comprobando…"
                2 -> "Conectada y autorizada"
                3 -> "Sin respuesta: ¿la TV está encendida y en tu Wi-Fi?"
                else -> "La TV ya no reconoce este celular. Vuelve a emparejar."
            }
            Panel(if (link == 2) OK else LINE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tvName.ifEmpty { "TV" } + "  ·  " + host, color = TX, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(txt, color = dot, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Comprobar", false) { check() }
                    Btn("Desconectar", false) {
                        store.put("token", "")
                        token = ""
                        link = 0
                    }
                }
            }
        }

        Text(if (token.isEmpty()) "Buscar tu TV" else "Buscar otra TV", color = TX, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Panel {
            if (found.isEmpty()) {
                Text("Buscando en la red…", color = MUTED, fontSize = 16.sp)
            } else {
                found.forEach { tv ->
                    val sel = target?.host == tv.host
                    Row(
                        Modifier.fillMaxWidth().clickable { target = tv }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (sel) ACC else LINE))
                        Spacer(Modifier.width(12.dp))
                        Text(tv.name + "  ·  " + tv.host, color = TX, fontSize = 16.sp)
                    }
                }
            }
        }

        Option("Escanear QR", "El QR aparece en la TV (Configuración › Celular)") { scan() }
        Option("Escribir IP", "Si la búsqueda automática no la encuentra") { manual = !manual }
        if (manual) {
            Panel {
                BasicTextField(
                    value = ipText,
                    onValueChange = { ipText = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(color = TX, fontSize = 20.sp),
                    cursorBrush = SolidColor(ACC),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (ipText.isEmpty()) Text("192.168.1.42", color = MUTED, fontSize = 20.sp)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Btn("Usar esta IP", false) {
                    if (Api.isLan(ipText)) {
                        target = Tv(ipText, 8080, "TV")
                        msg = ""
                    } else {
                        msg = "Escribe una IP de tu red, por ejemplo 192.168.1.42."
                    }
                }
            }
        }

        Text("PIN de la TV (una sola vez)", color = MUTED, fontSize = 16.sp)
        Box {
            BasicTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { c -> c.isDigit() }.take(4) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier.size(1.dp).focusRequester(fr)
            )
            Row(
                Modifier.clickable {
                    fr.requestFocus()
                    kb?.show()
                },
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (i in 0 until 4) {
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.width(72.dp).height(88.dp).clip(shape).background(CARD)
                            .border(2.dp, if (i == pin.length) ACC else LINE, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i < pin.length) Text("•", color = TX, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val t = target
        Btn(
            if (busy) "Emparejando…" else if (t == null) "Elige una TV" else "Emparejar con " + t.name,
            true,
            enabled = t != null && pin.length == 4 && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (t != null) doPair(t, pin)
        }
        if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("Emparejada")) OK else BAD, fontSize = 15.sp)
        Text("El PIN lo ves en la TV: Configuración › Conexión con el celular.", color = MUTED, fontSize = 13.sp)
    }
}
