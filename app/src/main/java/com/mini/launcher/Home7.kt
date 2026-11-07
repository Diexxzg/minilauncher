package com.mini.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

// Paquete 7 (Sistema, sin puente ADB): modo juego, instalador de APK desde USB, volumen adaptable
// y centro de notificaciones.

// ---------- Modo juego ----------

object Game {
    private fun stamp(): Long = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000L

    /** Activo solo desde la ultima vez que arranco la TV (al reiniciar se apaga solo). */
    fun active(cfg: Cfg): Boolean {
        if (cfg.ex("gameOn") != "1") return false
        val s = cfg.ex("gameBoot").toLongOrNull() ?: return false
        return Math.abs(stamp() - s) <= 2L
    }

    fun on(ctx: Context, cfg: Cfg) {
        cfg.setEx("gameBoot", stamp().toString())
        cfg.setEx("gameOn", "1")
        if (cfg.ex("gameRam", "1") == "1") Perf.cleanAsync(ctx)
    }

    fun off(cfg: Cfg) {
        cfg.setEx("gameOn", "0")
    }

    fun quiet(cfg: Cfg): Boolean = active(cfg) && cfg.ex("gameQuiet", "1") == "1"
    fun pauseBg(cfg: Cfg): Boolean = active(cfg) && cfg.ex("gamePause", "1") == "1"
}

@Composable
fun GameModeDialog(cfg: Cfg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val active = Game.active(cfg)
    ExtraDialog("Modo juego", onClose, 620, scroll = true) {
        for ((k, t) in listOf("gameRam" to "Liberar RAM al activar", "gamePause" to "Pausar fondo animado", "gameQuiet" to "Silenciar avisos")) {
            val on = cfg.ex(k, "1") == "1"
            ActionRow(t + ": " + (if (on) "Sí" else "No")) { cfg.setEx(k, if (on) "0" else "1") }
        }
        ActionRow(if (active) "Desactivar modo juego" else "Activar modo juego") {
            if (active) Game.off(cfg) else Game.on(ctx, cfg)
        }
        Text(
            "Silenciar avisos oculta los recordatorios y la tarjeta en esquina. Se apaga solo al reiniciar la TV.",
            color = Color(0x99FFFFFF), fontSize = 12.sp
        )
        ActionRow("Cerrar") { onClose() }
    }
}

// ---------- Volumen adaptable ----------

object VolGuard {
    @Volatile
    private var rx: BroadcastReceiver? = null

    /** Registra o quita el vigilante segun los ajustes. Se llama al arrancar y al cambiar algo. */
    @Synchronized
    fun apply(ctx: Context) {
        val app = ctx.applicationContext
        val on = Prefs(app).getInt("vgOn", 0) == 1
        val cur = rx
        if (!on) {
            if (cur != null) {
                try {
                    app.unregisterReceiver(cur)
                } catch (e: Exception) {
                }
                rx = null
            }
            return
        }
        if (cur == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    clamp(c)
                }
            }
            val f = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            try {
                if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
                rx = r
            } catch (e: Exception) {
            }
        }
        clamp(app)
    }

    private fun clamp(c: Context) {
        try {
            val p = Prefs(c.applicationContext)
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val lo = Math.round(mx * p.getInt("vgMin", 15) / 100f)
            val top = maxOf(lo, Math.round(mx * p.getInt("vgMax", 70) / 100f))
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur > top) am.setStreamVolume(AudioManager.STREAM_MUSIC, top, 0)
            else if (cur < lo) am.setStreamVolume(AudioManager.STREAM_MUSIC, lo, 0)
        } catch (e: Exception) {
        }
    }
}

@Composable
fun VolumeGuardDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.getInt("vgOn", 0) == 1) }
    var mx by remember { mutableIntStateOf(prefs.getInt("vgMax", 70)) }
    var mn by remember { mutableIntStateOf(prefs.getInt("vgMin", 15)) }
    fun save() {
        prefs.putInt("vgOn", if (on) 1 else 0)
        prefs.putInt("vgMax", mx)
        prefs.putInt("vgMin", mn)
        VolGuard.apply(ctx)
    }
    ExtraDialog("Volumen adaptable", onClose, 620, scroll = true) {
        ActionRow("Volumen adaptable: " + (if (on) "Activado" else "Apagado")) {
            on = !on
            save()
        }
        Text("Tope máximo", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("−", false) {
                mx = maxOf(mn + 5, mx - 5)
                save()
            }
            Text("" + mx + " %", color = Color.White, fontSize = 18.sp)
            Pill("+", false) {
                mx = minOf(100, mx + 5)
                save()
            }
        }
        Text("Piso mínimo", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("−", false) {
                mn = maxOf(0, mn - 5)
                save()
            }
            Text("" + mn + " %", color = Color.White, fontSize = 18.sp)
            Pill("+", false) {
                mn = minOf(mx - 5, mn + 5)
                save()
            }
        }
        Text(
            "El volumen no sube del tope ni baja del piso. Si tu TV regula el volumen por HDMI o una barra de sonido, puede que no funcione.",
            color = Color(0x99FFFFFF), fontSize = 12.sp
        )
        ActionRow("Cerrar") { onClose() }
    }
}

