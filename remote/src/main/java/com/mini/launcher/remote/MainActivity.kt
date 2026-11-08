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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ControlApp() } }
    }
}

@Composable
private fun NavItem(glyph: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val box = if (selected) Modifier.background(GRAD).border(1.dp, Color(0x60FFFFFF), shape) else Modifier
        Box(Modifier.size(54.dp).clip(shape).then(box), contentAlignment = Alignment.Center) {
            Text(glyph, color = if (selected) Color.White else MUT, fontSize = 22.sp)
        }
        Text(label, color = if (selected) TXT else MUT, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    var tab by remember { mutableStateOf(if (token.isEmpty()) 2 else 0) } // 0 Mando, 1 Enviar, 2 Conexion
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

    val paired = token.isNotEmpty()
    val linkColor = when {
        !paired -> MUT
        link == 2 -> TEAL
        link == 3 -> AMBER
        link == 4 -> RED
        else -> MUT
    }
    val linkText = when {
        !paired -> "Sin emparejar"
        else -> tvName.ifEmpty { "TV" } + " · " + when (link) {
            1 -> "Comprobando…"
            2 -> "Conectada"
            3 -> "Sin respuesta"
            else -> "No autorizada"
        }
    }

    Box(
        Modifier.fillMaxSize().background(BG0).drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0x405B3FB8), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.12f),
                    radius = size.width * 0.95f
                )
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera: TV actual y acceso rapido a Conexion.
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glass(Modifier.weight(1f).height(60.dp), radius = 30.dp, onClick = { tab = 2 }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBox("★", 40.dp, 13.dp, 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("One Piece Control", color = TXT, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(linkColor))
                                Spacer(Modifier.width(6.dp))
                                Text(linkText, color = linkColor, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Box(Modifier.size(60.dp)) {
                    Glass(Modifier.fillMaxSize(), radius = 20.dp, onClick = { tab = 2 }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("⚙", color = TXT, fontSize = 24.sp)
                        }
                    }
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 2.dp).size(11.dp).clip(CircleShape).background(P1))
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (tab != 2 && !paired) {
                    Title(if (tab == 0) "Mando de la TV" else "Enviar a la TV", "Primero empareja el celular con tu TV.")
                    Glass(Modifier.fillMaxWidth(), radius = 30.dp) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Aún no hay una TV emparejada.", color = TXT, fontSize = 16.sp)
                            PrimaryBtn("Ir a Conexión") { tab = 2 }
                        }
                    }
                } else if (tab == 0) {
                    MandoScreen(Conn(host, port, token))
                } else if (tab == 1) {
                    EnviarScreen(Conn(host, port, token))
                } else {
                    Title("Conexión con tu TV", "Empareja el celular una sola vez.")

                    if (paired) {
                        Glass(Modifier.fillMaxWidth(), radius = 30.dp, border = if (link == 2) Color(0xFF4A3C80) else EDGE) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBox("★", 64.dp, 20.dp, 26.sp)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(tvName.ifEmpty { "TV" } + "  ·  " + host, color = TXT, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(7.dp).clip(CircleShape).background(linkColor))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                when (link) {
                                                    1 -> "Comprobando…"
                                                    2 -> "Conectada y autorizada"
                                                    3 -> "Sin respuesta: ¿la TV está encendida y en tu Wi-Fi?"
                                                    else -> "La TV ya no reconoce este celular. Vuelve a emparejar."
                                                },
                                                color = linkColor, fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    GhostBtn("Comprobar") { check() }
                                    GhostBtn("Desconectar") {
                                        store.put("token", "")
                                        token = ""
                                        link = 0
                                    }
                                }
                            }
                        }
                    }

                    Text(if (paired) "Buscar otra TV" else "Buscar tu TV", color = TXT, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                    Glass(Modifier.fillMaxWidth(), radius = 30.dp) {
                        if (found.isEmpty()) {
                            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔍", fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Text("Buscando en la red…", color = MUT, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                CircularProgressIndicator(Modifier.size(22.dp), color = P1, strokeWidth = 2.dp)
                            }
                        } else {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                found.forEach { tv ->
                                    val sel = target?.host == tv.host
                                    Row(
                                        Modifier.fillMaxWidth().clickable { target = tv }.padding(horizontal = 18.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (sel) P1 else EDGE))
                                        Spacer(Modifier.width(12.dp))
                                        Text(tv.name + "  ·  " + tv.host, color = TXT, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }

                    OptionRow("▣", "Escanear QR", "El QR aparece en la TV") { scan() }
                    OptionRow("123", "Escribir IP", "Si la búsqueda no la encuentra") { manual = !manual }
                    if (manual) {
                        Glass(Modifier.fillMaxWidth(), radius = 30.dp) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GlassField(
                                    ipText,
                                    { ipText = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                                    "192.168.1.42",
                                    keyboard = KeyboardType.Decimal
                                )
                                GhostBtn("Usar esta IP") {
                                    if (Api.isLan(ipText)) {
                                        target = Tv(ipText, 8080, "TV")
                                        msg = ""
                                    } else {
                                        msg = "Escribe una IP de tu red, por ejemplo 192.168.1.42."
                                    }
                                }
                            }
                        }
                    }

                    Text("PIN de la TV (una sola vez)", color = MUT, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
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
                            Modifier.fillMaxWidth().clickable {
                                fr.requestFocus()
                                kb?.show()
                            },
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (i in 0 until 4) {
                                val shape = RoundedCornerShape(22.dp)
                                Box(
                                    Modifier.weight(1f).height(76.dp).clip(shape).background(FIELD)
                                        .border(if (i == pin.length) 2.dp else 1.dp, if (i == pin.length) P1 else EDGE, shape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (i < pin.length) Text("•", color = TXT, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    val t = target
                    PrimaryBtn(
                        if (busy) "Emparejando…" else if (t == null) "Elige una TV" else "Emparejar con " + t.name,
                        enabled = t != null && pin.length == 4 && !busy
                    ) {
                        if (t != null) doPair(t, pin)
                    }
                    if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("Emparejada")) TEAL else RED, fontSize = 14.sp)
                    Text("El PIN lo ves en la TV: Configuración › Celular.", color = MUT, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // Barra inferior de cristal.
            Glass(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp, top = 4.dp), radius = 34.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    NavItem("●", "Mando", tab == 0, Modifier.weight(1f)) { tab = 0 }
                    NavItem("➤", "Enviar", tab == 1, Modifier.weight(1f)) { tab = 1 }
                    NavItem("⚙", "Conexión", tab == 2, Modifier.weight(1f)) { tab = 2 }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(glyph: String, title: String, sub: String, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), radius = 30.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(glyph, 50.dp, 16.dp, if (glyph.length > 1) 16.sp else 20.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TXT, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = MUT, fontSize = 13.sp)
            }
            Text("›", color = TXT, fontSize = 26.sp)
        }
    }
}
