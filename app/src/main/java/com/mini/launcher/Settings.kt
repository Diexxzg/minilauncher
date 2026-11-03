package com.mini.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class Cat(val icon: String, val name: String, val desc: String)

private val CATS = listOf(
    Cat("palette", "Apariencia", "Fondo, tema, brillo y rendimiento"),
    Cat("folder", "Dock", "Fichas, filas y tarjeta de la app"),
    Cat("home", "Inicio", "Reloj, clima y modo ambiente"),
    Cat("music", "Música", "Reproductor y permisos"),
    Cat("device", "Apps", "Favoritas, orden y ocultas"),
    Cat("globe", "Sistema", "Red, entradas y ajustes de Android"),
    Cat("lock", "Seguridad", "PIN y protección"),
    Cat("info", "Acerca de", "Versión, equipo y copias de seguridad")
)

private val CITIES = listOf(
    "Caracas", "Maracaibo", "Valencia", "Barquisimeto", "Bogotá", "Medellín",
    "Ciudad de México", "Miami", "Madrid", "Lima", "Santiago", "Buenos Aires"
)

private val GLOW_NAMES = listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo")


// ---------- Estilo (cristal oscuro) ----------
// Escala interna del contenido del panel (menor = mas pequeño).
private const val PANEL_SCALE = 0.84f

/**
 * Ancho (dp) del panel lateral derecho para un ancho disponible dado.
 * Lo usan el panel y el inicio (que se hace mas angosto) para no taparse entre si.
 */
fun sidePanelWidth(availDp: Float): Float =
    (availDp * 0.40f).coerceIn(400f, 560f).coerceAtMost(availDp * 0.62f)

private val ACC: Color get() = Themes.current.acc
private val ACC_DIM: Color get() = Themes.current.accDim
private val ACCENT: Brush get() = Brush.horizontalGradient(listOf(ACC_DIM, ACC))
private val TAB_IDLE = Color(0xB3C8D2F0)

// ---------- Controles ----------

