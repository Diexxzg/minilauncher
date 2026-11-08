package com.mini.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/** Ordenes que manda la app del celular: teclas, volumen y enlaces. Devuelve (codigo HTTP, texto). */
object RemoteCmd {
    fun state(c: Context): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return JSONObject().put("vol", cur * 100 / mx).put("mute", am.isStreamMute(AudioManager.STREAM_MUSIC)).toString()
    }

    fun key(c: Context, n: String, onKey: (Int) -> Unit): Pair<Int, String> {
        val code = when (n) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> 0
        }
        if (code != 0) {
            onKey(code)
            return Pair(200, "ok")
        }
        val svc = PowerService.instance
            ?: return Pair(409, "Activa «One Piece - energía» en Accesibilidad para usar Atrás e Inicio.")
        return when (n) {
            "back" -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); Pair(200, "ok") }
            "home" -> { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); Pair(200, "ok") }
            else -> Pair(400, "Tecla desconocida")
        }
    }

    fun vol(c: Context, d: String?, p: Int?): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        try {
            when {
                d == "up" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                d == "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                d == "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                p != null -> am.setStreamVolume(AudioManager.STREAM_MUSIC, (mx * p.coerceIn(0, 100) / 100f).toInt(), 0)
            }
        } catch (e: Exception) {
        }
        return state(c)
    }

    fun open(c: Context, raw: String): Pair<Int, String> {
        var u = raw.trim()
        if (u.isEmpty()) return Pair(400, "Escribe un enlace")
        if (!u.contains("://")) u = "https://$u"
        val uri = Uri.parse(u)
        if (uri.scheme != "http" && uri.scheme != "https") return Pair(400, "Solo enlaces http o https")
        return try {
            c.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Pair(200, "Abierto en la TV")
        } catch (e: Exception) {
            Pair(500, "La TV no pudo abrir el enlace")
        }
    }

    /** Apps de la TV: [{p: paquete, l: nombre, f: 1 si es favorita}]. */
    fun apps(prefs: Prefs): String {
        val favs = prefs.favs().toSet()
        val a = JSONArray()
        for (x in AppCache.apps) {
            a.put(JSONObject().put("p", x.pkg).put("l", x.label).put("f", if (x.pkg in favs) 1 else 0))
        }
        return a.toString()
    }

    fun launch(c: Context, pkg: String): Pair<Int, String> {
        val app = AppCache.apps.firstOrNull { it.pkg == pkg } ?: return Pair(400, "App no encontrada")
        return try {
            c.startActivity(Intent(app.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Pair(200, "ok")
        } catch (e: Exception) {
            Pair(500, "La TV no pudo abrir la app")
        }
    }

    private fun fb(b: Long): String =
        if (b >= 1L shl 30) String.format("%.1f GB", b / 1073741824.0) else (b shr 20).toString() + " MB"

    /** Pantalla "Acerca de": [{t: titulo, l: [[nombre, valor], ...]}]. */
    @Suppress("DEPRECATION")
    fun about(c: Context): String {
        fun sec(t: String, l: List<Pair<String, String>>): JSONObject {
            val a = JSONArray()
            for (x in l) a.put(JSONArray().put(x.first).put(x.second))
            return JSONObject().put("t", t).put("l", a)
        }
        val pi = try { c.packageManager.getPackageInfo(c.packageName, 0) } catch (e: Exception) { null }
        val out = JSONArray()
        out.put(sec("Versión", listOf("Versión" to (pi?.versionName ?: "?"), "Código de versión" to (pi?.versionCode?.toString() ?: "?"))))
        out.put(sec("Equipo", listOf(
            "Dispositivo" to android.os.Build.MODEL,
            "Fabricante" to android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Android" to android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")",
            "Procesador" to (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?") + " · " + Runtime.getRuntime().availableProcessors() + " núcleos",
            "Apps instaladas" to AppCache.apps.size.toString(),
            "Encendida desde hace" to (android.os.SystemClock.elapsedRealtime() / 60000).let { m -> (m / 60).toString() + " h " + (m % 60) + " min" },
            "Parche de seguridad" to (android.os.Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() } ?: "—")
        )))
        try {
            val wm = c.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val dm = android.util.DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            out.put(sec("Pantalla", listOf(
                "Resolución" to dm.widthPixels.toString() + " × " + dm.heightPixels,
                "Densidad" to dm.densityDpi.toString() + " dpi",
                "Frecuencia" to Math.round(wm.defaultDisplay.refreshRate).toString() + " Hz"
            )))
        } catch (e: Exception) {
        }
        val mem = ArrayList<Pair<String, String>>()
        try {
            val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            mem.add("Almacenamiento" to fb(st.availableBlocksLong * st.blockSizeLong) + " libres de " + fb(st.blockCountLong * st.blockSizeLong))
        } catch (e: Exception) {
        }
        try {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mem.add("Memoria (RAM)" to fb(mi.availMem) + " libres de " + fb(mi.totalMem))
        } catch (e: Exception) {
        }
        if (mem.isNotEmpty()) out.put(sec("Almacenamiento y memoria", mem))
        return out.toString()
    }

    fun sys(c: Context, n: String): Pair<Int, String> {
        val a = when (n) {
            "wifi" -> android.provider.Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> android.provider.Settings.ACTION_SOUND_SETTINGS
            "display" -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            "settings" -> android.provider.Settings.ACTION_SETTINGS
            "dream" -> android.provider.Settings.ACTION_DREAM_SETTINGS
            else -> return Pair(400, "Ajuste desconocido")
        }
        return try {
            c.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Pair(200, "ok")
        } catch (e: Exception) {
            Pair(500, "La TV no tiene esa pantalla")
        }
    }
}
