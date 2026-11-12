package com.mini.launcher

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aplicacion del launcher. Arranca antes que cualquier pantalla: instala el registro de
 * errores (42) y deja listos los ajustes del clima que lee Weather.kt.
 */
class OnePieceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        ErrorLog.install(this)
        Safe.check(this)
        val p = Prefs(this)
        VolGuard.apply(this)
    }

    companion object {
        @Volatile
        var instance: Application? = null
    }
}

/** Registro sencillo de errores: guarda los ultimos cierres inesperados en un archivo pequeño. */
object ErrorLog {
    private const val FILE = "errors.log"
    private const val SEP = "\n\u0001\n"
    private const val MAX_CHARS = 30000

    @Volatile
    private var dir: File? = null

    fun install(ctx: Context) {
        dir = ctx.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                add("Cierre inesperado en " + t.name, e)
                OnePieceApp.instance?.let { Home1.noteCrash(it); Safe.onCrash(it) }
            } catch (x: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    /** Guarda un error. Se puede llamar desde cualquier parte para anotar algo que fallo. */
    @Synchronized
    fun add(what: String, e: Throwable?) {
        val d = dir ?: return
        try {
            val sw = StringWriter()
            e?.printStackTrace(PrintWriter(sw))
            val lines = sw.toString().lines().take(14)
            val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(Date())
            val entry = stamp + " · " + what + "\n" + lines.joinToString("\n")
            val f = File(d, FILE)
            val old = if (f.exists()) f.readText() else ""
            var all = if (old.isEmpty()) entry else old + SEP + entry
            if (all.length > MAX_CHARS) {
                all = all.takeLast(MAX_CHARS)
                val i = all.indexOf(SEP)
                if (i >= 0) all = all.substring(i + SEP.length)
            }
            f.writeText(all)
        } catch (x: Throwable) {
        }
    }

    /** Errores guardados, el mas reciente primero. */
    fun read(ctx: Context): List<String> {
        return try {
            val f = File(ctx.filesDir, FILE)
            if (!f.exists()) {
                emptyList()
            } else {
                f.readText().split(SEP).filter { it.isNotBlank() }.reversed()
            }
        } catch (x: Throwable) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(ctx: Context) {
        try {
            File(ctx.filesDir, FILE).delete()
        } catch (x: Throwable) {
        }
    }
}
