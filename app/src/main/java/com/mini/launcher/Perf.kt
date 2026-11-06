package com.mini.launcher

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

class Health(
    val ramMb: Long, val ramPct: Int, val ramLight: Int,
    val storGb: Double, val storPct: Int, val storLight: Int,
    val wifiOn: Boolean, val rssi: Int, val linkMbps: Int, val wifiLight: Int,
    val upMin: Long, val upLight: Int
)

class AppSize(val pkg: String, val label: String, val bytes: Long, val cache: Long)

/** Rendimiento: limpiar RAM, salud, red, almacenamiento, apps lentas y zombi, ahorro automatico. */
object Perf {
    var autoFluid by mutableStateOf(false)
        private set
    var autoSaver by mutableStateOf(false)
        private set
    var nightClean by mutableStateOf(false)
        private set
    var warmOn by mutableStateOf(false)
        private set
    var warmHour by mutableIntStateOf(19)
        private set
    var warmPkg by mutableStateOf("")
        private set

    @Volatile
    private var low = false

    @Volatile
    private var pendPkg = ""

    @Volatile
    private var pendAt = 0L

    fun load(c: Context) {
        val p = Prefs(c)
        autoFluid = p.getInt("pfAutoFluid", 0) == 1
        autoSaver = p.getInt("pfAutoSaver", 0) == 1
        nightClean = p.getInt("pfNightClean", 0) == 1
        warmOn = p.getInt("pfWarmOn", 0) == 1
        warmHour = p.getInt("pfWarmHour", 19).coerceIn(0, 23)
        warmPkg = p.getStr("pfWarmPkg", "")
    }

    fun setAutoFluid(c: Context, v: Boolean) { autoFluid = v; Prefs(c).putInt("pfAutoFluid", if (v) 1 else 0) }
    fun setAutoSaver(c: Context, v: Boolean) { autoSaver = v; Prefs(c).putInt("pfAutoSaver", if (v) 1 else 0) }
    fun setNightClean(c: Context, v: Boolean) { nightClean = v; Prefs(c).putInt("pfNightClean", if (v) 1 else 0) }
    fun setWarmOn(c: Context, v: Boolean) { warmOn = v; Prefs(c).putInt("pfWarmOn", if (v) 1 else 0) }
    fun setWarmHour(c: Context, v: Int) { warmHour = v.coerceIn(0, 23); Prefs(c).putInt("pfWarmHour", warmHour) }
    fun setWarmPkg(c: Context, v: String) { warmPkg = v; Prefs(c).putStr("pfWarmPkg", v) }

