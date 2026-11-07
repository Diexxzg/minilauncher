package com.mini.launcher

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Modo seguro: si el launcher se cierra 3 veces seguidas al arrancar, vuelve a ajustes mínimos. */
object Safe {
    private val t0 = SystemClock.elapsedRealtime()
    private fun sp(c: Context) = c.getSharedPreferences("safe", Context.MODE_PRIVATE)

    fun safeFile(c: Context): File =
        File(c.getExternalFilesDir(null) ?: c.filesDir, "ajustes_antes_modo_seguro.json")

    /** Se llama desde el manejador de cierres: cuenta solo los que ocurren en los primeros 20 s. */
    fun onCrash(c: Context) {
        if (SystemClock.elapsedRealtime() - t0 < 20_000L) {
            val s = sp(c)
            s.edit().putInt("quick", s.getInt("quick", 0) + 1).commit()
        }
    }

    /** Al arrancar: con 3 fallos seguidos guarda copia y deja solo dock, ocultas y diseño base. */
    fun check(c: Context) {
        try {
            val s = sp(c)
            if (s.getInt("quick", 0) < 3) return
            safeFile(c).writeText(Prefs(c).exportJson())
            val h = c.getSharedPreferences("home", Context.MODE_PRIVATE)
            val e = h.edit()
            for (k in h.all.keys) {
                if (!(k.startsWith("favs") || k == "hidden" || k == "init")) e.remove(k)
            }
            e.commit()
            s.edit().putInt("quick", 0).putBoolean("notice", true).commit()
        } catch (x: Throwable) {
        }
    }

    fun takeNotice(c: Context): Boolean {
        val s = sp(c)
        val n = s.getBoolean("notice", false)
        if (n) s.edit().putBoolean("notice", false).apply()
        return n
    }

    /** Si el launcher sigue vivo tras 20 s, el arranque se considera bueno. */
    fun armReset(c: Context) {
        val app = c.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ sp(app).edit().putInt("quick", 0).apply() }, 20_000L)
    }
}

/** Copia automática semanal (opcional, apagada por defecto). Guarda las 4 últimas. */
object AutoBackup {
    private fun sp(c: Context) = c.getSharedPreferences("support", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.getExternalFilesDir(null) ?: c.filesDir, "auto")

    fun enabled(c: Context) = sp(c).getInt("autoBak", 0) == 1
    fun setEnabled(c: Context, on: Boolean) {
        sp(c).edit().putInt("autoBak", if (on) 1 else 0).apply()
    }

    fun list(c: Context): List<File> =
        dir(c).listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name } ?: emptyList()

    fun label(f: File): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(f.lastModified()))

    fun runIfDue(c: Context) {
        if (!enabled(c)) return
        val s = sp(c)
        val now = System.currentTimeMillis()
        if (now - s.getLong("autoBakLast", 0L) < 7L * 86_400_000L) return
        val app = c.applicationContext
        Thread {
            try {
                val d = dir(app)
                d.mkdirs()
                val name = "auto_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".json"
                File(d, name).writeText(Prefs(app).exportJson())
                for (f in list(app).drop(4)) f.delete()
                s.edit().putLong("autoBakLast", now).apply()
            } catch (e: Exception) {
            }
        }.start()
    }

    fun restore(c: Context, f: File): Boolean =
        try {
            Prefs(c).importJson(f.readText())
        } catch (e: Exception) {
            false
        }
}

/** Dirección de ayuda: abre un reporte en GitHub con versión, equipo y último error ya escritos. */
fun helpUrl(c: Context): String {
    val v = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "?"
    val last = ErrorLog.read(c).firstOrNull()?.lines()?.take(2)?.joinToString("\n")?.take(160)
        ?: "Sin errores registrados"
    val body = "Versión: $v\nEquipo: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\n" +
        "Último error: $last\n\nProblema:\n"
    return "https://github.com/Diexxzg/minilauncher/issues/new?title=" +
        Uri.encode("Ayuda con One Piece $v") + "&body=" + Uri.encode(body)
}
