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

private val CATS = listOf(
    "palette" to "Apariencia",
    "folder" to "Dock",
    "home" to "Inicio",
    "music" to "Música",
    "device" to "Apps",
    "globe" to "Sistema",
    "lock" to "Seguridad",
    "info" to "Acerca de"
)

private val CITIES = listOf(
    "Caracas", "Maracaibo", "Valencia", "Barquisimeto", "Bogotá", "Medellín",
    "Ciudad de México", "Miami", "Madrid", "Lima", "Santiago", "Buenos Aires"
)

private val GLOW_NAMES = listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo")

// ---------- Estilo (cristal oscuro) ----------
// Para cambiar el tamaño del panel: PANEL_W = ancho (fraccion de pantalla), PANEL_H = alto.
private const val PANEL_W = 0.34f
private const val PANEL_H = 0.62f
// Escala interna del contenido del panel (menor = mas pequeño).
private const val PANEL_SCALE = 0.84f

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
/**
 * Barra de categorias: un solo elemento enfocable. Izquierda/derecha cambia de
 * categoria, abajo entra a las opciones.
 */
@Composable
fun CategoryBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionLeft -> {
                            if (selected > 0) onSelect(selected - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (selected < CATS.lastIndex) onSelect(selected + 1)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    ) {
        Row(Modifier.fillMaxWidth()) {
            CATS.forEachIndexed { i, c ->
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel && focused) Color(0x2EFFFFFF) else Color.Transparent)
                        .pointerInput(i) { detectTapGestures { onSelect(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(c.first, if (sel) ACC else TAB_IDLE, 24)
                    if (sel) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width(34.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(ACC)
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (focused) 2.dp else 1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, ACC.copy(alpha = if (focused) 0.9f else 0.55f), Color.Transparent)
                    )
                )
        )
    }
}
// ---------- Marco de cristal (compartido por Configuracion y Editar apps) ----------

/**
 * Panel de cristal centrado. Si avoidDock es true se coloca entre la barra superior y
 * el dock para no taparlo. El contenido se dibuja a PANEL_SCALE para verse mas compacto.
 */
