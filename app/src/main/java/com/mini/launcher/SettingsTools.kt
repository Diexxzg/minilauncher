package com.mini.launcher

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

// Herramientas de Configuracion: restablecer una categoria, exportar/importar solo el diseño,
// descripciones bajo las opciones y el asistente de primer uso.

/** Ajustes (claves de Prefs) que borra "Restablecer". Las categorias que no estan aqui no se restablecen. */
val RESET_KEYS: Map<Int, List<String>> = mapOf(
    0 to listOf(
        "rot", "fade", "pauseWall", "dynBg", "wallShow", "wallShowMins", "weatherFx",
        "theme", "autoTheme", "accentCustom", "glow", "glowSrc", "dim"
    ),
    1 to listOf(
        "scale", "spacing", "corner", "glow", "dynBg", "bigClock", "row0", "row1", "row2",
        "uniformIcons", "focusStyle", "blur", "screenAnim", "openAnim", "bottom", "tileStyle", "focusCard"
    ),
    2 to listOf(
        "topMode", "topScale", "bigClock", "clock24", "showDate", "dateFmt", "cardEvent",
        "cardNote", "cardCountdown", "showWeather", "fahrenheit", "forecast", "autoLoc",
        "continueRow", "showRecents", "voiceMode", "hideBar", "hideBarSecs"
    ),
    3 to listOf(
        "uiScale", "overscan", "saver", "fluid", "saverHint", "night", "nightFrom", "nightTo",
        "sounds", "panelSounds", "ambient", "burnIn", "sleepAction",
        "ambDimOn", "ambDimLvl", "ambAnim", "ambPosTime", "ambPosDate", "ambPosMusic", "ambPosIcons"
    ),
    4 to listOf("showMusic", "musicSize", "musicPos"),
    6 to listOf(
        "keyRed", "keyGreen", "keyYellow", "keyBlue", "bootEnabled", "bootApp",
        "sceneApp", "sceneNight", "sceneDim", "sceneSleep",
        "routineOn", "routineHour", "routineMin", "routineAction", "routineApp"
    ),
    9 to listOf("bigText", "highContrast", "thickFocus")
)

/** Solo el aspecto (Fondo y color + Dock y diseño): no incluye apps, PIN ni notas. */
val DESIGN_KEYS: List<String> =
    ((RESET_KEYS[0] ?: emptyList()) + (RESET_KEYS[1] ?: emptyList())).distinct()

/**
 * Escena de cine: activa modo noche, oscurecimiento y temporizador. Si ya esta activa, restaura lo anterior.
 * Devuelve true si quedo activada.
 */
fun toggleScene(cfg: Cfg, prefs: Prefs, sleep: SleepTimer): Boolean {
    if (prefs.getInt("sceneOn", 0) == 1) {
        cfg.setDim(prefs.getInt("scenePrevDim", 0))
        cfg.setNight(prefs.getInt("scenePrevNight", 0) == 1)
        sleep.cancel()
        prefs.putInt("sceneOn", 0)
        return false
    }
    prefs.putInt("scenePrevDim", cfg.dim)
    prefs.putInt("scenePrevNight", if (cfg.night) 1 else 0)
    cfg.setDim(cfg.sceneDim)
    cfg.setNight(cfg.sceneNight)
    val mins = SLEEP_MINS[cfg.sceneSleep.coerceIn(0, SLEEP_MINS.lastIndex)]
    if (mins > 0) sleep.start(mins)
    prefs.putInt("sceneOn", 1)
    return true
}

/** Borra los ajustes de la categoria y reinicia la pantalla; al volver se abre la misma categoria. */
fun resetCategory(ctx: Context, cat: Int) {
    val keys = RESET_KEYS[cat] ?: return
    val prefs = Prefs(ctx)
    prefs.removeKeys(keys)
    prefs.putInt("reopenCat", cat)
    findActivity(ctx)?.recreate()
}

fun writeDesignTo(ctx: Context, prefs: Prefs, uri: Uri): Boolean =
    try {
        val out = ctx.contentResolver.openOutputStream(uri, "wt")
        if (out == null) {
            false
        } else {
            out.use { it.write(prefs.exportKeysJson(DESIGN_KEYS).toByteArray(Charsets.UTF_8)) }
            true
        }
    } catch (e: Exception) {
        false
    }

fun readDesignFrom(ctx: Context, prefs: Prefs, uri: Uri): Boolean =
    try {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        if (text == null || text.isBlank() || text.length > 200_000) false
        else prefs.importKeysJson(text, DESIGN_KEYS)
    } catch (e: Exception) {
        false
    }

