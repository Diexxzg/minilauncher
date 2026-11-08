package com.mini.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
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
}