@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    onKey: (Int) -> Boolean = { false },
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (focused) Color(0x2BFFFFFF) else Color.Transparent)
        .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape) else Modifier)
        .drawBehind {
            if (focused) {
                drawRoundRect(
                    color = ACC,
                    topLeft = Offset(6.dp.toPx(), size.height * 0.28f),
                    size = Size(3.dp.toPx(), size.height * 0.44f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) {
                when (e.key) {
                    Key.DirectionLeft -> onKey(-1)
                    Key.DirectionRight -> onKey(1)
                    else -> false
                }
            } else {
                false
            }
        }
        .onFocusChanged { focused = it.isFocused }
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base.focusable()
    Row(
        clickable.padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(this, focused)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            val nv = (value + d * step).coerceIn(min, max)
            if (nv != value) onChange(nv)
            true
        }
    ) { focused ->
        Text(label, color = Color(0xF2FFFFFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
        val frac = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
        Box(Modifier.width(150.dp).height(16.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x40FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .background(if (focused) ACC else Color(0x99FFFFFF))
                )
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (136f * frac).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
        Text(
            value.toString() + unit, color = Color(0xE6FFFFFF), fontSize = 16.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(62.dp)
        )
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            onChange(d > 0)
            true
        },
        onClick = { onChange(!checked) }
    ) {
        Text(label, color = Color(0xF2FFFFFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (checked) ACCENT else SolidColor(Color(0x40FFFFFF)))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val sel = selected.coerceIn(0, options.lastIndex)
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            val n = (sel + d).coerceIn(0, options.lastIndex)
            if (n != sel) onSelect(n)
            true
        },
        onClick = { onSelect((sel + 1) % options.size) }
    ) { focused ->
        Text(label, color = Color(0xF2FFFFFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(
            (if (focused) "‹  " else "") + options[sel] + (if (focused) "  ›" else ""),
            color = if (focused) Color.White else Color(0xCCFFFFFF),
            fontSize = 16.sp, fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun ActionRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier, onClick = onClick) { focused ->
        Text(text, color = Color(0xF2FFFFFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text("›", color = if (focused) ACC else Color(0xAAFFFFFF), fontSize = 22.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xA6FFFFFF), fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color(0xF2FFFFFF), fontSize = 16.sp, fontWeight = FontWeight.Normal)
    }
}
// ---------- Etiqueta de seccion ----------
/** Titulo pequeno que agrupa opciones dentro de una categoria. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color(0x80FFFFFF), fontSize = 11.sp, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** Fila de la lista de categorias: icono, nombre y una descripcion corta. */
@Composable
private fun CategoryRow(
    icon: String,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusRow(modifier = modifier, onClick = onClick) { focused ->
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (focused) ACC.copy(alpha = 0.22f) else Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(icon, if (focused) ACC else TAB_IDLE, 22)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color(0xF2FFFFFF), fontSize = 18.sp)
            Text(
                desc, color = Color(0x80FFFFFF), fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text("›", color = if (focused) ACC else Color(0xAAFFFFFF), fontSize = 22.sp)
    }
}

// ---------- Marco de cristal (compartido por Configuracion y Editar apps) ----------

/**
 * Panel de cristal pegado al lado derecho, de arriba a abajo. El inicio se hace mas angosto
 * (ver sidePanelWidth) para que el panel nunca quede encima de las apps.
 * El contenido se dibuja a PANEL_SCALE para verse mas compacto.
 */
@Composable
fun GlassPanelFrame(content: @Composable () -> Unit) {
    val conf = LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val us = LocalUiScale.current
    val ov = LocalOverscan.current / us
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "panelIn")
    val availW = conf.screenWidthDp.toFloat() / us - 2f * ov
    val pw = sidePanelWidth(availW)
    val shape = RoundedCornerShape(22.dp)
    val panelBg = Brush.verticalGradient(listOf(Themes.current.panelTop, Themes.current.panelBottom))
    val panelBorder = Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x1AFFFFFF)))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 8.dp, bottom = 8.dp, end = 6.dp)
                .width(pw.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = (1f - p) * 56.dp.toPx()
                    alpha = p
                }
                .clip(shape)
                .background(panelBg)
                .drawBehind {
                    // Brillo suave arriba, efecto cristal.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0x14FFFFFF), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.4f
                        )
                    )
                }
                .border(1.dp, panelBorder, shape)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(dens.density * PANEL_SCALE, dens.fontScale)
            ) { content() }
        }
    }
}

// ---------- Opciones ----------

/**
 * Una opcion de Configuracion: categoria, seccion donde se agrupa y su fila.
 * focusable = false para filas que solo muestran informacion (no reciben el foco inicial).
 * content recibe el Modifier que debe llevar la fila (aqui va el foco inicial).
 */
private class SItem(
    val cat: Int,
    val label: String,
    val sec: String = "",
    val focusable: Boolean = true,
    val content: @Composable (Modifier) -> Unit
)

/** Fila que abrio un sub-panel (fondos, apps, entradas): al volver, el foco regresa a ella. */
private var returnKey: String? = null
// ---------- Pantalla ----------

/**
 * Configuracion en un panel a la derecha. Dos niveles: lista de categorias (cat = -1) y las
 * opciones de una categoria. cat vive en MainActivity para que Atras funcione: dentro de una
 * categoria vuelve a la lista y desde la lista cierra el panel (una sola pulsacion cada vez).
 */