@Composable
fun GlassPanelFrame(
    wFrac: Float,
    hFrac: Float,
    minW: Float,
    maxW: Float,
    avoidDock: Boolean,
    content: @Composable () -> Unit
) {
    val st = LocalDock.current
    val conf = LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val us = LocalUiScale.current
    val ov = LocalOverscan.current / us
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "panelIn")
    val wDp = conf.screenWidthDp.toFloat() / us - 2f * ov
    val hDp = conf.screenHeightDp.toFloat() / us - 2f * ov
    val topRes = if (avoidDock) 88f else 36f
    val botRes = if (avoidDock) 84f * st.scalePct / 100f + 72f + st.bottom + 24f else 36f
    val avail = (hDp - topRes - botRes).coerceAtLeast(240f)
    val pw = (wDp * wFrac).coerceIn(minW, maxOf(minW, maxW))
    val ph = (hDp * hFrac).coerceIn(240f, avail)
    val top = topRes + (avail - ph) / 2f
    val shape = RoundedCornerShape(22.dp)
    val panelBg = Brush.verticalGradient(listOf(Themes.current.panelTop, Themes.current.panelBottom))
    val panelBorder = Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x1AFFFFFF)))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = top.dp)
                .width(pw.dp)
                .height(ph.dp)
                .graphicsLayer {
                    translationY = (1f - p) * 24.dp.toPx()
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
// ---------- Pantalla ----------

@Composable
fun SettingsScreen(
    style: DockStyle,
    cfg: Cfg,
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
    onPin: (String) -> Unit
) {
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    var cat by remember { mutableIntStateOf(0) }
    GlassPanelFrame(PANEL_W, PANEL_H, 360f, 500f, true) {
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp)) {
                CategoryBar(cat, Modifier.autoFocus()) { cat = it }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GearIcon(Color(0x99FFFFFF), 14)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CONFIGURACIÓN",
                        color = Color(0x99FFFFFF), fontSize = 13.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Text(
                    CATS[cat].second, color = Color.White, fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                when (cat) {
                    0 -> {
                        ActionRow("Cambiar fondo de pantalla") { onWalls() }
                        ChoiceRow("Tema de color", Themes.list.map { it.name }, cfg.theme) { cfg.setTheme(it) }
                        SwitchRow("Desenfoque al abrir paneles", cfg.blur) { cfg.setBlur(it) }
                        SliderRow("Oscurecer fondo", cfg.dim, 0, 80, 5, "%") { cfg.setDim(it) }
                        ChoiceRow(
                            "Rotación de fondos",
                            listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"),
                            cfg.rot
                        ) { cfg.setRot(it) }
                        SwitchRow("Transición suave", cfg.fade) { cfg.setFade(it) }
                        ChoiceRow(
                            "Fondo según app enfocada",
                            listOf("Apagado", "Degradado", "Banner"),
                            cfg.dynBg
                        ) { cfg.setDynBg(it) }
                        SwitchRow("Animar cambio de pantallas", cfg.screenAnim) { cfg.setScreenAnim(it) }
                        ChoiceRow("Brillo de color", GLOW_NAMES, style.glow) { onSet("glow", it) }
                        ChoiceRow(
                            "Color del brillo",
                            listOf("Color de cada app", "Color del fondo", "Color del tema"),
                            cfg.glowSrc
                        ) { cfg.setGlowSrc(it) }
                        SliderRow("Tamaño de la interfaz", cfg.uiScale, 70, 130, 5, "%") { cfg.setUiScale(it) }
                        SliderRow("Margen de seguridad", cfg.overscan, 0, 60, 2, " dp") { cfg.setOverscan(it) }
                        ChoiceRow(
                            "Sonido al mover el foco",
                            listOf("Apagado", "Suave", "Medio"),
                            cfg.soundLevel
                        ) { cfg.setSoundLevel(it) }
                    }
                    1 -> {
                        SliderRow("Tamaño general", style.scalePct, 50, 180, 10, "%") { onSet("scale", it) }
                        SliderRow("Separación", style.spacing, 0, 48, 2, " dp") { onSet("spacing", it) }
                        SliderRow("Redondeo", style.corner, 0, 40, 2, " dp") { onSet("corner", it) }
                        SliderRow("Margen inferior", style.bottom, 0, 80, 2, " dp") { onSet("bottom", it) }
                        ChoiceRow("Fila extra 1", ROW_NAMES, cfg.row(0)) { cfg.setRow(0, it) }
                        ChoiceRow("Fila extra 2", ROW_NAMES, cfg.row(1)) { cfg.setRow(1, it) }
                        ChoiceRow("Fila extra 3", ROW_NAMES, cfg.row(2)) { cfg.setRow(2, it) }
                        InfoRow("Asignar apps", "mantén OK en la app")
                        ActionRow("Restablecer dock") { onResetDock() }
                    }
                    2 -> {
                        SwitchRow("Reloj grande", cfg.bigClock) { cfg.setBigClock(it) }
                        ChoiceRow("Formato de hora", listOf("12 horas", "24 horas"), if (cfg.clock24) 1 else 0) {
                            cfg.setClock24(it == 1)
                        }
                        SwitchRow("Mostrar fecha", cfg.showDate) { cfg.setShowDate(it) }
                        SwitchRow("Mostrar clima", cfg.showWeather) { cfg.setShowWeather(it) }
                        val cityOpts = if (cfg.city in CITIES) CITIES else listOf(cfg.city) + CITIES
                        ChoiceRow("Ciudad del clima", cityOpts, cityOpts.indexOf(cfg.city)) {
                            cfg.setCity(cityOpts[it])
                        }
                        ChoiceRow("Unidades", listOf("°C", "°F"), if (cfg.fahrenheit) 1 else 0) {
                            cfg.setFahrenheit(it == 1)
                        }
                        SwitchRow("Mostrar recientes", cfg.showRecents) { cfg.setShowRecents(it) }
                        ChoiceRow(
                            "Botón de voz",
                            listOf("Asistente de voz", "Buscar apps"),
                            cfg.voiceMode
                        ) { cfg.setVoiceMode(it) }
                        ChoiceRow(
                            "Modo ambiente",
                            listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"),
                            cfg.ambient
                        ) { cfg.setAmbient(it) }
                    }
                    3 -> {
                        SwitchRow("Mostrar reproductor", cfg.showMusic) { cfg.setShowMusic(it) }
                        SliderRow("Tamaño del reproductor", cfg.musicSize, 50, 120, 5, "%") { cfg.setMusicSize(it) }
                        ChoiceRow(
                            "Posición del reproductor",
                            listOf("Junto al reloj", "Arriba al centro"),
                            cfg.musicPos
                        ) { cfg.setMusicPos(it) }
                        ActionRow("Permitir acceso a notificaciones") {
                            tryStart(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        }
                    }
                    4 -> {
                        ActionRow("Editar apps (favoritas, orden, tamaño, ocultas)") { onEdit() }
                    }
                    5 -> {
                        ActionRow("Wi-Fi") { onOpen("wifi") }
                        ActionRow("Entradas (HDMI)") { onInputs() }
                        ActionRow("Todos los ajustes de Android") { onOpen("all") }
                        ActionRow("Bluetooth") { onOpen("bt") }
                        ActionRow("Permitir nombre de red y Bluetooth") { onNetPerm() }
                        ActionRow("Menú de energía (accesibilidad)") { onOpen("access") }
                        ActionRow("Protector de pantalla del sistema") { onOpen("dream") }
                        ActionRow("Establecer como inicio predeterminado") { onSetHome() }
                    }
                    6 -> {
                        if (!cfg.pinSet) {
                            ActionRow("Crear PIN de 4 dígitos") { onPin("create") }
                            InfoRow("Estado", "Sin PIN")
                        } else {
                            ActionRow("Cambiar PIN") { onPin("change") }
                            ActionRow("Quitar PIN") { onPin("remove") }
                            SwitchRow("Proteger ajustes", cfg.lockSettings) { cfg.setLockSettings(it) }
                            SwitchRow("Proteger apps ocultas", cfg.lockHidden) { cfg.setLockHidden(it) }
                            InfoRow("Estado", "PIN activo")
                        }
                    }
                    else -> {
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
                        ActionRow("Guardar copia de seguridad") { onBackup() }
                        ActionRow("Restaurar copia de seguridad") { onRestore() }
                    }
                }
                }
            }
        }
}
