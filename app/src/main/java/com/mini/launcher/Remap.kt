package com.mini.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import org.json.JSONObject

/** Acciones del remapeo: 0..12 igual que COLOR_ACTIONS, mas las globales. */
val REMAP_ACTIONS = COLOR_ACTIONS + listOf("Atrás", "Inicio", "Apps recientes del sistema", "Pausar / reanudar")

/** Remapeo de botones del mando: codigo de tecla -> "a:N" (accion) o "p:paquete" (abrir app). */
object Remap {
    @Volatile var map: Map<Int, String> = emptyMap()
    @Volatile var learn: ((Int) -> Unit)? = null
    @Volatile var lastKeyMs = 0L

    /** Teclas que no se pueden cambiar para no dejar el mando inservible. */
    fun blocked(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_UNKNOWN -> true
        else -> false
    }

    fun load(c: Context): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        try {
            val o = JSONObject(Prefs(c).getStr("remap", "{}"))
            for (k in o.keys()) k.toIntOrNull()?.let { out[it] = o.getString(k) }
        } catch (e: Exception) {
        }
        map = out
        return out
    }

    fun save(c: Context, m: Map<Int, String>) {
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), v)
        Prefs(c).putStr("remap", o.toString())
        map = m
    }

    fun label(c: Context, v: String): String =
        if (v.startsWith("w:")) {
            "Llamar a " + v.substring(2).removePrefix("https://").removePrefix("http://").take(40)
        } else if (v.startsWith("l:")) {
            "Encender equipo (" + v.substring(2) + ")"
        } else if (v.startsWith("p:")) {
            "Abrir " + (AppCache.apps.firstOrNull { it.pkg == v.substring(2) }?.label ?: v.substring(2))
        } else {
            REMAP_ACTIONS.getOrElse(v.removePrefix("a:").toIntOrNull() ?: 0) { "?" }
        }

    /** Ejecuta la accion desde el servicio de accesibilidad (funciona en cualquier app). */
    fun run(c: Context, svc: AccessibilityService, v: String) {
        try {
            if (runExtra(c, v)) return
            if (v.startsWith("p:")) {
                c.packageManager.getLaunchIntentForPackage(v.substring(2))?.let {
                    c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return
            }
            when (val a = v.removePrefix("a:").toIntOrNull() ?: 0) {
                13 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                14 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                15 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                16 -> {
                    val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                }
                0 -> {}
                else -> c.startActivity(
                    Intent(c, MainActivity::class.java).putExtra("dAct", a)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun say(c: Context, s: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { toast(c, s) }
    }

    /** Acciones de casa: «w:url» llama a una direccion web, «l:MAC» envia Wake-on-LAN. */
    fun runExtra(c: Context, v: String): Boolean {
        if (v.startsWith("w:")) {
            val u = v.substring(2)
            Thread {
                try {
                    val k = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    k.connectTimeout = 4000
                    k.readTimeout = 4000
                    val code = k.responseCode
                    k.disconnect()
                    say(c, if (code in 200..299) "Listo." else "La dirección respondió $code.")
                } catch (e: Exception) {
                    say(c, "No se pudo llamar a la dirección.")
                }
            }.start()
            return true
        }
        if (v.startsWith("l:")) {
            val mac = parseMac(v.substring(2))
            if (mac == null) {
                say(c, "Dirección MAC no válida.")
                return true
            }
            Thread {
                try {
                    val data = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { mac.toList() }.toByteArray()
                    java.net.DatagramSocket().use { sk ->
                        sk.broadcast = true
                        sk.send(java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName("255.255.255.255"), 9))
                    }
                    say(c, "Señal de encendido enviada.")
                } catch (e: Exception) {
                    say(c, "No se pudo enviar la señal de encendido.")
                }
            }.start()
            return true
        }
        return false
    }

    /** Devuelve la MAC como 6 bytes, o null si no es valida. */
    fun parseMac(s: String): ByteArray? {
        val h = s.filter { it.isLetterOrDigit() }
        if (h.length != 12 || !h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return ByteArray(6) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