@Composable
fun SettingsScreen(
    style: DockStyle,
    cfg: Cfg,
    cat: Int,
    onCat: (Int) -> Unit,
    onSet: (String, Int) -> Unit,
    onResetDock: () -> Unit,
    onWalls: () -> Unit,
    onEdit: () -> Unit,
    onInputs: () -> Unit,
    onOpen: (String) -> Unit,
    onSetHome: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onNetPerm: () -> Unit,
    onPin: (String) -> Unit,
    onProfile: (Int) -> Unit = {},
    onBackupSave: () -> Unit = {},
    onBackupShare: () -> Unit = {},
    onRestorePick: () -> Unit = {},
    onUpdate: () -> Unit = {},
    updBack: Int = 0,
    blocked: Boolean = false
) {
    val ctx = LocalContext.current
    // Ultima categoria abierta: al volver a la lista el foco regresa a ella.
    var lastCat by remember { mutableIntStateOf(if (cat >= 0) cat else 0) }
    LaunchedEffect(cat) { if (cat >= 0) lastCat = cat }
    // Si venimos de un sub-panel, el foco vuelve a la fila que lo abrio (se lee una sola vez).
    val retKey = remember { returnKey.also { returnKey = null } }

    val dateLabels = remember { dateFormatLabels() }
    val cityOpts = if (cfg.city in CITIES) CITIES else listOf(cfg.city) + CITIES
    // Perfil que coincide con los ajustes actuales (0 = personalizado).
    val profSel = profileIndex(style.scalePct, style.spacing, style.corner, style.glow, cfg)

    // Todas las opciones en una sola lista; se muestra la de la categoria abierta.
    val items = buildList<SItem> {
        // ---- Apariencia ----
        add(SItem(0, "Cambiar fondo de pantalla", "Fondo") { m ->
            ActionRow("Cambiar fondo de pantalla", m) {
                returnKey = "Cambiar fondo de pantalla"
                onWalls()
            }
        })
        add(SItem(0, "Rotación de fondos", "Fondo") { m ->
            ChoiceRow(
                "Rotación de fondos",
                listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"),
                cfg.rot, m
            ) { cfg.setRot(it) }
        })
        add(SItem(0, "Transición suave", "Fondo") { m ->
            SwitchRow("Transición suave", cfg.fade, m) { cfg.setFade(it) }
        })
        add(SItem(0, "Pausar fondo animado", "Fondo") { m ->
            SwitchRow("Pausar fondo animado con paneles", cfg.pauseWall, m) { cfg.setPauseWall(it) }
        })
        add(SItem(0, "Fondo según app enfocada", "Fondo") { m ->
            ChoiceRow(
                "Fondo según app enfocada",
                listOf("Apagado", "Degradado", "Banner"),
                cfg.dynBg, m
            ) { cfg.setDynBg(it) }
        })
        add(SItem(0, "Perfil de diseño", "Diseño") { m ->
            // Izquierda/derecha cambia de perfil; la opcion "Personalizado" solo indica el estado.
            ChoiceRow("Perfil de diseño", PROFILE_OPTIONS, profSel, m) {
                onProfile(if (it > 0) it - 1 else 0)
            }
        })
        add(SItem(0, "Tema de color", "Diseño") { m ->
            ChoiceRow("Tema de color", Themes.list.map { it.name }, cfg.theme, m) { cfg.setTheme(it) }
        })
        add(SItem(0, "Brillo de color", "Diseño") { m ->
            ChoiceRow("Brillo de color", GLOW_NAMES, style.glow, m) { onSet("glow", it) }
        })
        add(SItem(0, "Color del brillo", "Diseño") { m ->
            ChoiceRow(
                "Color del brillo",
                listOf("Color de cada app", "Color del fondo", "Color del tema"),
                cfg.glowSrc, m
            ) { cfg.setGlowSrc(it) }
        })
        add(SItem(0, "Estilo de foco", "Diseño") { m ->
            ChoiceRow("Estilo de foco", FOCUS_STYLES, cfg.focusStyle, m) { cfg.setFocusStyle(it) }
        })
        add(SItem(0, "Oscurecer fondo", "Diseño") { m ->
            SliderRow("Oscurecer fondo", cfg.dim, 0, 80, 5, "%", m) { cfg.setDim(it) }
        })
        add(SItem(0, "Desenfoque al abrir paneles", "Diseño") { m ->
            SwitchRow("Desenfoque al abrir paneles", cfg.blur, m) { cfg.setBlur(it) }
        })
        add(SItem(0, "Animar cambio de pantallas", "Diseño") { m ->
            SwitchRow("Animar cambio de pantallas", cfg.screenAnim, m) { cfg.setScreenAnim(it) }
        })
        add(SItem(0, "Tamaño de la interfaz", "Pantalla") { m ->
            SliderRow("Tamaño de la interfaz", cfg.uiScale, 70, 130, 5, "%", m) { cfg.setUiScale(it) }
        })
        add(SItem(0, "Margen de seguridad", "Pantalla") { m ->
            SliderRow("Margen de seguridad", cfg.overscan, 0, 60, 2, " dp", m) { cfg.setOverscan(it) }
        })
        add(SItem(0, "Modo ahorro", "Rendimiento y noche") { m ->
            SwitchRow("Modo ahorro (TV modesta)", cfg.saver, m) { cfg.setSaver(it) }
        })
        add(SItem(0, "Sugerir modo ahorro", "Rendimiento y noche") { m ->
            SwitchRow("Sugerir modo ahorro si va lenta", cfg.saverHint, m) { cfg.setSaverHint(it) }
        })
        add(SItem(0, "Modo noche automático", "Rendimiento y noche") { m ->
            SwitchRow("Modo noche automático", cfg.night, m) { cfg.setNight(it) }
        })
        if (cfg.night) {
            add(SItem(0, "Noche desde", "Rendimiento y noche") { m ->
                SliderRow("Noche desde las", cfg.nightFrom, 0, 23, 1, ":00", m) { cfg.setNightFrom(it) }
            })
            add(SItem(0, "Noche hasta", "Rendimiento y noche") { m ->
                SliderRow("Noche hasta las", cfg.nightTo, 0, 23, 1, ":00", m) { cfg.setNightTo(it) }
            })
        }
        add(SItem(0, "Sonido al mover el foco", "Sonido") { m ->
            ChoiceRow(
                "Sonido al mover el foco",
                listOf("Apagado", "Suave", "Medio"),
                cfg.soundLevel, m
            ) { cfg.setSoundLevel(it) }
        })
        // ---- Dock ----
        add(SItem(1, "Tamaño general", "Fichas") { m ->
            SliderRow("Tamaño general", style.scalePct, 50, 180, 10, "%", m) { onSet("scale", it) }
        })
        add(SItem(1, "Separación", "Fichas") { m ->
            SliderRow("Separación", style.spacing, 0, 48, 2, " dp", m) { onSet("spacing", it) }
        })
        add(SItem(1, "Redondeo", "Fichas") { m ->
            SliderRow("Redondeo", style.corner, 0, 40, 2, " dp", m) { onSet("corner", it) }
        })
        add(SItem(1, "Margen inferior", "Fichas") { m ->
            SliderRow("Margen inferior", style.bottom, 0, 80, 2, " dp", m) { onSet("bottom", it) }
        })
        add(SItem(1, "Estilo de ficha", "Fichas") { m ->
            ChoiceRow("Estilo de ficha", TILE_STYLES, cfg.tileStyle, m) { cfg.setTileStyle(it) }
        })
        add(SItem(1, "Tarjeta de la app enfocada", "Información") { m ->
            SwitchRow("Tarjeta bajo el dock", cfg.focusCard, m) { cfg.setFocusCard(it) }
        })
        add(SItem(1, "Fila extra 1", "Filas extra") { m ->
            ChoiceRow("Fila extra 1", ROW_NAMES, cfg.row(0), m) { cfg.setRow(0, it) }
        })
        add(SItem(1, "Fila extra 2", "Filas extra") { m ->
            ChoiceRow("Fila extra 2", ROW_NAMES, cfg.row(1), m) { cfg.setRow(1, it) }
        })
        add(SItem(1, "Fila extra 3", "Filas extra") { m ->
            ChoiceRow("Fila extra 3", ROW_NAMES, cfg.row(2), m) { cfg.setRow(2, it) }
        })
        add(SItem(1, "Asignar apps", "Filas extra", false) {
            InfoRow("Asignar apps", "mantén OK en la app")
        })
        add(SItem(1, "Restablecer dock", "Restablecer") { m ->
            ActionRow("Restablecer dock", m) { onResetDock() }
        })
        // ---- Inicio ----
        add(SItem(2, "Reloj grande", "Reloj y fecha") { m ->
            SwitchRow("Reloj grande", cfg.bigClock, m) { cfg.setBigClock(it) }
        })
        add(SItem(2, "Formato de hora", "Reloj y fecha") { m ->
            ChoiceRow("Formato de hora", listOf("12 horas", "24 horas"), if (cfg.clock24) 1 else 0, m) {
                cfg.setClock24(it == 1)
            }
        })
        add(SItem(2, "Mostrar fecha", "Reloj y fecha") { m ->
            SwitchRow("Mostrar fecha", cfg.showDate, m) { cfg.setShowDate(it) }
        })
        add(SItem(2, "Formato de fecha", "Reloj y fecha") { m ->
            ChoiceRow("Formato de fecha", dateLabels, cfg.dateFmt, m) { cfg.setDateFmt(it) }
        })
        add(SItem(2, "Mostrar clima", "Clima") { m ->
            SwitchRow("Mostrar clima", cfg.showWeather, m) { cfg.setShowWeather(it) }
        })
        add(SItem(2, "Ciudad del clima", "Clima") { m ->
            ChoiceRow("Ciudad del clima", cityOpts, cityOpts.indexOf(cfg.city), m) {
                cfg.setCity(cityOpts[it])
            }
        })
        add(SItem(2, "Unidades", "Clima") { m ->
            ChoiceRow("Unidades", listOf("°C", "°F"), if (cfg.fahrenheit) 1 else 0, m) {
                cfg.setFahrenheit(it == 1)
            }
        })
        add(SItem(2, "Mostrar recientes", "Contenido") { m ->
            SwitchRow("Mostrar recientes", cfg.showRecents, m) { cfg.setShowRecents(it) }
        })
        add(SItem(2, "Botón de voz", "Contenido") { m ->
            ChoiceRow(
                "Botón de voz",
                listOf("Asistente de voz", "Buscar apps"),
                cfg.voiceMode, m
            ) { cfg.setVoiceMode(it) }
        })
        add(SItem(2, "Ocultar barra superior", "Barra superior") { m ->
            SwitchRow("Ocultar la barra superior sola", cfg.hideBar, m) { cfg.setHideBar(it) }
        })
        if (cfg.hideBar) {
            add(SItem(2, "Ocultar tras", "Barra superior") { m ->
                SliderRow("Ocultar tras", cfg.hideBarSecs, 3, 60, 3, " s", m) { cfg.setHideBarSecs(it) }
            })
        }
                add(SItem(2, "Modo ambiente", "Reposo") { m ->
            ChoiceRow(
                "Modo ambiente",
                listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"),
                cfg.ambient, m
            ) { cfg.setAmbient(it) }
        })
        add(SItem(2, "Protección contra quemado", "Reposo") { m ->
            SwitchRow("Mover el reloj en modo ambiente", cfg.burnIn, m) { cfg.setBurnIn(it) }
        })
        // ---- Música ----
        add(SItem(3, "Mostrar reproductor", "Reproductor") { m ->
            SwitchRow("Mostrar reproductor", cfg.showMusic, m) { cfg.setShowMusic(it) }
        })
        add(SItem(3, "Tamaño del reproductor", "Reproductor") { m ->
            SliderRow("Tamaño del reproductor", cfg.musicSize, 50, 120, 5, "%", m) { cfg.setMusicSize(it) }
        })
        add(SItem(3, "Posición del reproductor", "Reproductor") { m ->
            ChoiceRow(
                "Posición del reproductor",
                listOf("Junto al reloj", "Arriba al centro"),
                cfg.musicPos, m
            ) { cfg.setMusicPos(it) }
        })
        add(SItem(3, "Permitir acceso a notificaciones", "Permisos") { m ->
            ActionRow("Permitir acceso a notificaciones", m) {
                tryStart(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
        })
        // ---- Apps ----
        add(SItem(4, "Editar apps", "Organizar") { m ->
            ActionRow("Editar apps (favoritas, orden, tamaño, ocultas)", m) {
                returnKey = "Editar apps"
                onEdit()
            }
        })
        // ---- Sistema ----
        add(SItem(5, "Wi-Fi", "Conexiones") { m ->
            ActionRow("Wi-Fi", m) { onOpen("wifi") }
        })
        add(SItem(5, "Bluetooth", "Conexiones") { m ->
            ActionRow("Bluetooth", m) { onOpen("bt") }
        })
        add(SItem(5, "Entradas (HDMI)", "Conexiones") { m ->
            ActionRow("Entradas (HDMI)", m) {
                returnKey = "Entradas (HDMI)"
                onInputs()
            }
        })
        add(SItem(5, "Permitir nombre de red y Bluetooth", "Conexiones") { m ->
            ActionRow("Permitir nombre de red y Bluetooth", m) { onNetPerm() }
        })
        add(SItem(5, "Todos los ajustes de Android", "Sistema") { m ->
            ActionRow("Todos los ajustes de Android", m) { onOpen("all") }
        })
        add(SItem(5, "Menú de energía (accesibilidad)", "Sistema") { m ->
            ActionRow("Menú de energía (accesibilidad)", m) { onOpen("access") }
        })
        add(SItem(5, "Protector de pantalla del sistema", "Sistema") { m ->
            ActionRow("Protector de pantalla del sistema", m) { onOpen("dream") }
        })
        add(SItem(5, "Establecer como inicio predeterminado", "Sistema") { m ->
            ActionRow("Establecer como inicio predeterminado", m) { onSetHome() }
        })
        // ---- Seguridad ----
        if (!cfg.pinSet) {
            add(SItem(6, "Estado del PIN", "Código PIN", false) { InfoRow("Estado", "Sin PIN") })
            add(SItem(6, "Crear PIN de 4 dígitos", "Código PIN") { m ->
                ActionRow("Crear PIN de 4 dígitos", m) { onPin("create") }
            })
        } else {
            add(SItem(6, "Estado del PIN", "Código PIN", false) { InfoRow("Estado", "PIN activo") })
            add(SItem(6, "Cambiar PIN", "Código PIN") { m ->
                ActionRow("Cambiar PIN", m) { onPin("change") }
            })
            add(SItem(6, "Quitar PIN", "Código PIN") { m ->
                ActionRow("Quitar PIN", m) { onPin("remove") }
            })
            add(SItem(6, "Proteger ajustes", "Protección") { m ->
                SwitchRow("Proteger ajustes", cfg.lockSettings, m) { cfg.setLockSettings(it) }
            })
            add(SItem(6, "Proteger apps ocultas", "Protección") { m ->
                SwitchRow("Proteger apps ocultas", cfg.lockHidden, m) { cfg.setLockHidden(it) }
            })
        }
        // ---- Acerca de ----
        add(SItem(7, "Guardar copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Guardar copia de seguridad", m) { onBackup() }
        })
        add(SItem(7, "Restaurar copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Restaurar copia de seguridad", m) { onRestore() }
        })
        add(SItem(7, "Guardar copia en…", "Copias de seguridad") { m ->
            ActionRow("Guardar copia en… (elegir dónde)", m) { onBackupSave() }
        })
        add(SItem(7, "Compartir copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Compartir copia de seguridad", m) { onBackupShare() }
        })
        add(SItem(7, "Restaurar desde un archivo…", "Copias de seguridad") { m ->
            ActionRow("Restaurar desde un archivo…", m) { onRestorePick() }
        })
        // Actualizaciones desde GitHub Releases.
        add(SItem(7, "Buscar actualizaciones", "Actualizaciones") { m ->
            val fr = remember { FocusRequester() }
            LaunchedEffect(updBack) {
                if (updBack > 0) {
                    delay(200)
                    runCatching { fr.requestFocus() }
                }
            }
            ActionRow("Buscar actualizaciones", m.focusRequester(fr)) { onUpdate() }
        })
        add(SItem(7, "Avisar de actualizaciones", "Actualizaciones") { m ->
            SwitchRow("Avisar si hay versión nueva (1 vez al día)", cfg.updateCheck, m) { cfg.setUpdateCheck(it) }
        })
    }

    val shown = if (cat >= 0) items.filter { it.cat == cat } else emptyList()
    val inCat = cat in 0..CATS.lastIndex
    val title = if (inCat) CATS[cat].name else "¿Qué quieres cambiar?"
    val subtitle = when {
        inCat && cat <= 1 -> "Los cambios se ven en vivo a la izquierda"
        inCat -> CATS[cat].desc
        else -> "Elige una categoría"
    }
    val scroll = remember(cat) { androidx.compose.foundation.ScrollState(0) }
    val focusItem = (if (retKey != null) shown.firstOrNull { it.focusable && it.label == retKey } else null)
        ?: shown.firstOrNull { it.focusable }

    GlassPanelFrame {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !blocked }
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GearIcon(Color(0x99FFFFFF), 14)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONFIGURACIÓN", color = Color(0x99FFFFFF), fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal
                )
                if (inCat) {
                    Text(
                        "   ›   " + CATS[cat].name.uppercase(), color = ACC, fontSize = 13.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Normal, maxLines = 1
                    )
                }
            }
            Text(
                title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Normal,
                maxLines = 1, modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Text(
                subtitle, color = Color(0x80FFFFFF), fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (cat < 0) {
                    CATS.forEachIndexed { i, c ->
                        CategoryRow(
                            c.icon, c.name, c.desc,
                            if (i == lastCat) Modifier.autoFocus() else Modifier
                        ) { onCat(i) }
                    }
                } else if (cat == 7) {
                    AboutPage(
                        cfg = cfg,
                        updItems = shown.filter { it.sec == "Actualizaciones" },
                        bakItems = shown.filter { it.sec == "Copias de seguridad" }
                    )
                } else {
                    var lastGroup = ""
                    for (si in shown) {
                        val group = si.sec
                        if (group.isNotEmpty() && group != lastGroup) SectionLabel(group)
                        lastGroup = group
                        key(si.cat to si.label) {
                            si.content(if (si === focusItem) Modifier.autoFocus() else Modifier)
                        }
                    }
                }
            }
        }
    }
}
// ---------- Acerca de ----------