    // ----- Memoria -----
    private fun memInfo(c: Context): ActivityManager.MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi
    }

    /** Con poca RAM libre (con margen para no oscilar) el launcher baja sus efectos solo. */
    fun ramLow(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.isLowRamDevice) return true
        val mb = memInfo(c).availMem / 1048576L
        low = if (low) mb < 500L else mb < 350L
        return low
    }

    /** Cierra procesos en segundo plano de las apps instaladas. Devuelve los MB liberados. */
    suspend fun cleanMemory(c: Context): Long = withContext(Dispatchers.IO) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = memInfo(c).availMem
        val home = Home1.current(c)
        for (a in AppCache.apps) {
            if (a.pkg == c.packageName || a.pkg == home) continue
            try { am.killBackgroundProcesses(a.pkg) } catch (e: Exception) { }
        }
        System.gc()
        delay(700)
        ((memInfo(c).availMem - before) / 1048576L).coerceAtLeast(0L)
    }

    fun cleanAsync(c: Context, quiet: Boolean = false) {
        val app = c.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val mb = cleanMemory(app)
            if (!quiet) withContext(Dispatchers.Main) { toast(app, "Memoria limpiada: +$mb MB libres.") }
        }
    }

    // ----- Salud -----
    fun health(c: Context): Health {
        val mi = memInfo(c)
        val ramPct = if (mi.totalMem > 0) (mi.availMem * 100 / mi.totalMem).toInt() else 0
        val ramLight = if (ramPct >= 25) 0 else if (ramPct >= 12) 1 else 2
        var storGb = 0.0
        var storPct = 0
        try {
            val sf = StatFs(android.os.Environment.getDataDirectory().path)
            storGb = sf.availableBytes / 1073741824.0
            storPct = if (sf.totalBytes > 0) (sf.availableBytes * 100 / sf.totalBytes).toInt() else 0
        } catch (e: Exception) { }
        val storLight = if (storPct >= 15) 0 else if (storPct >= 7) 1 else 2
        var wifiOn = false
        var rssi = -127
        var link = 0
        try {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val wi = wm.connectionInfo
            if (wi != null && wi.networkId != -1) {
                wifiOn = true
                rssi = wi.rssi
                link = wi.linkSpeed
            }
        } catch (e: Exception) { }
        val wifiLight = if (!wifiOn) 3 else if (rssi >= -62) 0 else if (rssi >= -74) 1 else 2
        val upMin = SystemClock.elapsedRealtime() / 60000L
        val upLight = if (upMin < 3 * 1440) 0 else if (upMin < 7 * 1440) 1 else 2
        return Health(
            mi.availMem / 1048576L, ramPct, ramLight, storGb, storPct, storLight,
            wifiOn, rssi, link, wifiLight, upMin, upLight
        )
    }

    fun upText(min: Long): String =
        if (min >= 1440) (min / 1440).toString() + " d " + (min % 1440 / 60) + " h"
        else (min / 60).toString() + " h " + (min % 60) + " min"

    // ----- Red -----
    suspend fun pingMs(): Int = withContext(Dispatchers.IO) {
        var sum = 0
        var ok = 0
        for (i in 0 until 3) {
            val s = Socket()
            try {
                val t0 = System.nanoTime()
                s.connect(InetSocketAddress("1.1.1.1", 443), 2500)
                sum += ((System.nanoTime() - t0) / 1_000_000L).toInt()
                ok++
            } catch (e: Exception) {
            } finally {
                try { s.close() } catch (e: Exception) { }
            }
        }
        if (ok == 0) -1 else sum / ok
    }

    suspend fun speedMbps(): Double = withContext(Dispatchers.IO) {
        var con: HttpURLConnection? = null
        try {
            con = URL("https://speed.cloudflare.com/__down?bytes=10000000").openConnection() as HttpURLConnection
            con.connectTimeout = 4000
            con.readTimeout = 4000
            val buf = ByteArray(65536)
            var total = 0L
            val t0 = System.nanoTime()
            val ins = con.inputStream
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (System.nanoTime() - t0 > 6_000_000_000L) break
            }
            ins.close()
            val secs = (System.nanoTime() - t0) / 1e9
            if (secs <= 0.0) 0.0 else total * 8 / 1e6 / secs
        } catch (e: Exception) {
            0.0
        } finally {
            con?.disconnect()
        }
    }

    // ----- Permiso de uso y almacenamiento -----
    fun usageOk(c: Context): Boolean = try {
        val ao = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun askUsage(c: Context) {
        if (!tryStart(c, "android.settings.USAGE_ACCESS_SETTINGS")) {
            toast(c, "Por ADB: appops set com.mini.launcher GET_USAGE_STATS allow")
        }
    }

    fun fmt(b: Long): String =
        if (b >= 1073741824L) String.format(Locale.getDefault(), "%.1f GB", b / 1073741824.0)
        else (b / 1048576L).toString() + " MB"

    suspend fun storageMap(c: Context): List<AppSize> = withContext(Dispatchers.IO) {
        val ok = usageOk(c)
        val pm = c.packageManager
        val ssm = c.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        AppCache.apps.map { a ->
            try {
                if (ok) {
                    val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, a.pkg, Process.myUserHandle())
                    AppSize(a.pkg, a.label, s.appBytes + s.dataBytes + s.cacheBytes, s.cacheBytes)
                } else {
                    AppSize(a.pkg, a.label, File(pm.getApplicationInfo(a.pkg, 0).sourceDir).length(), 0L)
                }
            } catch (e: Exception) {
                AppSize(a.pkg, a.label, 0L, 0L)
            }
        }.sortedByDescending { it.bytes }
    }

    fun uninstall(c: Context, pkg: String) {
        try {
            c.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(c, "No se pudo abrir el desinstalador.")
        }
    }

    fun appSettings(c: Context, pkg: String) {
        try {
            c.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast(c, "No se pudo abrir los ajustes de la app.")
        }
    }

    fun stop(c: Context, pkg: String) {
        try {
            (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
            toast(c, "Detenida en segundo plano.")
        } catch (e: Exception) {
        }
    }

    // ----- Tiempo de apertura -----
    fun noteLaunch(pkg: String) {
        pendPkg = pkg
        pendAt = System.currentTimeMillis()
    }

    /** Al volver al launcher: mide cuanto tardo la ultima app en llegar a primer plano. */
    fun resolveLaunch(c: Context) {
        val pkg = pendPkg
        val at = pendAt
        pendPkg = ""
        if (pkg.isEmpty() || !usageOk(c)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val ev = usm.queryEvents(at - 500L, System.currentTimeMillis())
                val e = UsageEvents.Event()
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e)
                    if (e.packageName == pkg && e.eventType == 1) {
                        val d = e.timeStamp - at
                        if (d in 1..30000) record(c, pkg, d)
                        break
                    }
                }
            } catch (x: Exception) {
            }
        }
    }

    private fun record(c: Context, pkg: String, ms: Long) {
        val p = Prefs(c)
        val m = LinkedHashMap<String, LongArray>()
        for (part in p.getStr("pfLaunchT", "").split(",")) {
            val f = part.split(":")
            if (f.size == 3) m[f[0]] = longArrayOf(f[1].toLongOrNull() ?: 0L, f[2].toLongOrNull() ?: 0L)
        }
        val cur = m[pkg] ?: longArrayOf(0L, 0L)
        // Ventana movil: a las 20 medidas se reduce a la mitad para que pese lo reciente.
        if (cur[1] >= 20L) {
            cur[0] = cur[0] / 2L
            cur[1] = cur[1] / 2L
        }
        cur[0] = cur[0] + ms
        cur[1] = cur[1] + 1L
        m[pkg] = cur
        p.putStr("pfLaunchT", m.entries.joinToString(",") { it.key + ":" + it.value[0] + ":" + it.value[1] })
    }

    /** Apps ordenadas de mas lenta a mas rapida: (paquete, media ms, veces). */
    fun slowRanking(c: Context): List<Triple<String, Long, Long>> {
        val out = ArrayList<Triple<String, Long, Long>>()
        for (part in Prefs(c).getStr("pfLaunchT", "").split(",")) {
            val f = part.split(":")
            if (f.size == 3) {
                val s = f[1].toLongOrNull() ?: continue
                val n = f[2].toLongOrNull() ?: continue
                if (n > 0) out.add(Triple(f[0], s / n, n))
            }
        }
        return out.sortedByDescending { it.second }
    }

    // ----- Zombis -----
    /** Apps de usuario que arrancan solas con la TV y que nunca abres desde el launcher. */
    fun zombies(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val boot = try {
            @Suppress("DEPRECATION")
            pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), 0)
                .mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val usage = Prefs(c).usage()
        return AppCache.apps.filter { a ->
            a.pkg != c.packageName && a.pkg in boot && (usage[a.pkg] ?: 0) == 0 && try {
                pm.getApplicationInfo(a.pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ----- Pre-calentar -----
    /** Abre la app favorita unos segundos para dejarla en memoria y vuelve al launcher. */
    fun warm(c: Context) {
        val a = AppCache.apps.firstOrNull { it.pkg == warmPkg } ?: return
        try {
            c.startActivity(Intent(a.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    c.startActivity(
                        Intent(c, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )
                } catch (e: Exception) { }
            }, 5000L)
        } catch (e: Exception) {
        }
    }
}

/** Fila con semaforo: 0 verde, 1 amarillo, 2 rojo, otro = gris. */
@Composable
fun SemRow(label: String, value: String, level: Int) {
    val col = when (level) {
        0 -> Color(0xFF4CAF50)
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFFF44336)
        else -> Color(0x66FFFFFF)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(col))
        Text(label, color = Color(0xA6FFFFFF), fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(value, color = Color(0xF2FFFFFF), fontSize = 16.sp)
    }
}

@Composable
fun PerfDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var sub by remember { mutableStateOf("") }
    val apps = remember { AppCache.apps.sortedBy { it.label.lowercase() } }
    val names = remember { listOf("Ninguna") + apps.map { it.label } }
    val idx = (apps.indexOfFirst { it.pkg == Perf.warmPkg } + 1).coerceAtLeast(0)
    ExtraDialog("Rendimiento", onClose, scroll = true) {
        ActionRow("Salud de la TV") { sub = "health" }
        ActionRow("Diagnóstico de red (ping y velocidad)") { sub = "net" }
        ActionRow("Almacenamiento por app") { sub = "storage" }
        ActionRow("Apps más lentas al abrir") { sub = "slow" }
        ActionRow("Apps zombi (arrancan solas)") { sub = "zombie" }
        ActionRow("Limpiar memoria ahora") { Perf.cleanAsync(c) }
        SwitchRow("Fluidez automática si va lenta", Perf.autoFluid) { Perf.setAutoFluid(c, it) }
        SwitchRow("Modo ahorro automático con poca RAM", Perf.autoSaver) { Perf.setAutoSaver(c, it) }
        SwitchRow("Limpiar memoria cada noche (3:00)", Perf.nightClean) { Perf.setNightClean(c, it) }
        SwitchRow("Pre-calentar una app a su hora", Perf.warmOn) { Perf.setWarmOn(c, it) }
        if (Perf.warmOn) {
            ChoiceRow("App a pre-calentar", names, idx) { i ->
                Perf.setWarmPkg(c, if (i == 0) "" else apps[i - 1].pkg)
            }
            SliderRow("Hora de pre-calentado", Perf.warmHour, 0, 23, 1, " h") { Perf.setWarmHour(c, it) }
        }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
    when (sub) {
        "health" -> HealthDialog { sub = "" }
        "net" -> NetTestDialog { sub = "" }
        "storage" -> StorageDialog { sub = "" }
        "slow" -> SlowAppsDialog { sub = "" }
        "zombie" -> ZombieDialog { sub = "" }
    }
}

@Composable
fun HealthDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableIntStateOf(0) }
    var sub by remember { mutableStateOf("") }
    val h = remember(t) { Perf.health(c) }
    ExtraDialog("Salud de la TV", onClose, scroll = true) {
        SemRow("Memoria libre", h.ramMb.toString() + " MB (" + h.ramPct + "%)", h.ramLight)
        if (h.ramLight > 0) ActionRow("Arreglar: limpiar memoria") { Perf.cleanAsync(c); t++ }
        SemRow(
            "Almacenamiento libre",
            String.format(Locale.getDefault(), "%.1f GB (%d%%)", h.storGb, h.storPct), h.storLight
        )
        if (h.storLight > 0) ActionRow("Arreglar: ver qué ocupa más") { sub = "storage" }
        SemRow(
            "Wi‑Fi",
            if (h.wifiOn) h.rssi.toString() + " dBm · " + h.linkMbps + " Mbps" else "sin Wi‑Fi",
            h.wifiLight
        )
        if (h.wifiLight in 1..2) ActionRow("Arreglar: abrir ajustes de Wi‑Fi") { openSystem(c, "wifi") }
        SemRow("Encendida hace", Perf.upText(h.upMin), h.upLight)
        if (h.upLight > 0) ActionRow("Arreglar: reiniciar (menú de energía)") { systemPower(c, 1) }
        ActionRow("Actualizar") { t++ }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
    if (sub == "storage") StorageDialog { sub = "" }
}

