package com.mini.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppInfo(
    val pkg: String,
    val label: String,
    val art: ImageBitmap,
    val banner: Boolean,
    val intent: Intent,
    val glow: Int
)

object AppCache {
    @Volatile
    var apps: List<AppInfo> = emptyList()
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    // Dock por franja horaria (opcional): cada franja guarda su propia lista.
    private fun favsKey(): String =
        if (sp.getInt("dSlots", 0) == 1) "favs" + Daily.dayPart() else "favs"
    fun favs(): List<String> {
        val k = favsKey()
        val v = if (sp.contains(k)) sp.getString(k, "") else sp.getString("favs", "")
        return (v ?: "").split(",").filter { it.isNotEmpty() }
    }
    fun saveFavs(l: List<String>) {
        sp.edit().putString(favsKey(), l.joinToString(",")).apply()
    }
    fun hidden(): Set<String> = (sp.getStringSet("hidden", emptySet()) ?: emptySet()).toSet()
    fun saveHidden(s: Set<String>) {
        sp.edit().putStringSet("hidden", s).apply()
    }
    fun recents(): List<String> =
        (sp.getString("recents", "") ?: "").split(",").filter { it.isNotEmpty() }
    fun pushRecent(p: String): List<String> {
        val l = (listOf(p) + recents().filter { it != p }).take(10)
        sp.edit().putString("recents", l.joinToString(",")).apply()
        return l
    }
    fun usage(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (part in (sp.getString("usage", "") ?: "").split(",")) {
            val p = part.split(":")
            if (p.size == 2) {
                val v = p[1].toIntOrNull()
                if (v != null) out[p[0]] = v
            }
        }
        return out
    }
    fun bumpUsage(p: String): Map<String, Int> {
        val m = HashMap(usage())
        m[p] = (m[p] ?: 0) + 1
        sp.edit().putString("usage", m.entries.joinToString(",") { it.key + ":" + it.value }).apply()
        return m
    }
    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            when (v) {
                is Int, is Long, is Boolean, is String -> o.put(k, v)
                is Set<*> -> o.put(k, JSONArray(v.map { it.toString() }))
                else -> {}
            }
        }
        return o.toString(2)
    }
    fun importJson(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val e = sp.edit()
            e.clear()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = o.get(k)) {
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is String -> e.putString(k, v)
                    is JSONArray -> e.putStringSet(k, (0 until v.length()).map { i -> v.getString(i) }.toSet())
                    else -> {}
                }
            }
            e.commit()
        } catch (ex: Exception) {
            false
        }
    }
    /** Cuantas de estas claves ya fueron guardadas alguna vez (ajustes que tocaste). */
    fun countKeys(keys: Collection<String>): Int = keys.count { sp.contains(it) }
    /** Borra solo estas claves (restablecer una categoria). */
    fun removeKeys(keys: Collection<String>) {
        val e = sp.edit()
        for (k in keys) e.remove(k)
        e.commit()
    }
    /** Copia solo las claves dadas (diseño exportable). */
    fun exportKeysJson(keys: Collection<String>): String {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            if (k !in keys) continue
            when (v) {
                is Int, is Long, is Boolean, is String -> o.put(k, v)
                else -> {}
            }
        }
        o.put("_design", 1)
        return o.toString(2)
    }
    /** Aplica solo las claves permitidas del archivo, sin borrar nada mas. false si no hay ninguna. */
    fun importKeysJson(text: String, keys: Collection<String>): Boolean {
        return try {
            val o = JSONObject(text)
            val e = sp.edit()
            var n = 0
            for (k in keys) {
                if (!o.has(k)) continue
                var ok = true
                when (val v = o.get(k)) {
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is String -> e.putString(k, v)
                    else -> ok = false
                }
                if (ok) n++
            }
            if (n == 0) false else e.commit()
        } catch (ex: Exception) {
            false
        }
    }
    fun getStr(k: String, d: String): String = sp.getString(k, d) ?: d
    fun putStr(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }
    fun getInt(k: String, d: Int): Int = sp.getInt(k, d)
    fun putInt(k: String, v: Int) {
        sp.edit().putInt(k, v).apply()
    }
    fun sizes(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (part in (sp.getString("sizes", "") ?: "").split(",")) {
            val p = part.split(":")
            if (p.size == 2) {
                val v = p[1].toIntOrNull()
                if (v != null) out[p[0]] = v
            }
        }
        return out
    }
    fun saveSizes(m: Map<String, Int>) {
        sp.edit().putString("sizes", m.entries.joinToString(",") { it.key + ":" + it.value }).apply()
    }
    fun rowApps(i: Int): List<String> =
        (sp.getString("rowApps$i", "") ?: "").split(",").filter { it.isNotEmpty() }
    fun saveRowApps(i: Int, l: List<String>) {
        sp.edit().putString("rowApps$i", l.joinToString(",")).apply()
    }
    // ---- Mejoras 16, 17, 29, 32, 34: datos extra guardados como JSON de texto ----
    private fun jsonMap(k: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(sp.getString(k, "") ?: "")
            val it = o.keys()
            while (it.hasNext()) {
                val key = it.next()
                out[key] = o.optString(key)
            }
        } catch (e: Exception) {
        }
        return out
    }
    private fun saveJsonMap(k: String, m: Map<String, String>) {
        val o = JSONObject()
        for ((a, b) in m) o.put(a, b)
        sp.edit().putString(k, o.toString()).apply()
    }
    /** Nombres propios de apps (16). */
    fun names(): Map<String, String> = jsonMap("appNames")
    fun setName(pkg: String, v: String) {
        val m = LinkedHashMap(names())
        if (v.isBlank()) m.remove(pkg) else m[pkg] = v.trim()
        saveJsonMap("appNames", m)
    }
    /** Color de brillo propio por app, como numero ARGB en texto (16). */
    fun glows(): Map<String, String> = jsonMap("appGlows")
    fun setGlow(pkg: String, argb: Int?) {
        val m = LinkedHashMap(glows())
        if (argb == null) m.remove(pkg) else m[pkg] = argb.toString()
        saveJsonMap("appGlows", m)
    }
    /** Nombre propio de entradas HDMI y entradas ocultas (29). */
    fun inputNames(): Map<String, String> = jsonMap("inputNames")
    fun setInputName(id: String, v: String) {
        val m = LinkedHashMap(inputNames())
        if (v.isBlank()) m.remove(id) else m[id] = v.trim()
        saveJsonMap("inputNames", m)
    }
    fun hiddenInputs(): Set<String> = (sp.getString("inputHidden", "") ?: "").split("\u0001").filter { it.isNotEmpty() }.toSet()
    fun saveHiddenInputs(s: Set<String>) {
        sp.edit().putString("inputHidden", s.joinToString("\u0001")).apply()
    }
    /** Carpetas del dock (17): nombre -> paquetes separados por coma. */
    fun groups(): Map<String, List<String>> =
        jsonMap("dockGroups").mapValues { e -> e.value.split(",").filter { it.isNotEmpty() } }
    fun saveGroups(g: Map<String, List<String>>) =
        saveJsonMap("dockGroups", g.mapValues { it.value.joinToString(",") })
    /** Apps permitidas en el perfil para ninos (34). */
    fun kidsApps(): Set<String> = (sp.getString("kidsApps", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
    fun saveKidsApps(s: Set<String>) {
        sp.edit().putString("kidsApps", s.joinToString(",")).apply()
    }
    /** Aperturas por dia (32): "paquete:aaaammdd:n", se guardan los ultimos 14 dias. */
    private fun dayKey(offset: Int = 0): String {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -offset)
        return java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(c.time)
    }
    private fun dayMap(): MutableMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (part in (sp.getString("usageDays", "") ?: "").split(",")) {
            val p = part.split(":")
            if (p.size == 3) {
                val n = p[2].toIntOrNull()
                if (n != null) out[p[0] + ":" + p[1]] = n
            }
        }
        return out
    }
    fun bumpDay(pkg: String) {
        val m = dayMap()
        val old = dayKey(14)
        m.keys.removeAll { it.substringAfter(":") < old }
        val k = pkg + ":" + dayKey()
        m[k] = (m[k] ?: 0) + 1
        sp.edit().putString("usageDays", m.entries.joinToString(",") { it.key + ":" + it.value }).apply()
    }
    /** Aperturas de los ultimos 7 dias por app. */
    fun weekUsage(): Map<String, Int> {
        val from = dayKey(6)
        val out = HashMap<String, Int>()
        for ((k, n) in dayMap()) {
            if (k.substringAfter(":") >= from) {
                val pkg = k.substringBefore(":")
                out[pkg] = (out[pkg] ?: 0) + n
            }
        }
        return out
    }
    // Apps ya vistas: las que no estan aqui se marcan como "NUEVA".
    fun hasSeen(): Boolean = sp.contains("seen")
    fun seen(): Set<String> =
        (sp.getString("seen", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()
    fun saveSeen(s: Set<String>) {
        sp.edit().putString("seen", s.joinToString(",")).apply()
    }
    fun markSeen(p: String): Set<String> {
        val s = seen() + p
        saveSeen(s)
        return s
    }
    var wallpaper: String
        get() = sp.getString("wall", "") ?: ""
        set(v) { sp.edit().putString("wall", v).apply() }
    var initDone: Boolean
        get() = sp.getBoolean("init", false)
        set(v) { sp.edit().putBoolean("init", v).apply() }
}

fun dominantColor(b: android.graphics.Bitmap): Int {
    val s = android.graphics.Bitmap.createScaledBitmap(b, 16, 16, true)
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var bl = 0.0
    var wsum = 0.0
    for (y in 0 until 16) {
        for (x in 0 until 16) {
            val p = s.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            android.graphics.Color.colorToHSV(p, hsv)
            val w = (hsv[1] * hsv[2]).toDouble() + 0.001
            r += android.graphics.Color.red(p) * w
            g += android.graphics.Color.green(p) * w
            bl += android.graphics.Color.blue(p) * w
            wsum += w
        }
    }
    if (wsum <= 0.0) return 0xFFFFFFFF.toInt()
    val c = android.graphics.Color.rgb((r / wsum).toInt(), (g / wsum).toInt(), (bl / wsum).toInt())
    android.graphics.Color.colorToHSV(c, hsv)
    if (hsv[1] < 0.08f) return 0xFFFFFFFF.toInt()
    hsv[1] = maxOf(hsv[1], 0.6f)
    hsv[2] = maxOf(hsv[2], 0.9f)
    return android.graphics.Color.HSVToColor(hsv)
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val map = LinkedHashMap<String, AppInfo>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || map.containsKey(pkg)) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            val bn = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val isBanner = bn != null
            val bmp = if (bn != null) bn.toBitmap(288, 168) else ri.loadIcon(pm).toBitmap(128, 128)
            map[pkg] = AppInfo(
                pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, launch,
                dominantColor(bmp)
            )
        }
    }
    return map.values.sortedBy { it.label.lowercase() }
}

