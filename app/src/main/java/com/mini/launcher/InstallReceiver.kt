package com.mini.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Recibe el resultado de la instalacion por sesion: muestra la confirmacion de Android o el error. */
class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(c: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val conf = if (Build.VERSION.SDK_INT >= 33) {
                    i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                try {
                    conf?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (conf != null) c.startActivity(conf)
                } catch (e: Exception) {
                    toast(c, "No se pudo abrir el instalador de Android.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> toast(c, "No se pudo instalar: " + (i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error"))
        }
    }
}