@Composable
fun NetTestDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var run by remember { mutableIntStateOf(1) }
    var ping by remember { mutableStateOf("…") }
    var speed by remember { mutableStateOf("…") }
    var busy by remember { mutableStateOf(true) }
    val h = remember(run) { Perf.health(c) }
    LaunchedEffect(run) {
        busy = true
        ping = "…"
        speed = "…"
        val p = Perf.pingMs()
        ping = if (p < 0) "sin respuesta" else "$p ms"
        val s = Perf.speedMbps()
        speed = if (s <= 0.0) "sin respuesta" else String.format(Locale.getDefault(), "%.1f Mbps", s)
        busy = false
    }
    ExtraDialog("Diagnóstico de red", onClose, scroll = true) {
        InfoRow("Ping", ping)
        InfoRow("Velocidad de bajada", speed)
        InfoRow("Señal Wi‑Fi", if (h.wifiOn) h.rssi.toString() + " dBm · enlace " + h.linkMbps + " Mbps" else "sin Wi‑Fi")
        ActionRow(if (busy) "Midiendo…" else "Repetir prueba") { if (!busy) run++ }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}

@Composable
fun StorageDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var list by remember { mutableStateOf<List<AppSize>?>(null) }
    var sel by remember { mutableStateOf<AppSize?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val ok = remember(tick) { Perf.usageOk(c) }
    LaunchedEffect(tick) { list = Perf.storageMap(c) }
    ExtraDialog("Almacenamiento por app", onClose, scroll = true) {
        if (!ok) {
            Text(
                "Sin el permiso de uso solo se ve el tamaño de la app, no sus datos.",
                color = Color(0xB3FFFFFF), fontSize = 13.sp
            )
            ActionRow("Dar permiso de uso") { Perf.askUsage(c) }
            ActionRow("Ya lo di: actualizar") { list = null; tick++ }
        }
        val l = list
        if (l == null) {
            Text("Calculando…", color = Color(0xE6FFFFFF), fontSize = 15.sp)
        } else {
            for (a in l.take(15)) ActionRow(a.label + " · " + Perf.fmt(a.bytes)) { sel = a }
        }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
    sel?.let { a ->
        AppActionDialog(a.pkg, a.label, if (a.cache > 0) "Caché: " + Perf.fmt(a.cache) else "") { sel = null }
    }
}

@Composable
fun AppActionDialog(pkg: String, label: String, detail: String, onClose: () -> Unit) {
    val c = LocalContext.current
    ExtraDialog(label, onClose) {
        if (detail.isNotEmpty()) Text(detail, color = Color(0xB3FFFFFF), fontSize = 14.sp)
        ActionRow("Desinstalar") { Perf.uninstall(c, pkg); onClose() }
        ActionRow("Limpiar caché o datos (abre sus ajustes)") { Perf.appSettings(c, pkg); onClose() }
        ActionRow("Detener en segundo plano") { Perf.stop(c, pkg); onClose() }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}

@Composable
fun SlowAppsDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    val ok = remember { Perf.usageOk(c) }
    val rank = remember { Perf.slowRanking(c) }
    ExtraDialog("Apps más lentas al abrir", onClose, scroll = true) {
        if (!ok) {
            Text(
                "Para medir la apertura hace falta el permiso de uso.",
                color = Color(0xB3FFFFFF), fontSize = 13.sp
            )
            ActionRow("Dar permiso de uso") { Perf.askUsage(c) }
        } else if (rank.isEmpty()) {
            Text(
                "Aún no hay datos: abre algunas apps desde el launcher y vuelve.",
                color = Color(0xE6FFFFFF), fontSize = 14.sp
            )
        }
        for ((pkg, ms, n) in rank.take(10)) {
            val name = AppCache.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
            InfoRow(name, String.format(Locale.getDefault(), "%.1f s (%d)", ms / 1000.0, n))
        }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}

@Composable
fun ZombieDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val z = remember(tick) { Perf.zombies(c) }
    var sel by remember { mutableStateOf<AppInfo?>(null) }
    ExtraDialog("Apps zombi", onClose, scroll = true) {
        Text(
            "Arrancan solas al encender la TV y nunca las abres desde aquí.",
            color = Color(0xB3FFFFFF), fontSize = 13.sp
        )
        if (z.isEmpty()) Text("No se encontró ninguna.", color = Color(0xE6FFFFFF), fontSize = 15.sp)
        for (a in z) ActionRow(a.label) { sel = a }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
    sel?.let { a -> AppActionDialog(a.pkg, a.label, "Arranque automático") { sel = null; tick++ } }
}
