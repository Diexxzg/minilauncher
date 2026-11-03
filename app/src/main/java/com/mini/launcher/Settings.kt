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
import java.text.Normalizer
import kotlinx.coroutines.delay

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

// ---------- Buscador ----------

/** Minusculas y sin acentos, para que "musica" encuentre "Música". */
private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/**
 * Una opcion de Configuracion: categoria, texto buscable, seccion donde se agrupa y su fila.
 * focusable = false para filas que solo muestran informacion (no reciben el foco inicial).
 * content recibe el Modifier que debe llevar la fila (aqui va el foco inicial).
 */
private class SItem(
    val cat: Int,
    val label: String,
    val keys: String = "",
    val sec: String = "",
    val focusable: Boolean = true,
    val content: @Composable (Modifier) -> Unit
)

// ---------- Pantalla ----------

/**
 * Configuracion en un panel a la derecha. Dos niveles: lista de categorias (cat = -1) y las
 * opciones de una categoria. cat y query viven en MainActivity para que Atras funcione:
 * primero borra la busqueda, luego vuelve a la lista y por ultimo cierra el panel.
 */
@Composable
fun SettingsScreen(
    style: DockStyle,
    cfg: Cfg,
    cat: Int,
    onCat: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
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

    val cityOpts = if (cfg.city in CITIES) CITIES else listOf(cfg.city) + CITIES
    // Perfil que coincide con los ajustes actuales (0 = personalizado).
    val profSel = profileIndex(style.scalePct, style.spacing, style.corner, style.glow, cfg)
        // Todas las opciones en una sola lista: sirve para mostrar una categoria y para buscar.
    val items = buildList<SItem> {
        // ---- Apariencia ----
        add(SItem(0, "Cambiar fondo de pantalla", "imagen video wallpaper", "Fondo") { m ->
            ActionRow("Cambiar fondo de pantalla", m) { onWalls() }
        })
        add(SItem(0, "Rotación de fondos", "cambiar fondos hora", "Fondo") { m ->
            ChoiceRow(
                "Rotación de fondos",
                listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"),
                cfg.rot, m
            ) { cfg.setRot(it) }
        })
        add(SItem(0, "Transición suave", "fundido fondo", "Fondo") { m ->
            SwitchRow("Transición suave", cfg.fade, m) { cfg.setFade(it) }
        })
        add(SItem(0, "Pausar fondo animado", "video gif ahorrar recursos paneles", "Fondo") { m ->
            SwitchRow("Pausar fondo animado con paneles", cfg.pauseWall, m) { cfg.setPauseWall(it) }
        })
        add(SItem(0, "Fondo según app enfocada", "degradado banner dinamico", "Fondo") { m ->
            ChoiceRow(
                "Fondo según app enfocada",
                listOf("Apagado", "Degradado", "Banner"),
                cfg.dynBg, m
            ) { cfg.setDynBg(it) }
        })
        add(SItem(0, "Perfil de diseño", "cine compacto minimalista clasico estilo dock", "Diseño") { m ->
            // Izquierda/derecha cambia de perfil; la opcion "Personalizado" solo indica el estado.
            ChoiceRow("Perfil de diseño", PROFILE_OPTIONS, profSel, m) {
                onProfile(if (it > 0) it - 1 else 0)
            }
        })
        add(SItem(0, "Tema de color", "colores acento", "Diseño") { m ->
            ChoiceRow("Tema de color", Themes.list.map { it.name }, cfg.theme, m) { cfg.setTheme(it) }
        })
        add(SItem(0, "Brillo de color", "glow resplandor", "Diseño") { m ->
            ChoiceRow("Brillo de color", GLOW_NAMES, style.glow, m) { onSet("glow", it) }
        })
        add(SItem(0, "Color del brillo", "glow acento", "Diseño") { m ->
            ChoiceRow(
                "Color del brillo",
                listOf("Color de cada app", "Color del fondo", "Color del tema"),
                cfg.glowSrc, m
            ) { cfg.setGlowSrc(it) }
        })
        add(SItem(0, "Oscurecer fondo", "brillo", "Diseño") { m ->
            SliderRow("Oscurecer fondo", cfg.dim, 0, 80, 5, "%", m) { cfg.setDim(it) }
        })
        add(SItem(0, "Desenfoque al abrir paneles", "blur", "Diseño") { m ->
            SwitchRow("Desenfoque al abrir paneles", cfg.blur, m) { cfg.setBlur(it) }
        })
        add(SItem(0, "Animar cambio de pantallas", "transiciones animaciones", "Diseño") { m ->
            SwitchRow("Animar cambio de pantallas", cfg.screenAnim, m) { cfg.setScreenAnim(it) }
        })
        add(SItem(0, "Tamaño de la interfaz", "escala zoom", "Pantalla") { m ->
            SliderRow("Tamaño de la interfaz", cfg.uiScale, 70, 130, 5, "%", m) { cfg.setUiScale(it) }
        })
        add(SItem(0, "Margen de seguridad", "overscan bordes", "Pantalla") { m ->
            SliderRow("Margen de seguridad", cfg.overscan, 0, 60, 2, " dp", m) { cfg.setOverscan(it) }
        })
        add(SItem(0, "Modo ahorro", "tv lenta rendimiento fps animaciones desenfoque brillo ahorrar", "Rendimiento y noche") { m ->
            SwitchRow("Modo ahorro (TV modesta)", cfg.saver, m) { cfg.setSaver(it) }
        })
        add(SItem(0, "Sugerir modo ahorro", "fps lento aviso rendimiento", "Rendimiento y noche") { m ->
            SwitchRow("Sugerir modo ahorro si va lenta", cfg.saverHint, m) { cfg.setSaverHint(it) }
        })
        add(SItem(0, "Modo noche automático", "noche brillo horario oscuro", "Rendimiento y noche") { m ->
            SwitchRow("Modo noche automático", cfg.night, m) { cfg.setNight(it) }
        })
        if (cfg.night) {
            add(SItem(0, "Noche desde", "modo noche hora inicio horario", "Rendimiento y noche") { m ->
                SliderRow("Noche desde las", cfg.nightFrom, 0, 23, 1, ":00", m) { cfg.setNightFrom(it) }
            })
            add(SItem(0, "Noche hasta", "modo noche hora fin horario", "Rendimiento y noche") { m ->
                SliderRow("Noche hasta las", cfg.nightTo, 0, 23, 1, ":00", m) { cfg.setNightTo(it) }
            })
        }
        add(SItem(0, "Sonido al mover el foco", "audio clic", "Sonido") { m ->
            ChoiceRow(
                "Sonido al mover el foco",
                listOf("Apagado", "Suave", "Medio"),
                cfg.soundLevel, m
            ) { cfg.setSoundLevel(it) }
        })
        // ---- Dock ----
        add(SItem(1, "Tamaño general", "dock fichas escala", "Fichas") { m ->
            SliderRow("Tamaño general", style.scalePct, 50, 180, 10, "%", m) { onSet("scale", it) }
        })
        add(SItem(1, "Separación", "dock espacio", "Fichas") { m ->
            SliderRow("Separación", style.spacing, 0, 48, 2, " dp", m) { onSet("spacing", it) }
        })
        add(SItem(1, "Redondeo", "dock esquinas", "Fichas") { m ->
            SliderRow("Redondeo", style.corner, 0, 40, 2, " dp", m) { onSet("corner", it) }
        })
        add(SItem(1, "Margen inferior", "dock", "Fichas") { m ->
            SliderRow("Margen inferior", style.bottom, 0, 80, 2, " dp", m) { onSet("bottom", it) }
        })
        add(SItem(1, "Tarjeta de la app enfocada", "nombre notificaciones ultimo uso texto bajo dock", "Información") { m ->
            SwitchRow("Tarjeta bajo el dock", cfg.focusCard, m) { cfg.setFocusCard(it) }
        })
        add(SItem(1, "Fila extra 1", "streaming juegos filas", "Filas extra") { m ->
            ChoiceRow("Fila extra 1", ROW_NAMES, cfg.row(0), m) { cfg.setRow(0, it) }
        })
        add(SItem(1, "Fila extra 2", "streaming juegos filas", "Filas extra") { m ->
            ChoiceRow("Fila extra 2", ROW_NAMES, cfg.row(1), m) { cfg.setRow(1, it) }
        })
        add(SItem(1, "Fila extra 3", "streaming juegos filas", "Filas extra") { m ->
            ChoiceRow("Fila extra 3", ROW_NAMES, cfg.row(2), m) { cfg.setRow(2, it) }
        })
        add(SItem(1, "Asignar apps", "filas mantén ok", "Filas extra", false) {
            InfoRow("Asignar apps", "mantén OK en la app")
        })
        add(SItem(1, "Restablecer dock", "reiniciar valores", "Restablecer") { m ->
            ActionRow("Restablecer dock", m) { onResetDock() }
        })
        // ---- Inicio ----
        add(SItem(2, "Reloj grande", "", "Reloj y fecha") { m ->
            SwitchRow("Reloj grande", cfg.bigClock, m) { cfg.setBigClock(it) }
        })
        add(SItem(2, "Formato de hora", "12 24 horas reloj", "Reloj y fecha") { m ->
            ChoiceRow("Formato de hora", listOf("12 horas", "24 horas"), if (cfg.clock24) 1 else 0, m) {
                cfg.setClock24(it == 1)
            }
        })
        add(SItem(2, "Mostrar fecha", "", "Reloj y fecha") { m ->
            SwitchRow("Mostrar fecha", cfg.showDate, m) { cfg.setShowDate(it) }
        })
        add(SItem(2, "Mostrar clima", "tiempo temperatura", "Clima") { m ->
            SwitchRow("Mostrar clima", cfg.showWeather, m) { cfg.setShowWeather(it) }
        })
        add(SItem(2, "Ciudad del clima", "tiempo ubicación", "Clima") { m ->
            ChoiceRow("Ciudad del clima", cityOpts, cityOpts.indexOf(cfg.city), m) {
                cfg.setCity(cityOpts[it])
            }
        })
        add(SItem(2, "Unidades", "celsius fahrenheit grados temperatura", "Clima") { m ->
            ChoiceRow("Unidades", listOf("°C", "°F"), if (cfg.fahrenheit) 1 else 0, m) {
                cfg.setFahrenheit(it == 1)
            }
        })
        add(SItem(2, "Mostrar recientes", "historial", "Contenido") { m ->
            SwitchRow("Mostrar recientes", cfg.showRecents, m) { cfg.setShowRecents(it) }
        })
        add(SItem(2, "Botón de voz", "micrófono asistente búsqueda", "Contenido") { m ->
            ChoiceRow(
                "Botón de voz",
                listOf("Asistente de voz", "Buscar apps"),
                cfg.voiceMode, m
            ) { cfg.setVoiceMode(it) }
        })
        add(SItem(2, "Modo ambiente", "protector reposo", "Reposo") { m ->
            ChoiceRow(
                "Modo ambiente",
                listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"),
                cfg.ambient, m
            ) { cfg.setAmbient(it) }
        })
        add(SItem(2, "Protección contra quemado", "ambiente reloj mover pantalla oled", "Reposo") { m ->
            SwitchRow("Mover el reloj en modo ambiente", cfg.burnIn, m) { cfg.setBurnIn(it) }
        })
        // ---- Música ----
        add(SItem(3, "Mostrar reproductor", "música", "Reproductor") { m ->
            SwitchRow("Mostrar reproductor", cfg.showMusic, m) { cfg.setShowMusic(it) }
        })
        add(SItem(3, "Tamaño del reproductor", "música", "Reproductor") { m ->
            SliderRow("Tamaño del reproductor", cfg.musicSize, 50, 120, 5, "%", m) { cfg.setMusicSize(it) }
        })
        add(SItem(3, "Posición del reproductor", "música", "Reproductor") { m ->
            ChoiceRow(
                "Posición del reproductor",
                listOf("Junto al reloj", "Arriba al centro"),
                cfg.musicPos, m
            ) { cfg.setMusicPos(it) }
        })
        add(SItem(3, "Permitir acceso a notificaciones", "música permiso", "Permisos") { m ->
            ActionRow("Permitir acceso a notificaciones", m) {
                tryStart(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
        })
        // ---- Apps ----
        add(SItem(4, "Editar apps", "favoritas orden tamaño ocultas", "Organizar") { m ->
            ActionRow("Editar apps (favoritas, orden, tamaño, ocultas)", m) { onEdit() }
        })
        // ---- Sistema ----
        add(SItem(5, "Wi-Fi", "red internet", "Conexiones") { m ->
            ActionRow("Wi-Fi", m) { onOpen("wifi") }
        })
        add(SItem(5, "Bluetooth", "control audífonos", "Conexiones") { m ->
            ActionRow("Bluetooth", m) { onOpen("bt") }
        })
        add(SItem(5, "Entradas (HDMI)", "fuentes", "Conexiones") { m ->
            ActionRow("Entradas (HDMI)", m) { onInputs() }
        })
        add(SItem(5, "Permitir nombre de red y Bluetooth", "permiso ubicación", "Conexiones") { m ->
            ActionRow("Permitir nombre de red y Bluetooth", m) { onNetPerm() }
        })
        add(SItem(5, "Todos los ajustes de Android", "sistema", "Sistema") { m ->
            ActionRow("Todos los ajustes de Android", m) { onOpen("all") }
        })
        add(SItem(5, "Menú de energía (accesibilidad)", "apagar reiniciar", "Sistema") { m ->
            ActionRow("Menú de energía (accesibilidad)", m) { onOpen("access") }
        })
        add(SItem(5, "Protector de pantalla del sistema", "dream", "Sistema") { m ->
            ActionRow("Protector de pantalla del sistema", m) { onOpen("dream") }
        })
        add(SItem(5, "Establecer como inicio predeterminado", "launcher home", "Sistema") { m ->
            ActionRow("Establecer como inicio predeterminado", m) { onSetHome() }
        })
        // ---- Seguridad ----
        if (!cfg.pinSet) {
            add(SItem(6, "Estado del PIN", "", "Código PIN", false) { InfoRow("Estado", "Sin PIN") })
            add(SItem(6, "Crear PIN de 4 dígitos", "contraseña bloqueo", "Código PIN") { m ->
                ActionRow("Crear PIN de 4 dígitos", m) { onPin("create") }
            })
        } else {
            add(SItem(6, "Estado del PIN", "", "Código PIN", false) { InfoRow("Estado", "PIN activo") })
            add(SItem(6, "Cambiar PIN", "contraseña", "Código PIN") { m ->
                ActionRow("Cambiar PIN", m) { onPin("change") }
            })
            add(SItem(6, "Quitar PIN", "contraseña", "Código PIN") { m ->
                ActionRow("Quitar PIN", m) { onPin("remove") }
            })
            add(SItem(6, "Proteger ajustes", "bloqueo pin", "Protección") { m ->
                SwitchRow("Proteger ajustes", cfg.lockSettings, m) { cfg.setLockSettings(it) }
            })
            add(SItem(6, "Proteger apps ocultas", "bloqueo pin", "Protección") { m ->
                SwitchRow("Proteger apps ocultas", cfg.lockHidden, m) { cfg.setLockHidden(it) }
            })
        }
        // ---- Acerca de ----
        add(SItem(7, "Guardar copia de seguridad", "backup respaldo", "Copias de seguridad") { m ->
            ActionRow("Guardar copia de seguridad", m) { onBackup() }
        })
        add(SItem(7, "Restaurar copia de seguridad", "backup respaldo", "Copias de seguridad") { m ->
            ActionRow("Restaurar copia de seguridad", m) { onRestore() }
        })
        add(SItem(7, "Guardar copia en…", "backup respaldo elegir carpeta usb ubicación", "Copias de seguridad") { m ->
            ActionRow("Guardar copia en… (elegir dónde)", m) { onBackupSave() }
        })
        add(SItem(7, "Compartir copia de seguridad", "backup respaldo enviar archivo", "Copias de seguridad") { m ->
            ActionRow("Compartir copia de seguridad", m) { onBackupShare() }
        })
        add(SItem(7, "Restaurar desde un archivo…", "backup respaldo elegir archivo importar", "Copias de seguridad") { m ->
            ActionRow("Restaurar desde un archivo…", m) { onRestorePick() }
        })
        // Actualizaciones desde GitHub Releases.
        add(SItem(7, "Buscar actualizaciones", "actualizar version nueva update descargar instalar", "Actualizaciones") { m ->
            val fr = remember { FocusRequester() }
            LaunchedEffect(updBack) {
                if (updBack > 0) {
                    delay(200)
                    runCatching { fr.requestFocus() }
                }
            }
            ActionRow("Buscar actualizaciones", m.focusRequester(fr)) { onUpdate() }
        })
        add(SItem(7, "Avisar de actualizaciones", "actualizar aviso diario version nueva", "Actualizaciones") { m ->
            SwitchRow("Avisar si hay versión nueva (1 vez al día)", cfg.updateCheck, m) { cfg.setUpdateCheck(it) }
        })
    }

    val q = norm(query.trim())
    val searching = q.isNotEmpty()
    val words = q.split(" ").filter { it.isNotEmpty() }
    val shown = if (searching) {
        items.filter { si ->
            val hay = norm(si.label + " " + si.keys + " " + si.sec + " " + CATS[si.cat].name)
            words.all { w -> hay.contains(w) }
        }
    } else if (cat >= 0) {
        items.filter { it.cat == cat }
    } else {
        emptyList()
    }
    val inCat = cat in 0..CATS.lastIndex && !searching
    val title = when {
        searching -> "Resultados"
        inCat -> CATS[cat].name
        else -> "¿Qué quieres cambiar?"
    }
    val subtitle = when {
        searching -> if (shown.size == 1) "1 opción" else shown.size.toString() + " opciones"
        inCat && cat <= 1 -> "Los cambios se ven en vivo a la izquierda"
        inCat -> CATS[cat].desc
        else -> "Elige una categoría o busca una opción"
    }
    val scroll = remember(cat, searching) { androidx.compose.foundation.ScrollState(0) }
    val firstFocus = shown.firstOrNull { it.focusable }

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
            SearchField(
                value = query,
                onChange = onQuery,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                placeholder = "Buscar en configuración…"
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!searching && cat < 0) {
                    CATS.forEachIndexed { i, c ->
                        CategoryRow(
                            c.icon, c.name, c.desc,
                            if (i == lastCat) Modifier.autoFocus() else Modifier
                        ) { onCat(i) }
                    }
                } else {
                    if (cat == 7 && !searching) {
                        SectionLabel("Esta TV")
                        AboutInfo()
                    }
                    if (searching && shown.isEmpty()) {
                        InfoRow("Sin resultados", "")
                    }
                    var lastGroup = ""
                    for (si in shown) {
                        val group = if (searching) CATS[si.cat].name else si.sec
                        if (group.isNotEmpty() && group != lastGroup) SectionLabel(group)
                        lastGroup = group
                        key(si.cat to si.label) {
                            si.content(
                                if (!searching && si === firstFocus) Modifier.autoFocus() else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Datos de la app y del equipo (categoria "Acerca de"). */
@Composable
private fun AboutInfo() {
    val ctx = LocalContext.current
    val version = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val listenerOn = remember {
        NotificationManagerCompat.getEnabledListenerPackages(ctx)
            .contains(ctx.packageName)
    }
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
    val wallCount = remember { listWalls(ctx).size }
    InfoRow("Versión", version)
    InfoRow("Permiso de música", if (listenerOn) "Activo" else "Falta permitir")
    InfoRow("Fotogramas por segundo", fps.toString())
    InfoRow("Apps instaladas", AppCache.apps.size.toString())
    InfoRow("Fondos guardados", wallCount.toString())
    InfoRow("Android", android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
    InfoRow("Dispositivo", android.os.Build.MODEL)
}