/** Datos que cambian con el tiempo o que se leen del sistema (se refrescan cada pocos segundos). */
private class AboutSnap(
    val res: String,
    val dpi: Int,
    val hz: Int,
    val storage: String,
    val ram: String,
    val temp: String?,
    val net: List<Pair<String, String>>
)

private fun fmtBytes(b: Long): String {
    val gb = b / 1073741824.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.forLanguageTag("es-VE"), "%.1f GB", gb)
    } else {
        (b / 1048576L).toString() + " MB"
    }
}

/** Temperatura del equipo si la TV la deja leer (zona termica o bateria); si no, null. */
private fun readTemp(ctx: Context): String? {
    for (i in 0..9) {
        val f = java.io.File("/sys/class/thermal/thermal_zone" + i + "/temp")
        val raw = runCatching { f.readText().trim().toDoubleOrNull() }.getOrNull() ?: continue
        val c = if (raw > 1000.0) raw / 1000.0 else raw
        if (c in 15.0..120.0) return Math.round(c).toString() + " °C"
    }
    try {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val present = bi?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ?: false
        if (present && t > 0) return Math.round(t / 10.0).toString() + " °C"
    } catch (e: Exception) {
    }
    return null
}

/** Primera direccion IPv4 de la TV (sin permisos). */
private fun localIp(): String? {
    try {
        val ifs = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in java.util.Collections.list(ifs)) {
            if (!ni.isUp || ni.isLoopback) continue
            for (a in java.util.Collections.list(ni.inetAddresses)) {
                if (!a.isLoopbackAddress && a is java.net.Inet4Address) return a.hostAddress
            }
        }
    } catch (e: Exception) {
    }
    return null
}

