package com.mini.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tras instalar una versión nueva reactiva la accesibilidad si quedó apagada. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pr = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                Home1.ensureAccess(c)
            } finally {
                pr.finish()
            }
        }, 6000L)
    }
}