fun wallDir(ctx: Context): File {
    val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    return File(base, "wallpapers").also { it.mkdirs() }
}

fun listWalls(ctx: Context): List<File> =
    (wallDir(ctx).listFiles() ?: emptyArray())
        .filter {
            it.extension.lowercase() in setOf(
                "png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mkv", "3gp"
            )
        }
        .sortedBy { it.name }

fun loadBitmap(f: File, target: Int): ImageBitmap? {
    if (f.extension.lowercase() in setOf("mp4", "webm", "mkv", "3gp")) {
        return try {
            val r = MediaMetadataRetriever()
            r.setDataSource(f.path)
            val b = r.getFrameAtTime(1_000_000L)
            r.release()
            b?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= target) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    return BitmapFactory.decodeFile(f.path, o2)?.asImageBitmap()
}

fun importWall(ctx: Context, uri: Uri): String? {
    val mime = ctx.contentResolver.getType(uri) ?: ""
    val ext = when {
        mime == "image/gif" -> "gif"
        mime == "image/webp" -> "webp"
        mime == "image/png" -> "png"
        mime == "video/webm" -> "webm"
        mime.startsWith("video/") -> "mp4"
        else -> "jpg"
    }
    val name = "fondo_" + System.currentTimeMillis() + "." + ext
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            File(wallDir(ctx), name).outputStream().use { out -> input.copyTo(out) }
        }
        name
    } catch (e: Exception) {
        null
    }
}