/** Explicaciones cortas que salen bajo las opciones menos obvias (clave = nombre interno de la opcion). */
val DESCS: Map<String, String> = mapOf(
    "Perfil de diseño" to "Combina tamaño, separación y brillo en un solo paso.",
    "Rotación de fondos" to "Cambia el fondo solo, según el horario elegido.",
    "Transición suave" to "Funde un fondo con el siguiente en vez de cambiarlo de golpe.",
    "Pausar fondo animado" to "Detiene el fondo en movimiento mientras hay un panel abierto.",
    "Fondo según app enfocada" to "El fondo toma el color o el banner de la app seleccionada.",
    "Presentación de fondos" to "Pasa por todos tus fondos automáticamente.",
    "Clima en el fondo" to "Muestra efectos de lluvia, nubes o sol detrás del inicio.",
    "Tema automático" to "Cambia entre tema de día y de noche según la hora.",
    "Color de acento propio" to "Reemplaza el color del tema por el que elijas.",
    "Brillo de color" to "Intensidad del resplandor alrededor de la ficha enfocada.",
    "Color del brillo" to "De dónde sale el color del resplandor.",
    "Oscurecer fondo" to "Ayuda a leer mejor sobre fondos muy claros.",
    "Desenfoque al abrir paneles" to "Difumina el inicio mientras hay un panel abierto.",
    "Tamaño de la interfaz" to "Agranda o reduce todo el launcher.",
    "Margen de seguridad" to "Aleja los elementos de los bordes si tu TV recorta la imagen.",
    "Iconos uniformes" to "Muestra cada imagen completa sobre un fondo del color de la app, para que todas se vean igual.",
    "App de la escena" to "Se abre al activar la escena (botón de color o Panel rápido). Púlsala otra vez para volver a como estaba.",
    "Rutina diaria" to "Una vez al día, si el launcher está en el inicio, hasta 30 minutos después de la hora elegida.",
    "Fluidez máxima" to "Apaga de una vez desenfoque, brillo, fondos animados y animaciones para ir más fluido.",
    "Modo ahorro" to "Desactiva efectos pesados. Útil en TV con poca memoria.",
    "Sugerir modo ahorro" to "Avisa cuando el launcher vaya lento.",
    "Modo noche automático" to "Atenúa la interfaz en el horario que elijas.",
    "Protección contra quemado" to "Mueve un poco los elementos fijos para cuidar la pantalla.",
    "Fondo animado en reposo" to "Si tu fondo es GIF, WebP o video, se mueve también en reposo. Se pausa con fluidez máxima o modo ahorro.",
    "Posición de la hora" to "Elige en qué parte de la pantalla va cada elemento; «Oculto» lo quita.",
    "Modo ambiente" to "Pantalla de reposo con reloj tras un rato sin usar el control.",
    "Tarjeta de la app enfocada" to "Muestra datos de la app seleccionada bajo el dock.",
    "Elementos de arriba" to "Cuánta información se ve en la parte superior.",
    "Fila Continuar" to "Muestra lo último que usaste para retomarlo rápido.",
    "Ocultar barra superior" to "La barra se esconde sola tras unos segundos sin usar.",
    "Ubicación automática" to "Usa la ubicación de la TV para el clima en vez de la ciudad elegida.",
    "Proteger ajustes" to "Pide el PIN para abrir Configuración.",
    "Proteger apps ocultas" to "Pide el PIN para ver las apps ocultas.",
    "Perfil para niños" to "Limita el inicio a las apps que elijas.",
    "Alto contraste" to "Aumenta la diferencia entre los textos y el fondo.",
    "Foco grueso" to "Marco de selección más visible.",
    "Abrir app al encender" to "Abre una app automáticamente cuando enciendes la TV.",
    "Exportar diseño" to "Guarda solo el aspecto (tema, dock, fondo…), sin apps ni PIN.",
    "Importar diseño" to "Reemplaza el aspecto actual por el del archivo elegido.",
    "Asistente de configuración" to "Repasa fondo, tema, pantalla y permisos paso a paso."
)

// ---------- Botones y filas de cuadros ----------