@Suppress("DEPRECATION")
private fun readAboutSnap(ctx: Context): AboutSnap {
    var res = "?"
    var dpi = 0
    var hz = 0
    try {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val dm = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        res = dm.widthPixels.toString() + " × " + dm.heightPixels.toString()
        dpi = dm.densityDpi
        hz = Math.round(wm.defaultDisplay.refreshRate)
    } catch (e: Exception) {
    }
    var storage = "No disponible"
    try {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        storage = fmtBytes(free) + " libres de " + fmtBytes(total)
    } catch (e: Exception) {
    }
    var ram = "No disponible"
    try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        ram = fmtBytes(mi.availMem) + " libres de " + fmtBytes(mi.totalMem)
    } catch (e: Exception) {
    }
    val ns = readNet(ctx)
    val net = ArrayList<Pair<String, String>>()
    val kindName = when (ns.kind) {
        1 -> "Wi-Fi"
        2 -> "Ethernet"
        3 -> "Otra"
        else -> "Sin conexión"
    }
    val inetName = when {
        ns.kind == 0 -> "—"
        ns.online -> "Disponible"
        else -> "Sin salida a internet"
    }
    net.add(Pair("Conexión", kindName))
    net.add(Pair("Internet", inetName))
    if (ns.kind == 1) {
        net.add(Pair("Red Wi-Fi", ns.ssid ?: "No visible (falta el permiso de ubicación)"))
    }
    net.add(Pair("Dirección IP", localIp() ?: "—"))
    val btName = if (ns.bt) {
        if (ns.btCount > 0) "Activado · " + ns.btCount.toString() + " conectado(s)" else "Activado"
    } else {
        "Apagado"
    }
    net.add(Pair("Bluetooth", btName))
    return AboutSnap(res, dpi, hz, storage, ram, readTemp(ctx), net)
}

