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

/** Mando de acciones pendientes (remapeo, alarmas, apagado inteligente) hacia la pantalla de inicio. */
object ActBus {
    var act by mutableIntStateOf(0)
    var n by mutableIntStateOf(0)
    fun post(a: Int) { act = a; n++ }
}

/** Funciones de «Uso diario» (fase 3). Todo se guarda en Prefs y viene apagado por defecto. */
object Daily {
    @Volatile var homeFromApp = false
    private var lastHomeMs = 0L
    private var rates: Pair<Long, JSONObject>? = null

    private fun p(c: Context) = Prefs(c)
    fun on(c: Context, k: String) = p(c).getInt(k, 0) == 1

    // ---- JSON de mapas simples (pkg -> texto) ----
    fun map(c: Context, k: String): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(p(c).getStr(k, "{}"))
            for (x in o.keys()) out[x] = o.getString(x)
        } catch (e: Exception) {
        }
        return out
    }

    fun saveMap(c: Context, k: String, m: Map<String, String>) {
        p(c).putStr(k, JSONObject(m).toString())
    }

    // ---- Franja horaria: 0 manana (6-12), 1 tarde (12-19), 2 noche ----
    fun dayPart(): Int {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (h in 6..11) 0 else if (h in 12..18) 1 else 2
    }

    // ---- Aprende habitos ----
    private fun habSlot(): Int {
        val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return (if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) 3 else 0) + dayPart()
    }

    fun noteOpen(c: Context, pkg: String) {
        if (!on(c, "dHabits")) return
        val m = map(c, "dHab")
        val k = pkg + "|" + habSlot()
        m[k] = ((m[k]?.toIntOrNull() ?: 0) + 1).toString()
        saveMap(c, "dHab", m)
    }

    /** Sube al inicio del dock las apps que sueles abrir a esta hora y en este tipo de dia. */
    fun habitSort(c: Context, list: List<String>): List<String> {
        if (!on(c, "dHabits")) return list
        val m = map(c, "dHab")
        val s = habSlot()
        fun sc(x: String): Int = (m[x + "|" + s]?.toIntOrNull() ?: 0).let { if (it >= 3) it else 0 }
        return list.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { sc(it.value) }.thenBy { it.index }
        ).map { it.value }
    }

    // ---- Volumen y atajo profundo por app ----
    fun applyVolume(c: Context, pkg: String) {
        val pct = map(c, "dVol")[pkg]?.toIntOrNull() ?: return
        try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * pct / 100f).toInt(), 0
            )
        } catch (e: Exception) {
        }
    }

    fun deep(c: Context, a: AppInfo): AppInfo {
        val u = map(c, "dDeep")[a.pkg] ?: return a
        if (u.isBlank()) return a
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(u.trim())).setPackage(a.pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (c.packageManager.queryIntentActivities(i, 0).isEmpty()) a
            else AppInfo(a.pkg, a.label, a.art, a.banner, i, a.glow)
        } catch (e: Exception) {
            a
        }
    }

    // ---- Disparadores: al abrir una app o una entrada ----
    val TRIG_ACTIONS = listOf("Ninguna", "Activar escena de cine", "Limpiar RAM", "Modo ahorro del launcher")
    fun trigFor(c: Context, pkg: String): Int =
        if (pkg.isNotEmpty() && pkg == p(c).getStr("dTrigApp", "")) p(c).getInt("dTrigAct", 0) else 0

    // ---- Doble Inicio (Alt-Tab) ----
    fun doubleHome(c: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        val dbl = now - lastHomeMs < 450
        lastHomeMs = now
        if (!dbl || !on(c, "dAltTab")) return false
        val last = p(c).recents().firstOrNull() ?: return false
        val i = c.packageManager.getLaunchIntentForPackage(last) ?: return false
        return try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            lastHomeMs = 0L
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Al encender: una vez por arranque de la TV ----
    fun bootPending(c: Context): Boolean {
        if (!on(c, "dBootCard") && !on(c, "dBootPick")) return false
        if (SystemClock.elapsedRealtime() > 600_000L) return false
        val id = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val old = p(c).getStr("dBootId", "0").toLongOrNull() ?: 0L
        if (Math.abs(id - old) < 600_000L) return false
        p(c).putStr("dBootId", id.toString())
        return true
    }

    // ---- Historial del portapapeles del celular ----
    fun pushClip(c: Context, t: String) {
        if (t.isBlank()) return
        val l = clips(c).filter { it != t }
        val n = (listOf(t) + l).take(20)
        p(c).putStr("dClip", JSONArray(n).toString())
    }

    fun clips(c: Context): List<String> = try {
        val a = JSONArray(p(c).getStr("dClip", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun copy(c: Context, t: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("One Piece", t))
    }

    // ---- Carpetas inteligentes por categoria ----
    fun smartFolders(c: Context): Int {
        val pr = p(c)
        val g = LinkedHashMap(pr.groups())
        val free = MAX_FOLDERS - g.size
        if (free <= 0) return 0
        val taken = g.values.flatten().toSet()
        val favs = pr.favs().toSet()
        val hid = pr.hidden()
        val pool = AppCache.apps.filter { it.pkg !in taken && it.pkg !in favs && it.pkg !in hid }
        val byCat = pool.groupBy { appCategory(c.packageManager, it.pkg) }
            .filter { it.key in 1..4 && it.value.size >= 2 && APP_CATS[it.key] !in g.keys }
        var made = 0
        for ((cat, l) in byCat.entries.sortedByDescending { it.value.size }.take(free)) {
            g[APP_CATS[cat]] = l.map { it.pkg }
            made++
        }
        if (made > 0) pr.saveGroups(g)
        return made
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

    // ---- Intermedio de cine: pausa la reproduccion activa ----
    fun pauseMedia(c: Context): String {
        return try {
            val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val l = msm.getActiveSessions(ComponentName(c, MediaListener::class.java))
            val m = l.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            m?.transportControls?.pause()
            m?.packageName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun resumeMedia(c: Context, pkg: String) {
        try {
            if (pkg.isNotEmpty()) c.packageManager.getLaunchIntentForPackage(pkg)?.let {
                c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val l = msm.getActiveSessions(ComponentName(c, MediaListener::class.java))
            l.firstOrNull { it.packageName == pkg }?.transportControls?.play()
        } catch (e: Exception) {
        }
    }

    // ---- Apagado inteligente (lo vigila PowerService) ----
    val SMART_HOURS = listOf(0, 2, 3, 4, 6)
    fun smartHours(c: Context): Int = SMART_HOURS.getOrElse(p(c).getInt("dSmartOff", 0)) { 0 }
}

/** Cuenta atras a pantalla completa. Cualquier tecla la termina y reanuda. */
@Composable
fun CountdownDialog(
    title: String,
    secs: Int,
    hint: String,
    onDone: (Boolean) -> Unit
) {
    var left by remember { mutableIntStateOf(secs) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
        onDone(true)
    }
    Dialog(
        onDismissRequest = { onDone(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF2000000))
                .focusRequester(fr).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) { onDone(false); true } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                Text(
                    (left / 60).toString() + ":" + (left % 60).toString().padStart(2, '0'),
                    color = Color.White, fontSize = 96.sp, modifier = Modifier.padding(vertical = 18.dp)
                )
                Text(hint, color = Color(0xB3FFFFFF), fontSize = 18.sp)
            }
        }
    }
}

/** Saludo, clima y evento al encender, y selector de perfil. */
@Composable
fun BootDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    val pr = remember { Prefs(c) }
    val card = Daily.on(c, "dBootCard")
    val pick = Daily.on(c, "dBootPick")
    var weather by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (!card) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            weather = runCatching { fetchWeatherLine(pr.getStr("city", CITY), pr.getInt("fahrenheit", 0) == 1) }.getOrNull() ?: ""
            event = if (hasCalendarPermission(c)) runCatching { nextEventText(c) }.getOrNull() ?: "" else ""
        }
    }
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hi = if (h < 12) "Buenos días" else if (h < 19) "Buenas tardes" else "Buenas noches"
    ExtraDialog(if (card) hi else "¿Quién va a ver la TV?", onClose) {
        if (card) {
            Text(
                SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date()),
                color = Color(0xE6FFFFFF), fontSize = 16.sp
            )
            if (weather.isNotEmpty()) Text(weather, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
            if (event.isNotEmpty()) Text("📅 " + event, color = Color(0xE6FFFFFF), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (pick) {
            val cur = pr.getInt("famCur", 0)
            Text("Perfil", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            for (i in 0 until FAM_COUNT) {
                ActionRow((if (i == cur) "✓ " else "") + "Perfil " + (i + 1), if (i == cur) Modifier.autoFocus() else Modifier) {
                    if (i != cur) switchFamily(c, i)
                    onClose()
                }
            }
        }
        ActionRow("Continuar", if (!pick) Modifier.autoFocus() else Modifier) { onClose() }
    }
}

/** «¿Sigues ahí?»: si nadie responde en 60 s, apaga la pantalla. */
@Composable
fun StillThereDialog(onDone: (Boolean) -> Unit) {
    CountdownDialog("¿Sigues ahí?", 60, "Pulsa cualquier botón para seguir viendo", onDone)
}

private fun keyLabel(code: Int): String = when (code) {
    183 -> "Rojo"; 184 -> "Verde"; 185 -> "Amarillo"; 186 -> "Azul"
    82 -> "Menú"; 165 -> "Info"; 172 -> "Guía"; 85 -> "Play/Pausa"; 87 -> "Siguiente"; 88 -> "Anterior"
    KeyEvent.KEYCODE_VOLUME_UP -> "Volumen +"; KeyEvent.KEYCODE_VOLUME_DOWN -> "Volumen -"
    KeyEvent.KEYCODE_VOLUME_MUTE -> "Silencio"; KeyEvent.KEYCODE_SEARCH -> "Buscar"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase().replaceFirstChar { it.uppercase() } + " (" + code + ")"
}

@Composable
private fun PSwitch(label: String, key: String) {
    val pr = Prefs(LocalContext.current)
    var on by remember { mutableStateOf(pr.getInt(key, 0) == 1) }
    SwitchRow(label, on) { on = it; pr.putInt(key, if (it) 1 else 0) }
}

@Composable
private fun PChoice(label: String, options: List<String>, key: String, after: (Int) -> Unit = {}) {
    val pr = Prefs(LocalContext.current)
    var v by remember { mutableIntStateOf(pr.getInt(key, 0)) }
    ChoiceRow(label, options, v) { v = it; pr.putInt(key, it); after(it) }
}

/** Cuadro de «Uso diario»: interruptores y listas que se guardan al instante. */
@Composable
fun DailyDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    ExtraDialog("Uso diario", onClose, scroll = true) {
        PSwitch("Doble Inicio: volver a la última app", "dAltTab")
        PSwitch("Dock distinto por franja horaria", "dSlots")
        PSwitch("Aprender hábitos (la ficha sube según día y hora)", "dHabits")
        PSwitch("Recordar posición al volver de una app", "dKeepPos")
        PSwitch("Tarjeta del día al encender", "dBootCard")
        PSwitch("Elegir perfil al encender", "dBootPick")
        PChoice("Apagado inteligente", listOf("Desactivado", "2 h sin tocar", "3 h", "4 h", "6 h"), "dSmartOff") {
            if (it > 0 && PowerService.instance == null) toast(c, "Activa «One Piece - energía» en Accesibilidad.")
        }
        Text("Al abrir…", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        TrigAppRow()
        PChoice("Acción al abrirla", Daily.TRIG_ACTIONS, "dTrigAct")
        PChoice("Acción al abrir una entrada HDMI", Daily.TRIG_ACTIONS, "dTrigHdmi")
        Text("Por app", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        DialogRow("Volumen y atajo profundo por app") { close -> PerAppDialog(close) }
        DialogRow("Portapapeles recibido del celular") { close -> ClipDialog(close) }
        ActionRow("Crear carpetas por categoría") {
            val n = Daily.smartFolders(c)
            toast(c, if (n > 0) "Carpetas creadas: $n. Mira el inicio." else "No hay carpetas libres o categorías con 2 apps.")
        }
        ActionRow(stringResource_close()) { onClose() }
    }
}

@Composable
private fun TrigAppRow() {
    val pr = Prefs(LocalContext.current)
    var cur by remember { mutableStateOf(pr.getStr("dTrigApp", "")) }
    val ta = AppCache.apps.firstOrNull { it.pkg == cur }?.label ?: "ninguna"
    DialogRow("App disparadora: $ta") { close ->
        AppPickerDialog("App disparadora", cur, { pr.putStr("dTrigApp", it); cur = it; close() }, close)
    }
}

@Composable
private fun stringResource_close(): String = androidx.compose.ui.res.stringResource(R.string.x_close)

@Composable
private fun PerAppDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var pkg by remember { mutableStateOf("") }
    if (pkg.isEmpty()) {
        AppPickerDialog("Elige la app", "", { if (it.isEmpty()) onClose() else pkg = it }, onClose)
        return
    }
    val label = AppCache.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
    var vol by remember { mutableIntStateOf((Daily.map(c, "dVol")[pkg]?.toIntOrNull()?.div(10) ?: -1) + 1) }
    var link by remember { mutableStateOf(Daily.map(c, "dDeep")[pkg] ?: "") }
    ExtraDialog(label, onClose, maxWidth = 640, scroll = true) {
        ChoiceRow("Volumen al abrir", listOf("Sin cambio") + (0..10).map { (it * 10).toString() + "%" }, vol) {
            vol = it
            val m = Daily.map(c, "dVol")
            if (it == 0) m.remove(pkg) else m[pkg] = ((it - 1) * 10).toString()
            Daily.saveMap(c, "dVol", m)
        }
        Text(
            "Enlace profundo (ej. vnd.youtube://… o https://…). Vacío = abrir la app normal.",
            color = Color(0xB3FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
        )
        SearchField(link, { link = it }, Modifier.fillMaxWidth(), placeholder = "Enlace")
        ActionRow("Guardar enlace") {
            val m = Daily.map(c, "dDeep")
            if (link.isBlank()) m.remove(pkg) else m[pkg] = link.trim()
            Daily.saveMap(c, "dDeep", m)
            toast(c, "Guardado.")
        }
        ActionRow("Listo") { onClose() }
    }
}

@Composable
private fun ClipDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableIntStateOf(0) }
    val l = remember(t) { Daily.clips(c) }
    ExtraDialog("Portapapeles del celular", onClose, scroll = true) {
        if (l.isEmpty()) Text("Aún no has enviado texto desde el celular.", color = Color(0xB3FFFFFF), fontSize = 14.sp)
        for (s in l) ActionRow(s.replace('\n', ' ').take(70)) { Daily.copy(c, s); toast(c, "Copiado.") }
        if (l.isNotEmpty()) ActionRow("Borrar historial") { Prefs(c).putStr("dClip", "[]"); t++ }
        ActionRow("Cerrar") { onClose() }
    }
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
    if (key != 0 && !needApp) {
        ExtraDialog(keyLabel(key) + " hará…", { key = 0 }, scroll = true) {
            for (i in 1 until REMAP_ACTIONS.size) {
                ActionRow(REMAP_ACTIONS[i], if (i == 1) Modifier.autoFocus() else Modifier) {
                    Remap.save(c, m + (key to "a:$i")); key = 0; t++
                }
            }
            ActionRow("Abrir una app…") { needApp = true }
            ActionRow("Cancelar") { key = 0 }
        }
    }
    if (key != 0 && needApp) {
        AppPickerDialog("Abrir con " + keyLabel(key), "", {
            if (it.isNotEmpty()) Remap.save(c, m + (key to "p:$it"))
            key = 0; needApp = false; t++
        }, { key = 0; needApp = false })
    }
}
