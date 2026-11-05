package com.mini.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class Cat(val icon: String, val name: String, val desc: String)

private val CATS = listOf(
    Cat("palette", "Fondo y color", "Fondos, temas, acento y brillo"),
    Cat("layout", "Dock y diseño", "Perfil, fichas, filas y efectos"),
    Cat("home", "Inicio", "Parte superior, reloj, clima y contenido"),
    Cat("bolt", "Pantalla y energía", "Tamaño, ahorro, noche, reposo y sonido"),
    Cat("music", "Música", "Reproductor y posición"),
    Cat("device", "Apps", "Organizar, carpetas y apps nuevas"),
    Cat("globe", "Sistema", "Red, celular, botones de color y arranque"),
    Cat("shield", "Permisos", "Calendario, ubicación, red y notificaciones"),
    Cat("lock", "Seguridad", "PIN, protección y perfil para niños"),
    Cat("eye", "Accesibilidad", "Texto grande, contraste y foco"),
    Cat("info", "Acerca de", "Versión, equipo, copias y actualizaciones")
)

/** Indice de la categoría "Acerca de" (la última de la lista). */
private const val ABOUT = 10

/** Orden de las secciones dentro de cada categoría (las no listadas van al final, en orden de código). */
private val SEC_ORDER = mapOf(
    0 to listOf("Fondo", "Color"),
    1 to listOf("Perfil", "Fichas", "Información", "Filas extra", "Foco y efectos", "Compartir diseño"),
    2 to listOf("Parte superior", "Reloj y fecha", "Tarjetas junto al reloj", "Clima", "Contenido", "Barra superior"),
    3 to listOf("Pantalla", "Rendimiento", "Noche", "Reposo", "Diseño del modo ambiente", "Sonido"),
    6 to listOf("Conexiones", "Sistema", "Celular", "Botones de color", "Arranque", "Escena de cine", "Rutina diaria", "Asistente"),
    7 to listOf("Permisos")
)

/** Categorías que muestran la vista previa en vivo a la izquierda (Fondo y color, Dock y diseño). */
fun settingsPreviewCat(cat: Int): Boolean = cat in 0..1

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
fun sidePanelWidth(availDp: Float, wide: Boolean = false): Float =
    if (wide) {
        // Configuracion: categorias a la izquierda y opciones a la derecha.
        (availDp * 0.38f).coerceIn(440f, 540f).coerceAtMost(availDp * 0.5f)
    } else {
        (availDp * 0.40f).coerceIn(400f, 560f).coerceAtMost(availDp * 0.62f)
    }

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
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) ACC.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                (if (focused) "‹  " else "") + options[sel] + (if (focused) "  ›" else ""),
                color = if (focused) Color.White else Color(0xCCFFFFFF),
                fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1
            )
        }
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
/** Titulo pequeno que agrupa opciones dentro de una categoria (con una linea fina a la derecha). */
@Composable
private fun SectionLabel(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text.uppercase(), color = ACC, fontSize = 12.sp, letterSpacing = 1.2.sp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x1AFFFFFF)))
    }
}

/** Tarjeta de cristal que agrupa las filas de una seccion. */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), shape)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { content() }
}