// ---------- Centro de notificaciones ----------

/** Historial de avisos (opcional, apagado por defecto). Archivo privado de la app, maximo 300 lineas. */
object NotifLog {
    class E(val t: Long, val pkg: String, val title: String, val text: String)

    private const val FILE = "notiflog.txt"
    private const val MAX = 300

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(140)

    @Synchronized
    fun add(ctx: Context, pkg: String, title: String, text: String) {
        val f = File(ctx.filesDir, FILE)
        val line = System.currentTimeMillis().toString() + "\t" + pkg + "\t" + clean(title) + "\t" + clean(text)
        val old = if (f.exists()) f.readLines() else emptyList()
        val last = old.lastOrNull()
        if (last != null && last.substringAfter("\t") == line.substringAfter("\t")) return
        f.writeText((old + line).takeLast(MAX).joinToString("\n") + "\n")
    }

    /** Del mas nuevo al mas viejo. */
    @Synchronized
    fun list(ctx: Context): List<E> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull {
            val p = it.split("\t", limit = 4)
            val t = if (p.size == 4) p[0].toLongOrNull() else null
            if (t == null) null else E(t, p[1], p[2], p[3])
        }.reversed()
    }

    @Synchronized
    fun clear(ctx: Context) {
        File(ctx.filesDir, FILE).delete()
    }
}

private fun dayLabel(t: Long): String {
    val d = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
    val a = d.format(java.util.Date(t))
    val now = System.currentTimeMillis()
    return when (a) {
        d.format(java.util.Date(now)) -> "Hoy"
        d.format(java.util.Date(now - 86400000L)) -> "Ayer"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale("es")).format(java.util.Date(t))
    }
}

@Composable
fun NotifCenterDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(prefs.getInt("nlOn", 0) == 1) }
    var tick by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("") }
    val all = remember(tick, on) { NotifLog.list(ctx) }
    val apps = remember(all) { all.groupingBy { it.pkg }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key } }
    val shown = remember(all, filter) { (if (filter.isEmpty()) all else all.filter { it.pkg == filter }).take(60) }
    fun label(pkg: String): String = AppCache.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg.substringAfterLast('.')
    val hm = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.US) }
    ExtraDialog("Centro de notificaciones", onClose, 900, scroll = true) {
        ActionRow("Guardar historial: " + (if (on) "Sí" else "No")) {
            on = !on
            prefs.putInt("nlOn", if (on) 1 else 0)
        }
        if (on && all.isEmpty()) {
            Text(
                "Todavía no hay avisos guardados. Hace falta el acceso a notificaciones para el launcher.",
                color = Color(0x99FFFFFF), fontSize = 12.sp
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(0.35f)) {
                ActionRow((if (filter.isEmpty()) "✓ " else "") + "Todas") { filter = "" }
                apps.forEach { a ->
                    ActionRow((if (filter == a) "✓ " else "") + label(a)) { filter = a }
                }
            }
            Column(Modifier.weight(0.65f)) {
                var lastDay = ""
                shown.forEach { e ->
                    val day = dayLabel(e.t)
                    if (day != lastDay) {
                        Text(day, color = Color(0xB3FFFFFF), fontSize = 13.sp)
                        lastDay = day
                    }
                    ActionRow(hm.format(java.util.Date(e.t)) + " · " + label(e.pkg) + ": " + (e.title + " " + e.text).trim()) {
                        val i = ctx.packageManager.getLaunchIntentForPackage(e.pkg)
                        if (i != null) {
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                ctx.startActivity(i)
                            } catch (ex: Exception) {
                            }
                        }
                    }
                }
            }
        }
        ActionRow("Borrar historial") {
            NotifLog.clear(ctx)
            filter = ""
            tick++
        }
        ActionRow("Cerrar") { onClose() }
    }
}

// ---------- Instalar APK desde USB ----------

private fun usbRoots(): List<File> =
    (File("/storage").listFiles() ?: emptyArray()).filter { it.isDirectory && it.name != "emulated" && it.name != "self" }.sortedBy { it.name }

private fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

private fun sizeStr(f: File): String {
    val mb = f.length() / 1048576L
    return if (mb > 0) "" + mb + " MB" else "" + (f.length() / 1024L) + " KB"
}

private fun sigsOf(pi: PackageInfo): Set<String>? {
    if (Build.VERSION.SDK_INT < 28) return null
    return try {
        pi.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
    } catch (e: Exception) {
        null
    }
}