/**
 * Tarjeta de solo lectura. Es una sola parada de foco: con las flechas se salta de tarjeta en
 * tarjeta y el panel se desplaza solo, asi se puede leer todo sin que el foco se pierda.
 */
@Composable
private fun InfoCard(lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color(0x2BFFFFFF) else Color(0x0DFFFFFF))
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        for (line in lines) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    line.first, color = Color(0xA6FFFFFF), fontSize = 17.sp,
                    modifier = Modifier.weight(0.42f).padding(end = 10.dp)
                )
                Text(
                    line.second, color = Color.White, fontSize = 18.sp,
                    textAlign = TextAlign.End, modifier = Modifier.weight(0.58f)
                )
            }
        }
    }
}

/** Linea fina que separa la informacion de las opciones. */
@Composable
private fun OptionsDivider() {
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Color(0x1FFFFFFF)))
    Spacer(Modifier.height(4.dp))
}

/**
 * Categoria "Acerca de", por secciones. La informacion va en tarjetas de solo lectura y las
 * opciones (actualizaciones y copias de seguridad) en filas aparte debajo de su seccion.
 */
@Suppress("DEPRECATION")
@Composable
private fun AboutPage(cfg: Cfg, updItems: List<SItem>, bakItems: List<SItem>) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val code = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode.toString() }
            .getOrNull() ?: "?"
    }
    val listenerOn = remember {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
    val wallCount = remember { listWalls(ctx).size }

    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            var frames = 0
            val start = withFrameNanos { it }
            var t = start
            while (t - start < 1_000_000_000L) {
                t = withFrameNanos { it }
                frames++
            }
            fps = frames
        }
    }

    var snap by remember { mutableStateOf(readAboutSnap(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            snap = withContext(Dispatchers.IO) { readAboutSnap(ctx) }
        }
    }

    SectionLabel("Versión y actualizaciones")
    InfoCard(
        listOf("Versión" to version, "Código de versión" to code),
        Modifier.autoFocus()
    )
    OptionsDivider()
    for (si in updItems) key(si.label) { si.content(Modifier) }

    SectionLabel("Equipo")
    InfoCard(
        listOf(
            "Dispositivo" to android.os.Build.MODEL,
            "Fabricante" to android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Android" to android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")",
            "Procesador" to (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?") + " · " +
                Runtime.getRuntime().availableProcessors() + " núcleos",
            "Apps instaladas" to AppCache.apps.size.toString(),
            "Fondos guardados" to wallCount.toString(),
            "Permiso de música" to if (listenerOn) "Activo" else "Falta permitir",
            "Fotogramas por segundo" to fps.toString()
        )
    )

    SectionLabel("Pantalla")
    InfoCard(
        listOf(
            "Resolución" to snap.res,
            "Densidad" to snap.dpi.toString() + " dpi",
            "Frecuencia" to snap.hz.toString() + " Hz",
            "Tamaño de la interfaz" to cfg.uiScale.toString() + " %",
            "Margen de seguridad" to cfg.overscan.toString() + " dp"
        )
    )

    SectionLabel("Almacenamiento y memoria")
    val mem = ArrayList<Pair<String, String>>()
    mem.add("Almacenamiento" to snap.storage)
    mem.add("Memoria (RAM)" to snap.ram)
    val temp = snap.temp
    if (temp != null) mem.add("Temperatura" to temp)
    InfoCard(mem)

    SectionLabel("Red")
    InfoCard(snap.net)

    SectionLabel("Copias de seguridad")
    for (si in bakItems) key(si.label) { si.content(Modifier) }
}