/** Pestaña de categoria (solo icono): se selecciona al recibir el foco, sin pulsar OK. */
@Composable
private fun TabChip(
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    onFocus: () -> Unit,
    onEnter: () -> Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color(0x2BFFFFFF)
                    selected -> ACC.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape) else Modifier)
            .drawBehind {
                if (dot) {
                    // Punto: esta categoria tiene ajustes que ya cambiaste.
                    drawCircle(ACC, radius = 3.dp.toPx(), center = Offset(size.width - 8.dp.toPx(), 8.dp.toPx()))
                }
                if (selected) {
                    drawRoundRect(
                        color = ACC,
                        topLeft = Offset(size.width * 0.3f, size.height - 4.dp.toPx()),
                        size = Size(size.width * 0.4f, 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
            .onKeyEvent { e ->
                // Abajo entra directo a la primera opcion de la categoria (sin depender de la busqueda de foco).
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) onEnter() else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(icon, if (focused || selected) ACC else TAB_IDLE, 22)
    }
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

/** Fila de la columna de categorias: icono y nombre; la seleccionada queda resaltada. */
@Composable
private fun RailItem(
    icon: String,
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> Color(0x2BFFFFFF)
                    selected -> ACC.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        color = ACC,
                        topLeft = Offset(3.dp.toPx(), size.height * 0.24f),
                        size = Size(3.dp.toPx(), size.height * 0.52f),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused || selected) ACC.copy(alpha = 0.22f) else Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(icon, if (focused || selected) ACC else TAB_IDLE, 20)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = if (focused || selected) Color.White else Color(0xCCFFFFFF),
            fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- Marco de cristal (compartido por Configuracion y Editar apps) ----------

/**
 * Panel de cristal pegado al lado derecho, de arriba a abajo. El inicio se hace mas angosto
 * (ver sidePanelWidth) para que el panel nunca quede encima de las apps.
 * El contenido se dibuja a PANEL_SCALE para verse mas compacto.
 */
@Composable
fun GlassPanelFrame(wide: Boolean = false, content: @Composable () -> Unit) {
    val conf = LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val us = LocalUiScale.current
    val ov = LocalOverscan.current / us
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "panelIn")
    val availW = conf.screenWidthDp.toFloat() / us - 2f * ov
    val pw = sidePanelWidth(availW, wide)
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
                    androidx.compose.ui.unit.Density(dens.density * (if (wide) 0.80f else PANEL_SCALE), dens.fontScale)
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
    onRightFocus: (Boolean) -> Unit = {},
    railBack: Int = 0,
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
    onDesignSave: () -> Unit = {},
    onDesignPick: () -> Unit = {},
    onWizard: () -> Unit = {},
    updBack: Int = 0,
    blocked: Boolean = false
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    // Permisos que se piden solo al activar la opcion que los necesita.
    var permTick by remember { mutableIntStateOf(0) }
    var calWantsOn by remember { mutableStateOf(false) }
    var locWantsOn by remember { mutableStateOf(false) }
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permTick++
        if (calWantsOn) {
            if (ok) {
                cfg.setCardEvent(true)
            } else {
                toast(ctx, "Sin el permiso del calendario no se puede mostrar el próximo evento.")
            }
        }
        calWantsOn = false
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permTick++
        if (locWantsOn) {
            if (ok) {
                cfg.setAutoLoc(true)
                WeatherOpts.autoLoc = 1
            } else {
                toast(ctx, "Sin el permiso de ubicación se usa la ciudad elegida.")
            }
        }
        locWantsOn = false
    }
    // Weather.kt lee estos dos ajustes fuera de Configuracion: se mantienen al dia aqui.
    SideEffect {
        WeatherOpts.forecast = if (cfg.forecast) 1 else 0
        WeatherOpts.autoLoc = if (cfg.autoLoc) 1 else 0
    }
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
        add(SItem(0, "Presentación de fondos", "Fondo") { m ->
            SwitchRow("Presentación de fondos (cambian solos)", cfg.wallShow, m) { cfg.setWallShow(it) }
        })
        if (cfg.wallShow) {
            add(SItem(0, "Cambiar cada", "Fondo") { m ->
                SliderRow("Cambiar el fondo cada", cfg.wallShowMins, 5, 120, 5, " min", m) { cfg.setWallShowMins(it) }
            })
        }
        add(SItem(0, "Clima en el fondo", "Fondo") { m ->
            SwitchRow("Clima en el fondo (se apaga con modo ahorro)", cfg.weatherFx, m) { cfg.setWeatherFx(it) }
        })
        add(SItem(1, "Perfil de diseño", "Perfil") { m ->
            // Izquierda/derecha cambia de perfil; la opcion "Personalizado" solo indica el estado.
            ChoiceRow("Perfil de diseño", PROFILE_OPTIONS, profSel, m) {
                onProfile(if (it > 0) it - 1 else 0)
            }
        })
        add(SItem(0, "Tema de color", "Color") { m ->
            ChoiceRow("Tema de color", Themes.list.map { it.name }, cfg.theme, m) { cfg.setTheme(it) }
        })
        add(SItem(0, "Tema automático", "Color") { m ->
            SwitchRow("Tema automático (día y noche)", cfg.autoTheme, m) { cfg.setAutoTheme(it) }
        })
        add(SItem(0, "Color de acento propio", "Color") { m ->
            DialogRow(
                if (cfg.accentCustom != 0) "Color de acento propio (activo)" else "Color de acento propio",
                m
            ) { close ->
                AccentPickerDialog(
                    current = cfg.accentCustom,
                    onSave = {
                        cfg.setAccentCustom(it)
                        close()
                    },
                    onClear = {
                        cfg.setAccentCustom(0)
                        close()
                    },
                    onClose = close
                )
            }
        })
        add(SItem(0, "Brillo de color", "Color") { m ->
            ChoiceRow("Brillo de color", GLOW_NAMES, style.glow, m) { onSet("glow", it) }
        })
        add(SItem(0, "Color del brillo", "Color") { m ->
            ChoiceRow(
                "Color del brillo",
                listOf("Color de cada app", "Color del fondo", "Color del tema"),
                cfg.glowSrc, m
            ) { cfg.setGlowSrc(it) }
        })
        add(SItem(1, "Estilo de foco", "Foco y efectos") { m ->
            ChoiceRow("Estilo de foco", FOCUS_STYLES, cfg.focusStyle, m) { cfg.setFocusStyle(it) }
        })
        add(SItem(0, "Oscurecer fondo", "Color") { m ->
            SliderRow("Oscurecer fondo", cfg.dim, 0, 80, 5, "%", m) { cfg.setDim(it) }
        })
        add(SItem(1, "Desenfoque al abrir paneles", "Foco y efectos") { m ->
            SwitchRow("Desenfoque al abrir paneles", cfg.blur, m) { cfg.setBlur(it) }
        })
        add(SItem(1, "Animar cambio de pantallas", "Foco y efectos") { m ->
            SwitchRow("Animar cambio de pantallas", cfg.screenAnim, m) { cfg.setScreenAnim(it) }
        })
        add(SItem(1, "Animación al abrir apps", "Foco y efectos") { m ->
            SwitchRow("Animación al abrir una app", cfg.openAnim, m) { cfg.setOpenAnim(it) }
        })
        add(SItem(3, "Tamaño de la interfaz", "Pantalla") { m ->
            SliderRow("Tamaño de la interfaz", cfg.uiScale, 70, 130, 5, "%", m) { cfg.setUiScale(it) }
        })
        add(SItem(3, "Margen de seguridad", "Pantalla") { m ->
            SliderRow("Margen de seguridad", cfg.overscan, 0, 60, 2, " dp", m) { cfg.setOverscan(it) }
        })
        add(SItem(3, "Fluidez máxima", "Rendimiento") { m ->
            SwitchRow("Fluidez máxima", cfg.fluid, m) { cfg.setFluid(it) }
        })
        add(SItem(3, "Modo ahorro", "Rendimiento") { m ->
            SwitchRow("Modo ahorro (TV modesta)", cfg.saverOnly, m) { cfg.setSaver(it) }
        })
        add(SItem(3, "Sugerir modo ahorro", "Rendimiento") { m ->
            SwitchRow("Sugerir modo ahorro si va lenta", cfg.saverHint, m) { cfg.setSaverHint(it) }
        })
        add(SItem(3, "Modo noche automático", "Noche") { m ->
            SwitchRow("Modo noche automático", cfg.night, m) { cfg.setNight(it) }
        })
        if (cfg.night) {
            add(SItem(3, "Noche desde", "Noche") { m ->
                SliderRow("Noche desde las", cfg.nightFrom, 0, 23, 1, ":00", m) { cfg.setNightFrom(it) }
            })
            add(SItem(3, "Noche hasta", "Noche") { m ->
                SliderRow("Noche hasta las", cfg.nightTo, 0, 23, 1, ":00", m) { cfg.setNightTo(it) }
            })
        }
        add(SItem(3, "Sonido al mover el foco", "Sonido") { m ->
            ChoiceRow(
                "Sonido al mover el foco",
                listOf("Apagado", "Suave", "Medio"),
                cfg.soundLevel, m
            ) { cfg.setSoundLevel(it) }
        })
        add(SItem(3, "Sonido de paneles", "Sonido") { m ->
            SwitchRow("Sonido al abrir y cerrar paneles", cfg.panelSounds, m) { cfg.setPanelSounds(it) }
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
        if (cfg.tileStyle >= 2) {
            add(SItem(1, "Iconos uniformes", "Fichas") { m ->
                SwitchRow("Iconos uniformes", cfg.uniformIcons, m) { cfg.setUniformIcons(it) }
            })
        }
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
        add(SItem(1, "Exportar diseño", "Compartir diseño") { m ->
            ActionRow("Guardar el diseño en un archivo", m) { onDesignSave() }
        })
        add(SItem(1, "Importar diseño", "Compartir diseño") { m ->
            ActionRow("Cargar un diseño desde un archivo", m) { onDesignPick() }
        })
        // ---- Inicio ----
        add(SItem(2, "Elementos de arriba", "Parte superior") { m ->
            ChoiceRow(
                "Elementos de arriba",
                listOf("Completa", "Reducida", "Mínima"),
                cfg.topMode, m
            ) { cfg.setTopMode(it) }
        })
        add(SItem(2, "Aviso de elementos de arriba", "Parte superior", false) {
            Text(
                when (cfg.topMode) {
                    0 -> "Se ve todo: reloj, fecha, clima, tarjetas, música, red y botón Más."
                    1 -> "Reloj, fecha, clima, música y ajustes. Sin tarjetas, red ni botón Más."
                    else -> "Solo el reloj y el botón de ajustes."
                },
                color = Color(0x99FFFFFF), fontSize = 14.sp,
                modifier = Modifier.padding(start = 22.dp, end = 16.dp, bottom = 4.dp)
            )
        })
        add(SItem(2, "Tamaño de arriba", "Parte superior") { m ->
            SliderRow("Tamaño de la parte superior", cfg.topScale, 60, 150, 10, " %", m) { cfg.setTopScale(it) }
        })
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
        add(SItem(2, "Próximo evento", "Tarjetas junto al reloj") { m ->
            SwitchRow("Próximo evento del calendario", cfg.cardEvent, m) { on ->
                if (on && !hasCalendarPermission(ctx)) {
                    calWantsOn = true
                    calPerm.launch(Manifest.permission.READ_CALENDAR)
                } else {
                    cfg.setCardEvent(on)
                }
            }
        })
        add(SItem(2, "Tarjeta de nota", "Tarjetas junto al reloj") { m ->
            SwitchRow("Tarjeta con una nota", cfg.cardNote, m) { cfg.setCardNote(it) }
        })
        add(SItem(2, "Escribir la nota", "Tarjetas junto al reloj") { m ->
            DialogRow(if (cfg.noteText.isBlank()) "Escribir la nota" else "Editar la nota", m) { close ->
                TextEditDialog(
                    title = "Nota del inicio",
                    initial = cfg.noteText,
                    hint = "Se muestra en una tarjeta junto al reloj.",
                    maxLen = 80,
                    onSave = {
                        cfg.setNoteText(it)
                        close()
                    },
                    onClose = close
                )
            }
        })
        add(SItem(2, "Tarjeta de cuenta regresiva", "Tarjetas junto al reloj") { m ->
            SwitchRow("Tarjeta de cuenta regresiva", cfg.cardCountdown, m) { cfg.setCardCountdown(it) }
        })
        add(SItem(2, "Fecha de la cuenta regresiva", "Tarjetas junto al reloj") { m ->
            DialogRow("Fecha: " + cfg.countdownDate.ifBlank { "sin elegir" }, m) { close ->
                TextEditDialog(
                    title = "Fecha de la cuenta regresiva",
                    initial = cfg.countdownDate,
                    hint = "Escribe año-mes-día, por ejemplo 2026-12-25.",
                    maxLen = 10,
                    onSave = { v ->
                        if (v.isEmpty() || validDate(v)) {
                            cfg.setCountdownDate(v)
                            close()
                        } else {
                            toast(ctx, "Fecha no válida. Usa año-mes-día, por ejemplo 2026-12-25.")
                        }
                    },
                    onClose = close
                )
            }
        })
        add(SItem(2, "Nombre de la cuenta regresiva", "Tarjetas junto al reloj") { m ->
            DialogRow("Nombre: " + cfg.countdownLabel.ifBlank { "sin elegir" }, m) { close ->
                TextEditDialog(
                    title = "Nombre de la cuenta regresiva",
                    initial = cfg.countdownLabel,
                    hint = "Por ejemplo: Navidad o Mi cumpleaños.",
                    maxLen = 30,
                    onSave = {
                        cfg.setCountdownLabel(it)
                        close()
                    },
                    onClose = close
                )
            }
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
        add(SItem(2, "Pronóstico de 3 días", "Clima") { m ->
            SwitchRow("Pronóstico de 3 días", cfg.forecast, m) {
                cfg.setForecast(it)
                WeatherOpts.forecast = if (it) 1 else 0
            }
        })
        add(SItem(2, "Ubicación automática", "Clima") { m ->
            SwitchRow("Ubicación automática (si la TV la entrega)", cfg.autoLoc, m) { on ->
                if (on && !hasLocationPermission(ctx)) {
                    locWantsOn = true
                    locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    cfg.setAutoLoc(on)
                    WeatherOpts.autoLoc = if (on) 1 else 0
                }
            }
        })
        add(SItem(2, "Fila Continuar", "Contenido") { m ->
            SwitchRow("Fila «Continuar» (última app y las de hoy)", cfg.continueRow, m) { cfg.setContinueRow(it) }
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
                add(SItem(3, "Modo ambiente", "Reposo") { m ->
            ChoiceRow(
                "Modo ambiente",
                listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"),
                cfg.ambient, m
            ) { cfg.setAmbient(it) }
        })
        add(SItem(3, "Protección contra quemado", "Reposo") { m ->
            SwitchRow("Mover el reloj en modo ambiente", cfg.burnIn, m) { cfg.setBurnIn(it) }
        })
        add(SItem(3, "Oscurecer en modo ambiente", "Diseño del modo ambiente") { m ->
            SwitchRow("Oscurecer el fondo", cfg.ambDimOn, m) { cfg.setAmbDimOn(it) }
        })
        if (cfg.ambDimOn) {
            add(SItem(3, "Nivel de oscurecimiento", "Diseño del modo ambiente") { m ->
                SliderRow("Nivel de oscurecimiento", cfg.ambDimLvl, 10, 85, 5, "%", m) { cfg.setAmbDimLvl(it) }
            })
        }
        add(SItem(3, "Fondo animado en reposo", "Diseño del modo ambiente") { m ->
            SwitchRow("Mostrar el fondo animado", cfg.ambAnim, m) { cfg.setAmbAnim(it) }
        })
        add(SItem(3, "Posición de la hora", "Diseño del modo ambiente") { m ->
            ChoiceRow("Hora", AMB_POS_OPTIONS, cfg.ambPosTime, m) { cfg.setAmbPosTime(it) }
        })
        add(SItem(3, "Posición de la fecha", "Diseño del modo ambiente") { m ->
            ChoiceRow("Fecha y clima", AMB_POS_OPTIONS, cfg.ambPosDate, m) { cfg.setAmbPosDate(it) }
        })
        add(SItem(3, "Posición del reproductor", "Diseño del modo ambiente") { m ->
            ChoiceRow("Reproductor", AMB_POS_OPTIONS, cfg.ambPosMusic, m) { cfg.setAmbPosMusic(it) }
        })
        add(SItem(3, "Posición de los iconos", "Diseño del modo ambiente") { m ->
            ChoiceRow("Iconos", AMB_POS_OPTIONS, cfg.ambPosIcons, m) { cfg.setAmbPosIcons(it) }
        })
        add(SItem(3, "Temporizador de sueño", "Reposo") { m -> SleepRow(m) })
        add(SItem(3, "Al terminar el temporizador", "Reposo") { m ->
            ChoiceRow("Al terminar el temporizador", SLEEP_ACTIONS, cfg.sleepAction, m) { cfg.setSleepAction(it) }
        })
        // ---- Música ----
        add(SItem(4, "Mostrar reproductor", "Reproductor") { m ->
            SwitchRow("Mostrar reproductor", cfg.showMusic, m) { cfg.setShowMusic(it) }
        })
        add(SItem(4, "Tamaño del reproductor", "Reproductor") { m ->
            SliderRow("Tamaño del reproductor", cfg.musicSize, 50, 120, 5, "%", m) { cfg.setMusicSize(it) }
        })
        add(SItem(4, "Posición del reproductor", "Reproductor") { m ->
            ChoiceRow(
                "Posición del reproductor",
                listOf("Junto al reloj", "Arriba al centro"),
                cfg.musicPos, m
            ) { cfg.setMusicPos(it) }
        })
        // ---- Apps ----
        add(SItem(5, "Editar apps", "Organizar") { m ->
            ActionRow("Editar apps (favoritas, orden, tamaño, ocultas)", m) {
                returnKey = "Editar apps"
                onEdit()
            }
        })
        add(SItem(5, "Carpetas del dock", "Carpetas") { m ->
            DialogRow("Carpetas del dock (crear, renombrar, borrar)", m) { close ->
                FoldersDialog(prefs, close)
            }
        })
        add(SItem(5, "Aviso de app nueva", "Apps nuevas") { m ->
            SwitchRow("Avisar al instalar una app nueva", cfg.newAppAlert, m) { cfg.setNewAppAlert(it) }
        })
        // ---- Sistema ----
        add(SItem(6, "Wi-Fi", "Conexiones") { m ->
            ActionRow("Wi-Fi", m) { onOpen("wifi") }
        })
        add(SItem(6, "Bluetooth", "Conexiones") { m ->
            ActionRow("Bluetooth", m) { onOpen("bt") }
        })
        add(SItem(6, "Entradas (HDMI)", "Conexiones") { m ->
            ActionRow("Entradas (HDMI)", m) {
                returnKey = "Entradas (HDMI)"
                onInputs()
            }
        })
        add(SItem(7, "Permitir nombre de red y Bluetooth", "Permisos") { m ->
            ActionRow("Permitir nombre de red y Bluetooth", m) { onNetPerm() }
        })
        add(SItem(6, "Todos los ajustes de Android", "Sistema") { m ->
            ActionRow("Todos los ajustes de Android", m) { onOpen("all") }
        })
        add(SItem(6, "Menú de energía (accesibilidad)", "Sistema") { m ->
            ActionRow("Menú de energía (accesibilidad)", m) { onOpen("access") }
        })
        add(SItem(6, "Protector de pantalla del sistema", "Sistema") { m ->
            ActionRow("Protector de pantalla del sistema", m) { onOpen("dream") }
        })
        add(SItem(6, "Establecer como inicio predeterminado", "Sistema") { m ->
            ActionRow("Establecer como inicio predeterminado", m) { onSetHome() }
        })
        add(SItem(6, "Conexión con el celular", "Celular") { m ->
            DialogRow("Conexión con el celular (fondos, texto, copia)", m) { close -> PhoneDialog(close) }
        })
        val colorNames = listOf("rojo", "verde", "amarillo", "azul")
        for (ci in 0..3) {
            add(SItem(6, "Botón " + colorNames[ci], "Botones de color") { m ->
                ChoiceRow("Botón " + colorNames[ci], COLOR_ACTIONS, cfg.colorAction(ci), m) { v ->
                    when (ci) {
                        0 -> cfg.setKeyRed(v)
                        1 -> cfg.setKeyGreen(v)
                        2 -> cfg.setKeyYellow(v)
                        else -> cfg.setKeyBlue(v)
                    }
                }
            })
        }
        add(SItem(6, "Abrir app al encender", "Arranque") { m ->
            SwitchRow("Abrir una app al encender la TV", cfg.bootEnabled, m) { on ->
                cfg.setBootEnabled(on)
                if (on && cfg.bootApp.isEmpty()) toast(ctx, "Elige también la app de arranque.")
            }
        })
        add(SItem(6, "App de arranque", "Arranque") { m ->
            val bootLabel = AppCache.apps.firstOrNull { it.pkg == cfg.bootApp }?.label
                ?: (if (cfg.bootApp.isEmpty()) "ninguna" else cfg.bootApp)
            DialogRow("App de arranque: " + bootLabel, m) { close ->
                AppPickerDialog(
                    title = "App de arranque",
                    current = cfg.bootApp,
                    onPick = {
                        cfg.setBootApp(it)
                        close()
                    },
                    onClose = close
                )
            }
        })
        add(SItem(7, "Permiso del calendario", "Permisos") { m ->
            val ok = remember(permTick) { hasCalendarPermission(ctx) }
            ActionRow("Calendario: " + (if (ok) "permitido" else "falta (se pide al activar el evento)"), m) {
                if (ok) {
                    toast(ctx, "El calendario ya está permitido.")
                } else {
                    calPerm.launch(Manifest.permission.READ_CALENDAR)
                }
            }
        })
        add(SItem(7, "Permiso de ubicación", "Permisos") { m ->
            val ok = remember(permTick) { hasLocationPermission(ctx) }
            ActionRow("Ubicación: " + (if (ok) "permitida" else "falta (se pide al activarla)"), m) {
                if (ok) {
                    toast(ctx, "La ubicación ya está permitida.")
                } else {
                    locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        })
        add(SItem(7, "Permiso de notificaciones", "Permisos") { m ->
            val ok = remember(permTick) {
                NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            }
            ActionRow("Acceso a notificaciones (música): " + (if (ok) "activo" else "falta permitir"), m) {
                tryStart(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
        })
        add(SItem(6, "App de la escena", "Escena de cine") { m ->
            val lbl = AppCache.apps.firstOrNull { it.pkg == cfg.sceneApp }?.label
                ?: (if (cfg.sceneApp.isEmpty()) "ninguna" else cfg.sceneApp)
            DialogRow("App de la escena: " + lbl, m) { close ->
                AppPickerDialog(
                    title = "App de la escena",
                    current = cfg.sceneApp,
                    onPick = {
                        cfg.setSceneApp(it)
                        close()
                    },
                    onClose = close
                )
            }
        })
        add(SItem(6, "Modo noche en la escena", "Escena de cine") { m ->
            SwitchRow("Activar modo noche", cfg.sceneNight, m) { cfg.setSceneNight(it) }
        })
        add(SItem(6, "Oscurecimiento de la escena", "Escena de cine") { m ->
            SliderRow("Oscurecer el fondo", cfg.sceneDim, 0, 80, 5, "%", m) { cfg.setSceneDim(it) }
        })
        add(SItem(6, "Temporizador de la escena", "Escena de cine") { m ->
            ChoiceRow("Temporizador de sueño", SLEEP_OPTIONS, cfg.sceneSleep, m) { cfg.setSceneSleep(it) }
        })
        add(SItem(6, "Rutina diaria", "Rutina diaria") { m ->
            SwitchRow("Rutina diaria", cfg.routineOn, m) { cfg.setRoutineOn(it) }
        })
        if (cfg.routineOn) {
            add(SItem(6, "Hora de la rutina", "Rutina diaria") { m ->
                SliderRow("Hora", cfg.routineHour, 0, 23, 1, " h", m) { cfg.setRoutineHour(it) }
            })
            add(SItem(6, "Minutos de la rutina", "Rutina diaria") { m ->
                ChoiceRow(
                    "Minutos", (0..11).map { (it * 5).toString().padStart(2, '0') },
                    (cfg.routineMin / 5).coerceIn(0, 11), m
                ) { cfg.setRoutineMin(it * 5) }
            })
            add(SItem(6, "Acción de la rutina", "Rutina diaria") { m ->
                ChoiceRow(
                    "Acción", listOf("Activar la escena de cine", "Abrir una app"),
                    cfg.routineAction, m
                ) { cfg.setRoutineAction(it) }
            })
            if (cfg.routineAction == 1) {
                add(SItem(6, "App de la rutina", "Rutina diaria") { m ->
                    val lbl = AppCache.apps.firstOrNull { it.pkg == cfg.routineApp }?.label
                        ?: (if (cfg.routineApp.isEmpty()) "ninguna" else cfg.routineApp)
                    DialogRow("App de la rutina: " + lbl, m) { close ->
                        AppPickerDialog(
                            title = "App de la rutina",
                            current = cfg.routineApp,
                            onPick = {
                                cfg.setRoutineApp(it)
                                close()
                            },
                            onClose = close
                        )
                    }
                })
            }
        }
        add(SItem(6, "Asistente de configuración", "Asistente") { m ->
            ActionRow("Abrir el asistente de configuración", m) { onWizard() }
        })
        // ---- Seguridad ----
        if (!cfg.pinSet) {
            add(SItem(6, "Estado del PIN", "Código PIN", false) { InfoRow("Estado", "Sin PIN") })
            add(SItem(8, "Crear PIN de 4 dígitos", "Código PIN") { m ->
                ActionRow("Crear PIN de 4 dígitos", m) { onPin("create") }
            })
        } else {
            add(SItem(6, "Estado del PIN", "Código PIN", false) { InfoRow("Estado", "PIN activo") })
            add(SItem(8, "Cambiar PIN", "Código PIN") { m ->
                ActionRow("Cambiar PIN", m) { onPin("change") }
            })
            add(SItem(8, "Quitar PIN", "Código PIN") { m ->
                ActionRow("Quitar PIN", m) {
                    if (cfg.kids) {
                        toast(ctx, "Desactiva antes el perfil para niños.")
                    } else {
                        onPin("remove")
                    }
                }
            })
            add(SItem(8, "Proteger ajustes", "Protección") { m ->
                SwitchRow("Proteger ajustes", cfg.lockSettings, m) { cfg.setLockSettings(it) }
            })
            add(SItem(8, "Proteger apps ocultas", "Protección") { m ->
                SwitchRow("Proteger apps ocultas", cfg.lockHidden, m) { cfg.setLockHidden(it) }
            })
        }
        add(SItem(8, "Perfil para niños", "Perfil para niños") { m ->
            SwitchRow("Perfil para niños", cfg.kids, m) { on ->
                if (on && !cfg.pinSet) {
                    toast(ctx, "Primero crea un PIN (arriba, en Código PIN) para activar el perfil para niños.")
                } else {
                    cfg.setKids(on)
                }
            }
        })
        add(SItem(8, "Apps permitidas para niños", "Perfil para niños") { m ->
            DialogRow("Apps permitidas para niños", m) { close -> KidsAppsDialog(prefs, close) }
        })
        // ---- Accesibilidad ----
        add(SItem(9, "Texto grande", "Lectura") { m ->
            SwitchRow("Texto grande", cfg.bigText, m) { cfg.setBigText(it) }
        })
        add(SItem(9, "Alto contraste", "Lectura") { m ->
            SwitchRow("Alto contraste", cfg.highContrast, m) { cfg.setHighContrast(it) }
        })
        add(SItem(9, "Foco grueso", "Navegación") { m ->
            SwitchRow("Foco más grueso en las fichas", cfg.thickFocus, m) { cfg.setThickFocus(it) }
        })
        // ---- Acerca de ----
        add(SItem(ABOUT, "Estadísticas de uso", "Uso y diagnóstico") { m ->
            DialogRow("Estadísticas de uso (esta semana)", m) { close -> StatsDialog(prefs, close) }
        })
        add(SItem(ABOUT, "Novedades", "Uso y diagnóstico") { m ->
            DialogRow("Novedades de la última versión", m) { close -> ReleaseNotesFlow(close) }
        })
        add(SItem(ABOUT, "Visor de errores", "Uso y diagnóstico") { m ->
            DialogRow("Visor de errores", m) { close -> ErrorsDialog(close) }
        })
        add(SItem(ABOUT, "Guardar copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Guardar copia de seguridad", m) { onBackup() }
        })
        add(SItem(ABOUT, "Restaurar copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Restaurar copia de seguridad", m) { onRestore() }
        })
        add(SItem(ABOUT, "Guardar copia en…", "Copias de seguridad") { m ->
            ActionRow("Guardar copia en… (elegir dónde)", m) { onBackupSave() }
        })
        add(SItem(ABOUT, "Compartir copia de seguridad", "Copias de seguridad") { m ->
            ActionRow("Compartir copia de seguridad", m) { onBackupShare() }
        })
        add(SItem(ABOUT, "Restaurar desde un archivo…", "Copias de seguridad") { m ->
            ActionRow("Restaurar desde un archivo…", m) { onRestorePick() }
        })
        // Actualizaciones desde GitHub Releases.
        add(SItem(ABOUT, "Buscar actualizaciones", "Actualizaciones") { m ->
            val fr = remember { FocusRequester() }
            LaunchedEffect(updBack) {
                if (updBack > 0) {
                    delay(200)
                    runCatching { fr.requestFocus() }
                }
            }
            ActionRow("Buscar actualizaciones", m.focusRequester(fr)) { onUpdate() }
        })
        add(SItem(ABOUT, "Avisar de actualizaciones", "Actualizaciones") { m ->
            SwitchRow("Avisar si hay versión nueva (1 vez al día)", cfg.updateCheck, m) { cfg.setUpdateCheck(it) }
        })
    }

    // Siempre hay una categoria seleccionada. Las categorias son pestañas arriba: se cambian con
    // izquierda/derecha (sin pulsar OK) y Atras cierra Configuracion de una sola vez.
    val curCat = if (cat in 0..CATS.lastIndex) cat else 0
    val secOrder = SEC_ORDER[curCat] ?: emptyList()
    val shown = items.filter { it.cat == curCat }
        .sortedBy { i -> secOrder.indexOf(i.sec).let { if (it < 0) 99 else it } }
    val title = CATS[curCat].name
    val subtitle = if (settingsPreviewCat(curCat)) "Los cambios se ven en vivo a la izquierda" else CATS[curCat].desc
    val scroll = remember(curCat) { androidx.compose.foundation.ScrollState(0) }
    val startCat = remember { curCat }
    // Mientras no se toquen las pestañas, el foco cae solo en la primera opcion (o en la fila que abrio un sub-panel).
    var tabTouched by remember { mutableStateOf(false) }
    val tabFr = remember { List(CATS.size) { FocusRequester() } }
    val firstFr = remember { FocusRequester() }
    val lastFr = remember { FocusRequester() }
    val hasReset = RESET_KEYS.containsKey(curCat) && curCat != ABOUT
    val enterOptions: () -> Boolean = { runCatching { firstFr.requestFocus() }.isSuccess }
    val retItem: String? = if (retKey != null && !tabTouched) retKey else null
    val firstFocus = !tabTouched && retKey == null && curCat == startCat
    val firstLabel = shown.firstOrNull { it.focusable }?.label
    val lastLabel = shown.lastOrNull { it.focusable }?.label
    // Abajo en la ultima opcion se queda donde esta: el foco nunca sale hacia el dock del inicio.
    val stopDown = Modifier.focusRequester(lastFr).focusProperties { down = lastFr }
    // Arriba desde la primera opcion siempre vuelve a la pestaña de la categoria abierta.
    val upMod = Modifier.focusProperties { up = tabFr[curCat] }
    DisposableEffect(Unit) { onDispose { onRightFocus(false) } }

    GlassPanelFrame(wide = true) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !blocked }
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GearIcon(Color(0x99FFFFFF), 14)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONFIGURACIÓN", color = Color(0x99FFFFFF), fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "↓ entrar  ·  Atrás cerrar", color = Color(0x80FFFFFF),
                    fontSize = 12.sp, maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CATS.forEachIndexed { i, c ->
                    TabChip(
                        c.icon, i == curCat,
                        Modifier.weight(1f).focusRequester(tabFr[i]).focusProperties {
                            up = tabFr[i]
                            if (i == 0) left = tabFr[i]
                            if (i == CATS.lastIndex) right = tabFr[i]
                        },
                        dot = (RESET_KEYS[i]?.let { prefs.countKeys(it) } ?: 0) > 0,
                        onFocus = {
                            tabTouched = true
                            if (i != curCat) onCat(i)
                        },
                        onEnter = enterOptions,
                        onClick = {
                            onCat(i)
                            enterOptions()
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                LineIcon(CATS[curCat].icon, ACC, 26)
                Spacer(Modifier.width(10.dp))
                Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Normal, maxLines = 1)
            }
            Text(
                subtitle, color = Color(0x99FFFFFF), fontSize = 14.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            Spacer(Modifier.height(2.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (curCat == ABOUT) {
                    AboutPage(
                        cfg = cfg,
                        updItems = shown.filter { it.sec == "Actualizaciones" },
                        bakItems = shown.filter { it.sec == "Copias de seguridad" },
                        toolItems = shown.filter { it.sec == "Uso y diagnóstico" },
                        firstMod = (if (firstFocus) upMod.autoFocus() else upMod).focusRequester(firstFr)
                    )
                } else {
                    for ((group, list) in shown.groupBy { it.sec }) {
                        if (group.isNotEmpty()) SectionLabel(group)
                        SectionCard {
                            for (si in list) {
                                key(si.cat to si.label) {
                                    val isFirst = si.label == firstLabel
                                    var m: Modifier = if (isFirst) upMod.focusRequester(firstFr) else Modifier
                                    if (si.focusable && si.label == retItem) {
                                        m = m.autoFocus()
                                    } else if (isFirst && firstFocus) {
                                        m = m.autoFocus()
                                    }
                                    if (!hasReset && si.label == lastLabel) m = m.then(stopDown)
                                    si.content(m)
                                    val d = DESCS[si.label]
                                    if (d != null) {
                                        Text(
                                            d, color = Color(0x80FFFFFF), fontSize = 12.sp,
                                            modifier = Modifier.padding(start = 22.dp, end = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (RESET_KEYS.containsKey(curCat)) {
                        SectionLabel(
                            "Restablecer" + (RESET_KEYS[curCat]?.let { prefs.countKeys(it) }
                                ?.takeIf { it > 0 }?.let { " · " + it + " ajustes cambiados" } ?: "")
                        )
                        SectionCard {
                            ResetCategoryRow(title, stopDown) { resetCategory(ctx, curCat) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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
    val net: List<Pair<String, String>>,
    val up: String
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

/** Tiempo desde que se encendio la TV, en texto corto. */
private fun fmtUptime(ms: Long): String {
    val mins = ms / 60000L
    val d = mins / 1440L
    val h = (mins % 1440L) / 60L
    val m = mins % 60L
    return when {
        d > 0L -> d.toString() + " d " + h.toString() + " h"
        h > 0L -> h.toString() + " h " + m.toString() + " min"
        else -> m.toString() + " min"
    }
}

/** true si One Piece es el inicio predeterminado de la TV. */
private fun isDefaultHome(ctx: Context): Boolean = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
} catch (e: Exception) {
    false
}

/** true si el texto es una fecha aaaa-mm-dd valida. */
private fun validDate(s: String): Boolean = try {
    java.time.LocalDate.parse(s.trim())
    true
} catch (e: Exception) {
    false
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
    return AboutSnap(res, dpi, hz, storage, ram, readTemp(ctx), net, fmtUptime(SystemClock.elapsedRealtime()))
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
private fun AboutPage(
    cfg: Cfg,
    updItems: List<SItem>,
    bakItems: List<SItem>,
    toolItems: List<SItem>,
    firstMod: Modifier = Modifier
) {
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
    val defaultHome = remember { isDefaultHome(ctx) }

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
        firstMod
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
            "Fotogramas por segundo" to fps.toString(),
            "Encendida desde hace" to snap.up,
            "Parche de seguridad" to (android.os.Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() } ?: "—"),
            "Launcher predeterminado" to if (defaultHome) "Sí" else "No"
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

    SectionLabel("Uso y diagnóstico")
    for (si in toolItems) key(si.label) { si.content(Modifier) }

    SectionLabel("Copias de seguridad")
    for (si in bakItems) key(si.label) { si.content(Modifier) }
}