fun move(list: List<String>, p: String, d: Int): List<String> {
    val i = list.indexOf(p)
    val j = i + d
    if (i < 0 || j < 0 || j >= list.size) return list
    val m = list.toMutableList()
    m.removeAt(i)
    m.add(j, p)
    return m
}

fun tryStart(ctx: Context, t: String): Boolean {
    return try {
        val i = Intent()
        if (t.contains("/")) {
            i.component = ComponentName.unflattenFromString(t)
        } else {
            i.action = t
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}

fun openSystem(ctx: Context, key: String) {
    val targets: List<String> = when (key) {
        "wifi" -> listOf(
            "android.settings.WIFI_SETTINGS",
            "com.android.tv.settings/.connectivity.NetworkActivity"
        )
        "bt" -> listOf(
            "android.settings.BLUETOOTH_SETTINGS",
            "com.android.tv.settings/.accessories.AddAccessoryActivity",
            "com.android.tv.settings/.accessories.BluetoothAccessoryActivity"
        )
        "sound" -> listOf(
            "android.settings.SOUND_SETTINGS",
            "com.android.tv.settings/.device.sound.SoundActivity"
        )
        "display" -> listOf(
            "android.settings.DISPLAY_SETTINGS",
            "com.android.tv.settings/.device.display.DisplayActivity"
        )
        "access" -> listOf("android.settings.ACCESSIBILITY_SETTINGS")
        "dream" -> listOf(
            "android.settings.DREAM_SETTINGS",
            "android.settings.DISPLAY_SETTINGS"
        )
        else -> listOf("android.settings.SETTINGS")
    }
    for (t in targets) {
        if (tryStart(ctx, t)) return
    }
    tryStart(ctx, "android.settings.SETTINGS")
}

/** Abre el asistente de voz del sistema. Devuelve false si la TV no tiene uno. */
fun launchAssistant(ctx: Context): Boolean {
    val actions = listOf(
        "android.intent.action.VOICE_COMMAND",
        "android.intent.action.ASSIST",
        "android.intent.action.VOICE_ASSIST"
    )
    for (a in actions) {
        if (tryIntent(ctx, Intent(a))) return true
    }
    return false
}

fun backupFile(ctx: Context): File {
    val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    return File(base, "ajustes_launcher.json")
}

fun writeBackup(ctx: Context, prefs: Prefs): Boolean =
    try {
        backupFile(ctx).writeText(prefs.exportJson())
        true
    } catch (e: Exception) {
        false
    }

fun readBackup(ctx: Context, prefs: Prefs): Boolean {
    val f = backupFile(ctx)
    if (!f.exists()) return false
    return try {
        prefs.importJson(f.readText())
    } catch (e: Exception) {
        false
    }
}
