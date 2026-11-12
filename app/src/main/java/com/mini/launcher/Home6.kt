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

// ---------- Ruido para dormir ----------