@Suppress("DEPRECATION")
private fun verCode(pi: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

private fun checkApk(c: Context, f: File): List<String> {
    val pm = c.packageManager
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
    val pi = try {
        pm.getPackageArchiveInfo(f.absolutePath, flags)
    } catch (e: Exception) {
        null
    } ?: return listOf("✗ No es un APK válido")
    val out = mutableListOf("✓ Archivo APK válido", "Paquete: " + pi.packageName, "Versión: " + (pi.versionName ?: "?"))
    val inst = try {
        pm.getPackageInfo(pi.packageName, flags)
    } catch (e: Exception) {
        null
    }
    if (inst == null) {
        out.add("No está instalada: será nueva")
    } else {
        val a = verCode(pi)
        val b = verCode(inst)
        out.add(if (a > b) "Es más nueva que la instalada" else if (a == b) "Misma versión que la instalada" else "✗ Es más antigua que la instalada")
        val s1 = sigsOf(pi)
        val s2 = sigsOf(inst)
        out.add(if (s1 == null || s2 == null) "No se pudo comprobar la firma" else if (s1 == s2) "✓ Misma firma que la instalada" else "✗ Firma distinta: Android no la actualizará")
    }
    return out
}

/** Devuelve un mensaje de error o null si la instalacion se inicio (Android pide la confirmacion). */
private fun installApk(c: Context, f: File): String? {
    if (!c.packageManager.canRequestPackageInstalls()) {
        try {
            val i = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (e: Exception) {
        }
        return "Activa «Instalar apps desconocidas» para el launcher y vuelve a intentarlo."
    }
    return try {
        val pi = c.packageManager.packageInstaller
        val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        pi.openSession(id).use { ss ->
            f.inputStream().use { inp ->
                ss.openWrite("app.apk", 0, f.length()).use { out ->
                    inp.copyTo(out)
                    ss.fsync(out)
                }
            }
            val it = Intent(c, InstallReceiver::class.java).setAction("com.mini.launcher.INSTALL_DONE")
            val fl = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            ss.commit(PendingIntent.getBroadcast(c, id, it, fl).intentSender)
        }
        null
    } catch (e: Exception) {
        "No se pudo iniciar la instalación."
    }
}

@Composable
fun UsbInstallDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var dir by remember { mutableStateOf<File?>(null) }
    var sel by remember { mutableStateOf<File?>(null) }
    var msg by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val allowed = remember(tick) { hasAllFiles() }
    val entries = remember(dir, tick, allowed) {
        val d = dir
        if (!allowed) {
            emptyList()
        } else if (d == null) {
            usbRoots()
        } else {
            (d.listFiles() ?: emptyArray())
                .filter { !it.name.startsWith(".") && (it.isDirectory || it.name.endsWith(".apk", ignoreCase = true)) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }
    val check = remember(sel) {
        val s = sel
        if (s == null) emptyList() else checkApk(ctx, s)
    }
    ExtraDialog("Instalar APK desde USB", onClose, 900, scroll = true) {
        if (!allowed) {
            Text(
                "Falta el permiso «Acceso a todos los archivos». Puedes darlo por ADB con: " +
                    "appops set com.mini.launcher MANAGE_EXTERNAL_STORAGE allow",
                color = Color(0xE6FFFFFF), fontSize = 14.sp
            )
            ActionRow("Abrir el ajuste del permiso") {
                try {
                    val i = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                } catch (e: Exception) {
                    msg = "Esta TV no tiene esa pantalla: usa el comando ADB."
                }
            }
            ActionRow("Ya lo concedí: revisar") { tick++ }
        } else {
            val d = dir
            Text(d?.absolutePath ?: "Memorias USB conectadas", color = Color(0xB3FFFFFF), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(0.6f)) {
                    if (d != null) {
                        ActionRow("📁  .. (subir)") {
                            val p = d.parentFile
                            dir = if (p == null || p.absolutePath == "/storage") null else p
                            sel = null
                        }
                    }
                    if (entries.isEmpty()) {
                        Text(if (d == null) "No encuentro ninguna memoria USB." else "No hay carpetas ni APK aquí.", color = Color(0xB3FFFFFF), fontSize = 14.sp)
                    }
                    entries.take(80).forEach { f ->
                        ActionRow((if (f.isDirectory) "📁  " else "📄  ") + f.name + (if (f.isDirectory) "" else "  (" + sizeStr(f) + ")")) {
                            if (f.isDirectory) {
                                dir = f
                                sel = null
                            } else {
                                sel = f
                                msg = ""
                            }
                        }
                    }
                }
                Column(Modifier.weight(0.4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Comprobación", color = Color.White, fontSize = 16.sp)
                    if (check.isEmpty()) Text("Elige un APK de la lista.", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                    check.forEach { Text(it, color = Color(0xE6FFFFFF), fontSize = 13.sp) }
                    val s = sel
                    if (s != null && check.isNotEmpty() && check[0].startsWith("✓")) {
                        ActionRow("Instalar") { msg = installApk(ctx, s) ?: "Android te pedirá confirmar la instalación." }
                    }
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = Color(0xFFFFD36B), fontSize = 13.sp)
        ActionRow("Cerrar") { onClose() }
    }
}
