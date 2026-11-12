package com.mini.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Mando de acciones pendientes (remapeo, alarmas) hacia la pantalla de inicio. */
object ActBus {
    var act by mutableIntStateOf(0)
    var n by mutableIntStateOf(0)
    fun post(a: Int) { act = a; n++ }
}

/** Paleta de comandos de la búsqueda (temporizador, alarma, divisas, cálculo) y alarmas propias. */
object Daily {
    private var rates: Pair<Long, JSONObject>? = null

    private fun p(c: Context) = Prefs(c)
    fun copy(c: Context, t: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("One Piece", t))
    }

    // ---- Alarmas y temporizadores propios (sin app de reloj) ----
    fun schedule(c: Context, at: Long, msg: String) {
        val i = Intent(c, MainActivity::class.java).putExtra("dAct", 98).putExtra("dMsg", msg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            c, (at / 1000L).toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, pi), pi)
    }

    fun ring(c: Context, msg: String) {
        toast(c, if (msg.isEmpty()) "¡Alarma!" else msg)
        try {
            val r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            r.play()
            Handler(Looper.getMainLooper()).postDelayed({ try { r.stop() } catch (e: Exception) { } }, 20_000L)
        } catch (e: Exception) {
        }
    }

    // ---- Paleta de comandos en la busqueda ----
    class Cmd(val text: String, val run: () -> Unit)

    fun command(c: Context, raw: String): Cmd? {
        val q = raw.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return null
        Regex("^(timer|temporizador)\\s+(\\d{1,3})\\s*(m|min|s|seg)?$").find(q)?.let { m ->
            val n = m.groupValues[2].toLong()
            val sec = m.groupValues[3].startsWith("s")
            val ms = n * (if (sec) 1000L else 60_000L)
            val lbl = n.toString() + (if (sec) " s" else " min")
            return Cmd("⏱ Temporizador de $lbl") {
                schedule(c, System.currentTimeMillis() + ms, "Temporizador de $lbl terminado")
                toast(c, "Temporizador de $lbl en marcha.")
            }
        }
        Regex("^(alarma|despertador)\\s+(\\d{1,2})[:.](\\d{2})$").find(q)?.let { m ->
            val h = m.groupValues[2].toInt()
            val mi = m.groupValues[3].toInt()
            if (h > 23 || mi > 59) return null
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val t = h.toString().padStart(2, '0') + ":" + m.groupValues[3]
            return Cmd("⏰ Alarma a las $t") {
                schedule(c, cal.timeInMillis, "Alarma de las $t")
                toast(c, "Alarma puesta a las $t.")
            }
        }
        Regex("^(\\d+(?:[.,]\\d+)?)\\s*([a-z]{3})\\s*(?:a|en|to|>|->)\\s*([a-z]{3})$").find(q)?.let { m ->
            val amt = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val from = m.groupValues[2].uppercase(Locale.ROOT)
            val to = m.groupValues[3].uppercase(Locale.ROOT)
            val r = rate(from, to) ?: return null
            val v = String.format(Locale.US, "%.2f", amt * r)
            return Cmd("💱 $amt $from = $v $to") { copy(c, v); toast(c, "Copiado: $v") }
        }
        if (q.any { it in "+-*/x×÷" } && q.any { it.isDigit() }) {
            val v = try { Calc(q.replace('x', '*').replace('×', '*').replace('÷', '/').replace(',', '.')).run() } catch (e: Exception) { null }
            if (v != null && !v.isNaN() && !v.isInfinite()) {
                val s = if (v == Math.rint(v) && Math.abs(v) < 1e12) v.toLong().toString() else String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
                return Cmd("🧮 $s") { copy(c, s); toast(c, "Copiado: $s") }
            }
        }
        return null
    }

    private fun rate(from: String, to: String): Double? {
        val now = System.currentTimeMillis()
        var r = rates
        if (r == null || now - r.first > 6 * 3_600_000L) {
            val t = httpGet("https://open.er-api.com/v6/latest/USD") ?: return null
            r = try { now to JSONObject(t).getJSONObject("rates") } catch (e: Exception) { return null }
            rates = r
        }
        val cur = r ?: return null
        return try { cur.second.getDouble(to) / cur.second.getDouble(from) } catch (e: Exception) { null }
    }

    /** Calculadora minima: + - * / y parentesis. */
    private class Calc(val s: String) {
        var i = 0
        fun run(): Double { val v = expr(); if (i != s.length) throw IllegalStateException(); return v }
        fun ws() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double {
            var v = term()
            while (true) { ws()
                if (i < s.length && s[i] == '+') { i++; v += term() }
                else if (i < s.length && s[i] == '-') { i++; v -= term() } else return v }
        }
        fun term(): Double {
            var v = atom()
            while (true) { ws()
                if (i < s.length && s[i] == '*') { i++; v *= atom() }
                else if (i < s.length && s[i] == '/') { i++; v /= atom() } else return v }
        }
        fun atom(): Double {
            ws()
            if (i < s.length && s[i] == '-') { i++; return -atom() }
            if (i < s.length && s[i] == '(') { i++; val v = expr(); ws(); if (i >= s.length || s[i] != ')') throw IllegalStateException(); i++; return v }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}

private fun keyLabel(code: Int): String = when (code) {
    183 -> "Rojo"; 184 -> "Verde"; 185 -> "Amarillo"; 186 -> "Azul"
    82 -> "Menú"; 165 -> "Info"; 172 -> "Guía"; 85 -> "Play/Pausa"; 87 -> "Siguiente"; 88 -> "Anterior"
    KeyEvent.KEYCODE_VOLUME_UP -> "Volumen +"; KeyEvent.KEYCODE_VOLUME_DOWN -> "Volumen -"
    KeyEvent.KEYCODE_VOLUME_MUTE -> "Silencio"; KeyEvent.KEYCODE_SEARCH -> "Buscar"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase().replaceFirstChar { it.uppercase() } + " (" + code + ")"
}

/** Remapeo de botones del mando. */
@Composable
fun RemapDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableIntStateOf(0) }
    val m = remember(t) { Remap.load(c) }
    var learning by remember { mutableStateOf(false) }
    var key by remember { mutableIntStateOf(0) }
    var needApp by remember { mutableStateOf(false) }
    var ask by remember { mutableIntStateOf(0) }
    ExtraDialog("Remapear botones", onClose, maxWidth = 600, scroll = true) {
        Text(
            "Los botones remapeados funcionan dentro del launcher.",
            color = Color(0xB3FFFFFF), fontSize = 13.sp
        )
        for ((code, v) in m) {
            ActionRow(keyLabel(code) + "  →  " + Remap.label(c, v) + "   (OK: quitar)") {
                Remap.save(c, m - code); t++
            }
        }
        ActionRow("+ Añadir un botón", Modifier.autoFocus()) { learning = true }
        if (m.isNotEmpty()) ActionRow("Quitar todos") { Remap.save(c, emptyMap()); t++ }
        ActionRow("Cerrar") { onClose() }
    }
    if (learning) {
        DisposableEffect(Unit) {
            Remap.learn = { code -> learning = false; key = code }
            onDispose { Remap.learn = null }
        }
        ExtraDialog("Pulsa el botón del mando", { learning = false }) {
            Column(Modifier.onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && !Remap.blocked(e.nativeKeyEvent.keyCode)) {
                    key = e.nativeKeyEvent.keyCode; learning = false; true
                } else false
            }) {
                Text(
                    "Pulsa el botón que quieres cambiar. Atrás, Inicio, las flechas y OK no se pueden cambiar.",
                    color = Color(0xE6FFFFFF), fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp)
                )
                ActionRow("Cancelar", Modifier.autoFocus()) { learning = false }
            }
        }
    }
    if (key != 0 && !needApp && ask == 0) {
        ExtraDialog(keyLabel(key) + " hará…", { key = 0 }, scroll = true) {
            for (i in 1 until REMAP_ACTIONS.size) {
                ActionRow(REMAP_ACTIONS[i], if (i == 1) Modifier.autoFocus() else Modifier) {
                    Remap.save(c, m + (key to "a:$i")); key = 0; t++
                }
            }
            ActionRow("Abrir una app…") { needApp = true }
            ActionRow("Llamar a una dirección web…") { ask = 1 }
            ActionRow("Encender un equipo (Wake-on-LAN)…") { ask = 2 }
            ActionRow("Cancelar") { key = 0 }
        }
    }
    if (key != 0 && ask != 0) {
        TextEditDialog(
            if (ask == 1) "Dirección web" else "Dirección MAC del equipo",
            "",
            if (ask == 1) "Ejemplo: http://192.168.1.20:8123/api/webhook/luz" else "Ejemplo: AA:BB:CC:DD:EE:FF",
            200,
            { t0 ->
                if (ask == 1 && t0.isNotEmpty()) {
                    val u = if (t0.startsWith("http://") || t0.startsWith("https://")) t0 else "http://$t0"
                    Remap.save(c, m + (key to "w:$u"))
                } else if (ask == 2 && t0.isNotEmpty()) {
                    val b = Remap.parseMac(t0)
                    if (b == null) toast(c, "Dirección MAC no válida.")
                    else Remap.save(c, m + (key to "l:" + b.joinToString(":") { "%02X".format(it) }))
                }
                key = 0; ask = 0; t++
            },
            { ask = 0 }
        )
    }
    if (key != 0 && needApp) {
        AppPickerDialog("Abrir con " + keyLabel(key), "", {
            if (it.isNotEmpty()) Remap.save(c, m + (key to "p:$it"))
            key = 0; needApp = false; t++
        }, { key = 0; needApp = false })
    }
}
