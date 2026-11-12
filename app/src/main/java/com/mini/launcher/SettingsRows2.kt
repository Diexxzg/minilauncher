package com.mini.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

fun rowsPantalla(): List<SRow> = listOf(
    slRow("uiscale", "Pantalla", "layout", "Tamaño de la interfaz", 70, 130, 5, "%", { it.cfg.uiScale }, { c, v -> c.cfg.setUiScale(v) }),
    slRow("overscan", "Pantalla", "layout", "Margen de seguridad", 0, 60, 2, " dp", { it.cfg.overscan }, { c, v -> c.cfg.setOverscan(v) }),
    chRow("fxmode", "Rendimiento", "bolt", "Modo de efectos", listOf("Normal", "Fluidez máxima", "Ahorro"),
        { if (it.cfg.saverOnly) 2 else if (it.cfg.fluid) 1 else 0 },
        { c, v -> c.cfg.setFluid(v == 1); c.cfg.setSaver(v == 2) }),
    SRow("perf", "Rendimiento", "bolt") { _, m -> DialogRow("Salud, RAM y almacenamiento", m) { close -> PerfDialog(close) } },
    chRow("ambient", "Modo ambiente", "eye", "Modo ambiente", listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"), { it.cfg.ambient }, { c, v -> c.cfg.setAmbient(v) }),
    chRow("ambtime", "Modo ambiente", "eye", "Reloj en ambiente", AMB_POS_OPTIONS, { it.cfg.ambPosTime }, { c, v -> c.cfg.setAmbPosTime(v) }),
    swRow("ambblack", "Modo ambiente", "eye", "Pantalla negra al entrar (el audio sigue)", { it.cfg.ex("ambBlack") == "1" }, { c, v -> c.cfg.setEx("ambBlack", if (v) "1" else "0") }),
    swRow("burnin", "Modo ambiente", "eye", "Protección contra quemado (mover el reloj)", { it.cfg.burnIn }, { c, v -> c.cfg.setBurnIn(v) }),
    SRow("sleep", "Modo ambiente", "bolt") { _, m -> SleepRow(m) },
    chRow("sleepact", "Modo ambiente", "bolt", "Al terminar el temporizador", SLEEP_ACTIONS, { it.cfg.sleepAction }, { c, v -> c.cfg.setSleepAction(v) })
)

fun rowsMusicaApps(): List<SRow> = listOf(
    swRow("showmusic", "Música", "music", "Mostrar reproductor", { it.cfg.showMusic }, { c, v -> c.cfg.setShowMusic(v) }),
    slRow("musicsize", "Música", "music", "Tamaño del reproductor", 50, 120, 5, "%", { it.cfg.musicSize }, { c, v -> c.cfg.setMusicSize(v) }),
    chRow("musicpos", "Música", "music", "Posición del reproductor", listOf("Junto al reloj", "Arriba al centro"), { it.cfg.musicPos }, { c, v -> c.cfg.setMusicPos(v) }),
    acRow("editapps", "Apps", "device", "Editar apps (favoritas, orden, tamaño, ocultas)") { it.act("edit") },
    SRow("folders", "Apps", "folder") { c, m -> DialogRow("Carpetas del dock (crear, renombrar, borrar)", m) { close -> FoldersDialog(c.prefs, close) } }
)

private val KEY_NAMES = listOf("rojo", "verde", "amarillo", "azul")

private fun rowsColorKeys(): List<SRow> = (0..3).flatMap { ci ->
    listOf(
        SRow("key$ci", "Celular y mando", "bolt") { c, m ->
            ChoiceRow("Botón " + KEY_NAMES[ci], COLOR_ACTIONS, c.cfg.colorAction(ci), m) { v ->
                when (ci) {
                    0 -> c.cfg.setKeyRed(v)
                    1 -> c.cfg.setKeyGreen(v)
                    2 -> c.cfg.setKeyYellow(v)
                    else -> c.cfg.setKeyBlue(v)
                }
            }
        },
        SRow("keyx$ci", "Celular y mando", "bolt") { c, m ->
            val cur = c.cfg.colorExtra(ci)
            val txt = if (cur.isEmpty()) "Botón " + KEY_NAMES[ci] + ": web o Wake-on-LAN…"
            else "Botón " + KEY_NAMES[ci] + ": " + cur.substring(2)
            DialogRow(txt, m) { close ->
                TextEditDialog(
                    "Botón " + KEY_NAMES[ci],
                    cur.drop(2),
                    "Dirección web (http://…) o MAC (AA:BB:CC:DD:EE:FF). Vacío = quitar. Tiene prioridad sobre la acción.",
                    200,
                    { t0 ->
                        val t = t0.trim()
                        if (t.isEmpty()) c.cfg.setColorExtra(ci, "")
                        else {
                            val b = Remap.parseMac(t)
                            if (b != null) c.cfg.setColorExtra(ci, "l:" + b.joinToString(":") { "%02X".format(it) })
                            else c.cfg.setColorExtra(ci, "w:" + (if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"))
                        }
                        close()
                    },
                    close
                )
            }
        }
    )
}

fun rowsSistema(): List<SRow> = listOf(
    acRow("wifi", "Conexiones", "globe", "Wi-Fi") { it.act("open:wifi") },
    acRow("bt", "Conexiones", "globe", "Bluetooth") { it.act("open:bt") },
    acRow("hdmi", "Conexiones", "device", "Entradas (HDMI)") { it.act("inputs") },
    acRow("allset", "Android", "globe", "Todos los ajustes de Android") { it.act("open:all") },
    acRow("dream", "Android", "eye", "Protector de pantalla del sistema") { it.act("open:dream") },
    acRow("sethome", "Android", "home", "Establecer como inicio predeterminado") { it.act("sethome") },
    SRow("phone", "Celular y mando", "device") { c, m -> DialogRow("Conexión con el celular (fondos, texto, copia)", m) { close -> PhoneDialog(close, c.cfg) } },
    SRow("remap", "Celular y mando", "bolt") { _, m -> DialogRow("Remapear botones del mando", m) { close -> RemapDialog(close) } }
) + rowsColorKeys() + listOf(
    SRow("visitor", "Visitas", "globe") { c, m -> DialogRow("Tarjeta de visitas (QR del Wi-Fi)", m) { close -> VisitorCardDialog(c.prefs, close) } }
)

fun rowsSeguridad(): List<SRow> = listOf(
    SRow("pinstate", "Seguridad", "lock", focusable = false) { c, _ -> InfoRow("PIN", if (c.cfg.pinSet) "Activo" else "Sin PIN") },
    acRow("pincreate", "Seguridad", "lock", "Crear PIN de 4 dígitos", { !it.cfg.pinSet }) { it.act("pin:create") },
    acRow("pinchange", "Seguridad", "lock", "Cambiar PIN", { it.cfg.pinSet }) { it.act("pin:change") },
    acRow("pinremove", "Seguridad", "lock", "Quitar PIN", { it.cfg.pinSet }) { it.act("pin:remove") },
    swRow("locksettings", "Seguridad", "shield", "Proteger ajustes", { it.cfg.lockSettings }, { c, v -> c.cfg.setLockSettings(v) }, { it.cfg.pinSet }),
    swRow("lockhidden", "Seguridad", "shield", "Proteger apps ocultas", { it.cfg.lockHidden }, { c, v -> c.cfg.setLockHidden(v) }, { it.cfg.pinSet }),
    SRow("pcal", "Permisos", "shield") { c, m ->
        val ok = remember(c.permTick) { hasCalendarPermission(c.ctx) }
        ActionRow("Calendario: " + (if (ok) "permitido" else "falta (se pide al activar el evento)"), m) {
            if (ok) toast(c.ctx, "El calendario ya está permitido.") else c.act("perm:cal")
        }
    },
    SRow("ploc", "Permisos", "shield") { c, m ->
        val ok = remember(c.permTick) { hasLocationPermission(c.ctx) }
        ActionRow("Ubicación: " + (if (ok) "permitida" else "falta (se pide al activarla)"), m) {
            if (ok) toast(c.ctx, "La ubicación ya está permitida.") else c.act("perm:loc")
        }
    },
    acRow("pnet", "Permisos", "shield", "Permitir nombre de red y Bluetooth") { it.act("netperm") },
    SRow("pnotif", "Permisos", "shield") { c, m ->
        val ok = remember(c.permTick) { NotificationManagerCompat.getEnabledListenerPackages(c.ctx).contains(c.ctx.packageName) }
        ActionRow("Acceso a notificaciones (música): " + (if (ok) "activo" else "falta permitir"), m) {
            tryStart(c.ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
    },
    swRow("bigtext", "Accesibilidad", "eye", "Texto grande", { it.cfg.bigText }, { c, v -> c.cfg.setBigText(v) }),
    swRow("contrast", "Accesibilidad", "eye", "Alto contraste", { it.cfg.highContrast }, { c, v -> c.cfg.setHighContrast(v) }),
    swRow("thickfocus", "Accesibilidad", "eye", "Foco resaltado (más grueso en las fichas)", { it.cfg.thickFocus }, { c, v -> c.cfg.setThickFocus(v) })
)

fun rowsAcerca(): List<SRow> = listOf(
    SRow("ver", "Versión", "info", focusable = false) { c, _ ->
        val v = remember { runCatching { c.ctx.packageManager.getPackageInfo(c.ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
        val code = remember { runCatching { c.ctx.packageManager.getPackageInfo(c.ctx.packageName, 0).versionCode.toString() }.getOrNull() ?: "?" }
        InfoCard(listOf("Versión" to v, "Código de versión" to code))
    },
    acRow("upd", "Versión", "info", "Buscar actualizaciones") { it.act("update") },
    swRow("updchk", "Versión", "info", "Avisar si hay versión nueva (1 vez al día)", { it.cfg.updateCheck }, { c, v -> c.cfg.setUpdateCheck(v) }),
    SRow("equipo", "Equipo", "info", focusable = false) { c, _ ->
        val snap = remember { readAboutSnap(c.ctx) }
        val home = remember { isDefaultHome(c.ctx) }
        val l = ArrayList<Pair<String, String>>()
        l.add("Dispositivo" to android.os.Build.MODEL)
        l.add("Android" to android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
        l.add("Procesador" to (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?") + " · " + Runtime.getRuntime().availableProcessors() + " núcleos")
        l.add("Pantalla" to snap.res + " · " + snap.dpi + " dpi · " + snap.hz + " Hz")
        l.add("Almacenamiento" to snap.storage)
        l.add("Memoria (RAM)" to snap.ram)
        snap.temp?.let { l.add("Temperatura" to it) }
        l.add("Encendida desde hace" to snap.up)
        l.add("Launcher predeterminado" to if (home) "Sí" else "No")
        InfoCard(l)
    },
    SRow("red", "Red", "info", focusable = false) { c, _ -> InfoCard(remember { readAboutSnap(c.ctx) }.net) },
    SRow("autobak", "Copias de seguridad", "folder") { c, m ->
        var on by remember { mutableStateOf(AutoBackup.enabled(c.ctx)) }
        SwitchRow("Copia automática semanal", on, m) {
            on = it
            AutoBackup.setEnabled(c.ctx, it)
            if (it) AutoBackup.runIfDue(c.ctx)
        }
    },
    acRow("bak", "Copias de seguridad", "folder", "Guardar copia de seguridad") { it.act("backup") },
    acRow("restore", "Copias de seguridad", "folder", "Restaurar copia de seguridad") { it.act("restore") },
    acRow("baksave", "Copias de seguridad", "folder", "Guardar copia en… (elegir dónde)") { it.act("backupSave") },
    acRow("bakshare", "Copias de seguridad", "folder", "Compartir copia de seguridad") { it.act("backupShare") },
    acRow("restorepick", "Copias de seguridad", "folder", "Restaurar desde un archivo…") { it.act("restorePick") },
    SRow("autorestore", "Copias de seguridad", "folder") { _, m -> DialogRow("Restaurar copia automática", m) { close -> AutoRestoreDialog(close) } },
    SRow("notes", "Uso y diagnóstico", "info") { _, m -> DialogRow("Novedades de la última versión", m) { close -> ReleaseNotesFlow(close) } },
    SRow("errors", "Uso y diagnóstico", "info") { _, m -> DialogRow("Visor de errores", m) { close -> ErrorsDialog(close) } },
    SRow("helpqr", "Uso y diagnóstico", "info") { _, m -> DialogRow("Ayuda y reporte (código QR)", m) { close -> HelpQrDialog(close) } },
    SRow("tscreen", "Uso y diagnóstico", "info") { _, m -> DialogRow("Test de pantalla", m) { close -> ScreenTestDialog(close) } },
    SRow("tsound", "Uso y diagnóstico", "info") { _, m -> DialogRow("Test de sonido", m) { close -> SoundTestDialog(close) } },
    SRow("tremote", "Uso y diagnóstico", "info") { _, m -> DialogRow("Test del mando", m) { close -> RemoteTestDialog(close) } }
)
