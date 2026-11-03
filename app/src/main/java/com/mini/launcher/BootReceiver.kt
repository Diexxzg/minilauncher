package com.mini.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Al encender la TV solo deja una marca. La app elegida la abre MainActivity (que arranca
 * como launcher y puede abrir apps sin las restricciones de Android 10+ para receptores).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(c)
        if (p.getInt("bootEnabled", 0) == 1 && p.getStr("bootApp", "").isNotEmpty()) {
            p.putInt("bootPending", 1)
        }
    }
}

/** Llamar desde MainActivity.onCreate: abre la app de arranque una sola vez por encendido. */
fun runBootApp(ctx: Context) {
    val p = Prefs(ctx)
    if (p.getInt("bootPending", 0) != 1) return
    p.putInt("bootPending", 0)
    val pkg = p.getStr("bootApp", "")
    if (pkg.isEmpty() || p.getInt("bootEnabled", 0) != 1) return
    try {
        val pm = ctx.packageManager
        val li = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(li)
    } catch (e: Exception) {
    }
}
