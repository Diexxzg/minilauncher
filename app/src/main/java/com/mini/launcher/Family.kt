package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

class FamBlock(val title: String, val detail: String, val kind: String, val pkg: String)

/** Familia: tiempo por app, horario, candado con PIN, aprobacion de apps nuevas y registro (solo perfil Ninos). */
object Fam {
    private var curPkg = ""
    private var curStart = 0L
    private var passUntil = 0L

    val LIMITS = listOf(0, 15, 30, 45, 60, 90, 120)

    private fun day(): String {
        val c = Calendar.getInstance()
        return (c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)).toString()
    }

    private fun useMap(cfg: Cfg): MutableMap<String, Long> {
        val p = cfg.ex("famUse").split("|")
        if (p.size < 2 || p[0] != day()) return mutableMapOf()
        val m = mutableMapOf<String, Long>()
        for (e in p[1].split(";")) {
            val k = e.split(":")
            if (k.size == 2) m[k[0]] = k[1].toLongOrNull() ?: 0L
        }
        return m
    }

    private fun saveUse(cfg: Cfg, m: Map<String, Long>) {
        cfg.setEx("famUse", day() + "|" + m.entries.joinToString(";") { it.key + ":" + it.value })
    }

    fun limits(cfg: Cfg): Map<String, Int> {
        val m = mutableMapOf<String, Int>()
        for (e in cfg.ex("famLim").split(";")) {
            val k = e.split(":")
            if (k.size == 2) k[1].toIntOrNull()?.let { if (it > 0) m[k[0]] = it }
        }
        return m
    }

    fun setLimit(cfg: Cfg, pkg: String, min: Int) {
        val m = limits(cfg).toMutableMap()
        if (min > 0) m[pkg] = min else m.remove(pkg)
        cfg.setEx("famLim", m.entries.joinToString(";") { it.key + ":" + it.value })
    }

    fun locked(cfg: Cfg): Set<String> = cfg.ex("famLock").split(";").filter { it.isNotEmpty() }.toSet()

    fun setLocked(cfg: Cfg, pkg: String, on: Boolean) {
        val s = locked(cfg).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        cfg.setEx("famLock", s.joinToString(";"))
    }

    private fun inRange(s: String): Boolean {
        val m = Regex("^(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return false
        val a = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
        val b = m.groupValues[3].toInt() * 60 + m.groupValues[4].toInt()
        val c = Calendar.getInstance()
        val n = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return if (a <= b) n in a until b else (n >= a || n < b)
    }

    fun validRange(s: String): String? {
        val m = Regex("^(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val v = m.groupValues.drop(1).map { it.toInt() }
        if (v[0] > 23 || v[2] > 23 || v[1] > 59 || v[3] > 59) return null
        return "%02d:%02d-%02d:%02d".format(v[0], v[1], v[2], v[3])
    }

    /** Bloqueo vigente para abrir pkg (solo perfil Ninos), o null. */
    fun blockFor(cfg: Cfg, pkg: String, label: String): FamBlock? {
        if (!cfg.kids) return null
        if (System.currentTimeMillis() < passUntil) return null
        if (cfg.ex("famSched") == "1" && inRange(cfg.ex("famRange", "21:00-07:00"))) {
            return FamBlock("Fuera de horario", "Bloqueado de " + cfg.ex("famRange", "21:00-07:00").replace("-", " a "), "h", pkg)
        }
        if (cfg.ex("famTime") == "1") {
            val lim = limits(cfg)[pkg] ?: return null
            val used = ((useMap(cfg)[pkg] ?: 0L) / 60000L).toInt()
            if (used >= lim) {
                return FamBlock("Se acabó el tiempo de $label hoy", "Usaste $used de $lim minutos · Se renueva mañana", "t", pkg)
            }
        }
        return null
    }

    fun grant(cfg: Cfg, b: FamBlock) {
        if (b.kind == "h") passUntil = System.currentTimeMillis() + 30 * 60000L
        else {
            val m = useMap(cfg)
            m[b.pkg] = ((m[b.pkg] ?: 0L) - 15 * 60000L).coerceAtLeast(0L)
            saveUse(cfg, m)
        }
    }

    fun opened(cfg: Cfg, pkg: String) {
        curPkg = pkg
        curStart = System.currentTimeMillis()
        if (cfg.kids && cfg.ex("famLog") == "1") {
            val l = (cfg.ex("famLogTxt").split("\n").filter { it.isNotBlank() } + "$curStart|$pkg").takeLast(300)
            cfg.setEx("famLogTxt", l.joinToString("\n"))
        }
    }

    /** Al volver al launcher: suma el tiempo que estuvo la app abierta. */
    fun returned(cfg: Cfg) {
        if (curPkg.isEmpty()) return
        val el = (System.currentTimeMillis() - curStart).coerceIn(0L, 6 * 3600_000L)
        if (cfg.kids && cfg.ex("famTime") == "1") {
            val m = useMap(cfg)
            m[curPkg] = (m[curPkg] ?: 0L) + el
            saveUse(cfg, m)
        }
        curPkg = ""
    }

    fun pending(cfg: Cfg): List<String> = cfg.ex("famPend").split(";").filter { it.isNotEmpty() }
    fun addPending(cfg: Cfg, pkg: String) {
        if (pkg !in pending(cfg)) cfg.setEx("famPend", (pending(cfg) + pkg).joinToString(";"))
    }
    fun removePending(cfg: Cfg, pkg: String) {
        cfg.setEx("famPend", pending(cfg).filter { it != pkg }.joinToString(";"))
    }
}

@Composable
fun FamBlockDialog(b: FamBlock, canAsk: Boolean, onMore: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .clip(shape)
                .background(Color(0xFF121722))
                .border(2.dp, Color(0xFFE8A33D), shape)
                .padding(32.dp)
        ) {
            Text(b.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(b.detail, color = Color(0xB3FFFFFF), fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
            if (canAsk) ActionRow("Pedir más tiempo (PIN)", Modifier.autoFocus()) { onMore() }
            else Text("Los padres pueden crear un PIN en Configuración para dar más tiempo.", color = Color(0x99FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            ActionRow("Volver al inicio", if (canAsk) Modifier else Modifier.autoFocus()) { onClose() }
        }
    }
}

@Composable
fun FamLimitsDialog(cfg: Cfg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val kids = remember { Prefs(ctx).kidsApps() }
    val apps = remember { AppCache.apps.filter { it.pkg in kids }.sortedBy { it.label.lowercase() } }
    val names = Fam.LIMITS.map { if (it == 0) "Sin límite" else "$it min" }
    ExtraDialog("Tiempo por app (Niños)", onClose, scroll = true) {
        if (apps.isEmpty()) Text("Primero elige las apps del perfil Niños.", color = Color(0xB3FFFFFF), fontSize = 15.sp)
        for (a in apps) {
            val cur = Fam.limits(cfg)[a.pkg] ?: 0
            ChoiceRow(a.label, names, Fam.LIMITS.indexOf(cur).coerceAtLeast(0)) { i -> Fam.setLimit(cfg, a.pkg, Fam.LIMITS[i]) }
        }
    }
}

@Composable
fun FamLockDialog(cfg: Cfg, onClose: () -> Unit) {
    val apps = remember { AppCache.apps.sortedBy { it.label.lowercase() } }
    ExtraDialog("Apps con PIN", onClose, scroll = true) {
        if (!cfg.pinSet) Text("Crea primero un PIN de 4 dígitos (Configuración › Sistema).", color = Color(0xB3FFFFFF), fontSize = 15.sp)
        val locked = Fam.locked(cfg)
        for (a in apps) SwitchRow(a.label, a.pkg in locked) { Fam.setLocked(cfg, a.pkg, it) }
    }
}

@Composable
fun FamPendingDialog(cfg: Cfg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { AppCache.apps }
    ExtraDialog("Apps pendientes de aprobar", onClose, scroll = true) {
        val pend = Fam.pending(cfg)
        if (pend.isEmpty()) Text("No hay apps nuevas por aprobar.", color = Color(0xB3FFFFFF), fontSize = 15.sp)
        for (p in pend) {
            val label = apps.firstOrNull { it.pkg == p }?.label ?: p
            ActionRow("Aprobar para Niños: $label") {
                val pr = Prefs(ctx)
                pr.saveKidsApps(pr.kidsApps() + p)
                HomeBridge.kidsTick++
                Fam.removePending(cfg, p)
            }
        }
        if (pend.isNotEmpty()) ActionRow("Descartar todas") { cfg.setEx("famPend", "") }
    }
}

@Composable
fun FamLogDialog(cfg: Cfg, onClose: () -> Unit) {
    val apps = remember { AppCache.apps }
    val fmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()) }
    ExtraDialog("Registro de actividad (Niños)", onClose, scroll = true) {
        val l = cfg.ex("famLogTxt").split("\n").filter { it.contains("|") }.takeLast(40).reversed()
        if (l.isEmpty()) Text("Sin actividad registrada.", color = Color(0xB3FFFFFF), fontSize = 15.sp)
        for (e in l) {
            val i = e.indexOf('|')
            val t = e.substring(0, i).toLongOrNull() ?: continue
            val p = e.substring(i + 1)
            InfoRow(fmt.format(java.util.Date(t)), apps.firstOrNull { it.pkg == p }?.label ?: p)
        }
        ActionRow("Borrar registro") { cfg.setEx("famLogTxt", "") }
    }
}
