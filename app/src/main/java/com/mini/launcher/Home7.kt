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

@Suppress("DEPRECATION")
private fun verCode(pi: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

