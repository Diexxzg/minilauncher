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
        if (v.startsWith("p:")) {
            "Abrir " + (AppCache.apps.firstOrNull { it.pkg == v.substring(2) }?.label ?: v.substring(2))
        } else {
            REMAP_ACTIONS.getOrElse(v.removePrefix("a:").toIntOrNull() ?: 0) { "?" }
        }

    /** Ejecuta la accion desde el servicio de accesibilidad (funciona en cualquier app). */
    fun run(c: Context, svc: AccessibilityService, v: String) {
        try {
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
}
