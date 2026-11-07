package com.mini.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Home predeterminado: recuerda el Home anterior, prueba temporal con salvavidas y permisos. */
object Home1 {
    private fun sp(c: Context) = c.getSharedPreferences("home1", Context.MODE_PRIVATE)

    fun current(c: Context): String {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName ?: ""
    }

    fun isDefault(c: Context) = current(c) == c.packageName

    fun previous(c: Context): String = sp(c).getString("prev", "") ?: ""

    /** Guarda cual era el Home antes de ser nosotros (Google, Projectivy, etc.). */
    fun saveBefore(c: Context) {
        val cur = current(c)
        if (cur.isNotEmpty() && cur != c.packageName && cur != "android") {
            sp(c).edit().putString("prev", cur).apply()
        }
    }

    fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Abre el selector de Home; si el anterior esta desactivado, abre su ficha para reactivarlo. */
    fun restore(c: Context) {
        val p = previous(c)
        if (p.isEmpty()) {
            toast(c, "Aún no hay un Home anterior guardado.")
            return
        }
        try {
            val off = !c.packageManager.getApplicationInfo(p, 0).enabled
            if (off) {
                toast(c, "Reactiva «" + label(c, p) + "» y vuelve a elegirlo como inicio.")
                c.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$p"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        } catch (e: Exception) {
        }
        sp(c).edit().putLong("trialEnd", 0L).putInt("fails", 0).apply()
        if (!tryStart(c, "android.settings.HOME_SETTINGS")) {
            toast(c, "Cámbialo en Ajustes > Apps > Apps predeterminadas.")
        }
    }

    // ----- Prueba temporal -----
    fun startTrial(c: Context, hours: Int) {
        saveBefore(c)
        sp(c).edit().putLong("trialEnd", System.currentTimeMillis() + hours * 3_600_000L)
            .putInt("fails", 0).apply()
    }

    fun trialLeftMin(c: Context): Long {
        val e = sp(c).getLong("trialEnd", 0L)
        return if (e <= 0L) 0L else maxOf(0L, (e - System.currentTimeMillis()) / 60_000L)
    }

    fun trialActive(c: Context) = sp(c).getLong("trialEnd", 0L) > 0L

    fun confirm(c: Context) {
        sp(c).edit().putLong("trialEnd", 0L).putInt("fails", 0).apply()
    }

    /** Anota un cierre inesperado durante la prueba; con 3 se activa el salvavidas. */
    fun noteCrash(c: Context) {
        if (!trialActive(c)) return
        sp(c).edit().putInt("fails", sp(c).getInt("fails", 0) + 1).apply()
    }

    /** Se llama al abrir el launcher: si la prueba venció o falló 3 veces, vuelve al anterior. */
    fun checkTrial(c: Context) {
        val s = sp(c)
        val end = s.getLong("trialEnd", 0L)
        if (end <= 0L) return
        if (System.currentTimeMillis() > end || s.getInt("fails", 0) >= 3) {
            restore(c)
        }
    }

    fun firstRunPending(c: Context): Boolean {
        val s = sp(c)
        if (s.getBoolean("asked", false)) return false
        s.edit().putBoolean("asked", true).apply()
        return !isDefault(c)
    }

    // ----- Permisos -----
    fun overlayOk(c: Context) = Settings.canDrawOverlays(c)
    fun accessOk() = PowerService.instance != null
    fun notifOk(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: ""
        return s.contains(c.packageName)
    }

    fun secureOk(c: Context) =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED

    @Volatile private var lastEnsure = 0L

    /** Si tiene WRITE_SECURE_SETTINGS y el servicio de accesibilidad esta apagado, lo vuelve a encender. */
    fun ensureAccess(c: Context) {
        if (PowerService.instance != null || !secureOk(c)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastEnsure != 0L && now - lastEnsure < 15_000L) return
        lastEnsure = now
        try {
            val cn = android.content.ComponentName(c, PowerService::class.java).flattenToString()
            val cr = c.contentResolver
            val cur = Settings.Secure.getString(cr, "enabled_accessibility_services") ?: ""
            val items = cur.split(":").filter { it.isNotEmpty() }
            if (items.contains(cn)) {
                Settings.Secure.putString(cr, "enabled_accessibility_services", (items - cn).joinToString(":"))
            }
            Settings.Secure.putString(cr, "enabled_accessibility_services", (items - cn + cn).joinToString(":"))
            Settings.Secure.putString(cr, "accessibility_enabled", "1")
        } catch (e: Exception) {
        }
    }
}

@Composable
private fun Light(label: String, ok: Boolean) {
    SemRow(label, if (ok) "Listo" else "Falta", if (ok) 0 else 2)
}

@Composable
fun HomeDialog(onClose: () -> Unit, onSetHome: () -> Unit) {
    val c = LocalContext.current
    var t by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") t
    val def = Home1.isDefault(c)
    val prev = Home1.previous(c)
    ExtraDialog("Inicio predeterminado", onClose, scroll = true) {
        Text(
            if (def) "One Piece es tu inicio." else "One Piece aún no es tu inicio.",
            color = Color(0xE6FFFFFF), fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )
        if (prev.isNotEmpty()) InfoRow("Home anterior", Home1.label(c, prev))
        if (Home1.trialActive(c)) {
            InfoRow("Prueba", "quedan " + Home1.trialLeftMin(c) + " min")
            ActionRow("Confirmar: quedarme con One Piece") {
                Home1.confirm(c); t++
            }
        }
        if (!def) {
            ActionRow("Hacer One Piece predeterminado") {
                Home1.saveBefore(c); onSetHome(); t++
            }
            ActionRow("Probar 1 hora (vuelve solo)") {
                Home1.startTrial(c, 1); onSetHome(); t++
            }
            ActionRow("Probar 24 horas (vuelve solo)") {
                Home1.startTrial(c, 24); onSetHome(); t++
            }
        }
        if (prev.isNotEmpty()) ActionRow("Restaurar Home anterior") { Home1.restore(c) }
        Text("Permisos", color = Color(0x99FFFFFF), fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp))
        Light("Superposición", Home1.overlayOk(c))
        Light("Accesibilidad", Home1.accessOk())
        Light("Notificaciones", Home1.notifOk(c))
        Light("WRITE_SECURE_SETTINGS", Home1.secureOk(c))
        ActionRow("Actualizar") { t++ }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}