/** Boton centrado para cuadros: el foco se ve igual que en las demas filas. */
@Composable
fun DialogButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    FocusRow(onClick = onClick) { focused ->
        Text(
            text,
            color = if (primary || focused) Color.White else Color(0xCCFFFFFF),
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Fila "Restablecer": un cuadro de confirmacion (el foco ya esta en "Restablecer", basta con OK). */
@Composable
fun ResetCategoryRow(name: String, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    DialogRow("Restablecer «" + name + "»", modifier) { close ->
        ExtraDialog("¿Restablecer «" + name + "»?", close) {
            Text(
                "Las opciones de esta categoría vuelven a su valor de fábrica. " +
                    "No cambian tus apps, tus fondos guardados ni el PIN.",
                color = Color(0xCCFFFFFF), fontSize = 14.sp
            )
            DialogButton("Restablecer", true) { onConfirm() }
            DialogButton("Cancelar") { close() }
        }
    }
}

// ---------- Asistente de primer uso ----------

private val WIZ_TITLES = listOf("Fondo", "Tema y diseño", "Pantalla y rendimiento", "Permisos")

/**
 * Asistente de 4 pasos (fondo, tema, ahorro/pantalla y permisos). Se ve en el panel derecho y el
 * inicio queda visible a la izquierda para ver cada cambio al instante. Atras lo cierra de una vez.
 */
@Composable
fun SetupWizard(
    style: DockStyle,
    cfg: Cfg,
    onProfile: (Int) -> Unit,
    onWalls: () -> Unit,
    onNetPerm: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val acc = Themes.current.acc
    var step by remember { mutableIntStateOf(0) }
    var permTick by remember { mutableIntStateOf(0) }
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val profSel = profileIndex(style.scalePct, style.spacing, style.corner, style.glow, cfg)
    val last = WIZ_TITLES.lastIndex

    GlassPanelFrame(wide = false) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            Text(
                "ASISTENTE DE CONFIGURACIÓN", color = Color(0x99FFFFFF),
                fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in WIZ_TITLES.indices) {
                    Box(
                        Modifier
                            .width(if (i == step) 28.dp else 10.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i <= step) acc else Color(0x33FFFFFF))
                    )
                }
            }
            Text(
                WIZ_TITLES[step], color = Color.White, fontSize = 26.sp,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp)
            )
            Text(
                "Paso " + (step + 1) + " de " + WIZ_TITLES.size, color = Color(0x99FFFFFF),
                fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                key(step) {
                    val first = Modifier.autoFocus()
                    when (step) {
                        0 -> {
                            ActionRow("Elegir un fondo de pantalla ahora", first) { onWalls() }
                            ChoiceRow(
                                "Rotación de fondos",
                                listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"),
                                cfg.rot
                            ) { cfg.setRot(it) }
                            SwitchRow("Transición suave", cfg.fade) { cfg.setFade(it) }
                            SwitchRow("Presentación de fondos (cambian solos)", cfg.wallShow) { cfg.setWallShow(it) }
                        }
                        1 -> {
                            ChoiceRow("Tema de color", Themes.list.map { it.name }, cfg.theme, first) { cfg.setTheme(it) }
                            SwitchRow("Tema automático (día y noche)", cfg.autoTheme) { cfg.setAutoTheme(it) }
                            ChoiceRow("Perfil de diseño", PROFILE_OPTIONS, profSel) {
                                onProfile(if (it > 0) it - 1 else 0)
                            }
                        }
                        2 -> {
                            SliderRow("Tamaño de la interfaz", cfg.uiScale, 70, 130, 5, "%", first) { cfg.setUiScale(it) }
                            SliderRow("Margen de seguridad", cfg.overscan, 0, 60, 2, " dp") { cfg.setOverscan(it) }
                            SwitchRow("Fluidez máxima", cfg.fluid) { cfg.setFluid(it) }
                            SwitchRow("Modo ahorro (TV modesta)", cfg.saverOnly) { cfg.setSaver(it) }
                            SwitchRow("Sugerir modo ahorro si va lenta", cfg.saverHint) { cfg.setSaverHint(it) }
                        }
                        else -> {
                            val calOk = remember(permTick) { hasCalendarPermission(ctx) }
                            val locOk = remember(permTick) { hasLocationPermission(ctx) }
                            val notiOk = remember(permTick) {
                                NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
                            }
                            ActionRow("Ubicación (clima): " + (if (locOk) "permitida" else "permitir"), first) {
                                if (!locOk) locPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                            ActionRow("Calendario: " + (if (calOk) "permitido" else "permitir")) {
                                if (!calOk) calPerm.launch(android.Manifest.permission.READ_CALENDAR)
                            }
                            ActionRow("Notificaciones (música): " + (if (notiOk) "activo" else "permitir")) {
                                tryStart(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            }
                            ActionRow("Nombre de red y Bluetooth") { onNetPerm() }
                            Text(
                                "Todos son opcionales: solo se usan si activas la función que los necesita.",
                                color = Color(0x80FFFFFF), fontSize = 12.sp,
                                modifier = Modifier.padding(start = 10.dp, top = 8.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    DialogButton(if (step == 0) "Omitir" else "Anterior") {
                        if (step == 0) onClose() else step--
                    }
                }
                Box(Modifier.weight(1f)) {
                    DialogButton(if (step == last) "Listo" else "Siguiente", true) {
                        if (step == last) onClose() else step++
                    }
                }
            }
        }
    }
}
