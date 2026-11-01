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
    "🎨" to "Apariencia",
    "🗂️" to "Dock",
    "🏠" to "Inicio",
    "🎵" to "Música",
    "📱" to "Apps",
    "🌐" to "Sistema",
    "ℹ️" to "Acerca de"
)

private val CITIES = listOf(
    "Caracas", "Maracaibo", "Valencia", "Barquisimeto", "Bogotá", "Medellín",
    "Ciudad de México", "Miami", "Madrid", "Lima", "Santiago", "Buenos Aires"
)

private val GLOW_NAMES = listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo")

// ---------- Estilo (sakura + neon) ----------
// Para cambiar el tamaño del panel: PANEL_W = ancho (fraccion de pantalla), PANEL_H = alto.
private const val PANEL_W = 0.40f
private const val PANEL_H = 0.72f

private val PINK = Color(0xFFFF8FB8)
private val VIOLET = Color(0xFFB69CFF)
private val CYAN = Color(0xFF7AE3FF)
private val ACCENT: Brush = Brush.horizontalGradient(listOf(PINK, VIOLET))
private val IDLE_BORDER: Brush = SolidColor(Color(0x1FFFFFFF))

// ---------- Controles ----------

@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    onKey: (Int) -> Boolean = { false },
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val bg: Brush = if (focused) {
        Brush.horizontalGradient(listOf(Color(0x55FF8FB8), Color(0x33B69CFF)))
    } else {
        SolidColor(Color(0x14FFFFFF))
    }
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(bg)
        .border(if (focused) 1.5.dp else 1.dp, if (focused) ACCENT else IDLE_BORDER, shape)
        .drawBehind {
            if (focused) {
                drawRoundRect(
                    color = PINK,
                    topLeft = Offset(6.dp.toPx(), size.height * 0.25f),
                    size = Size(3.dp.toPx(), size.height * 0.5f),
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
        clickable.padding(start = 20.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
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
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(110.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x33FFFFFF))
        ) {
            val frac = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .alpha(if (focused) 1f else 0.75f)
                    .background(ACCENT)
            )
        }
        Text(
            value.toString() + unit, color = Color.White, fontSize = 15.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(66.dp)
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
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (checked) ACCENT else SolidColor(Color(0x55FFFFFF)))
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
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            (if (focused) "‹  " else "") + options[sel] + (if (focused) "  ›" else ""),
            color = if (focused) Color(0xFFFFD9E8) else Color(0xDDFFFFFF),
            fontSize = 15.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ActionRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier, onClick = onClick) { focused ->
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text("›", color = if (focused) PINK else Color(0xAAFFFFFF), fontSize = 20.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xB3FFFFFF), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Barra de categorias: un solo elemento enfocable. Izquierda/derecha cambia de
 * categoria, abajo entra a las opciones.
 */
@Composable
fun CategoryBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) ACCENT else IDLE_BORDER, shape)
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
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CATS.forEachIndexed { i, c ->
            val sel = i == selected
            val chipBg: Brush = if (sel) {
                Brush.horizontalGradient(listOf(Color(0x77FF8FB8), Color(0x66B69CFF)))
            } else {
                SolidColor(Color.Transparent)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chipBg)
                    .pointerInput(i) { detectTapGestures { onSelect(i) } },
                contentAlignment = Alignment.Center
            ) {
                Text(c.first, fontSize = 18.sp, modifier = Modifier.alpha(if (sel) 1f else 0.5f))
            }
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
    onRestore: () -> Unit
) {
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    var cat by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(380), label = "settingsIn")

    val shape = RoundedCornerShape(28.dp)
    val pw = (conf.screenWidthDp * PANEL_W).coerceIn(400f, 580f).dp
    val ph = (conf.screenHeightDp * PANEL_H).dp
    val panelBg = Brush.linearGradient(listOf(Color(0xF2171229), Color(0xF20E1527)))
    val panelBorder = Brush.linearGradient(
        listOf(PINK.copy(alpha = 0.75f), Color(0x22FFFFFF), CYAN.copy(alpha = 0.6f))
    )
    val rule = Brush.horizontalGradient(
        listOf(Color.Transparent, PINK.copy(alpha = 0.6f), VIOLET.copy(alpha = 0.6f), Color.Transparent)
    )

    // El inicio se ve a la izquierda casi sin oscurecer para ver los cambios en vivo.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x55000000))))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 32.dp)
                .width(pw)
                .height(ph)
                .graphicsLayer {
                    translationX = (1f - p) * 140.dp.toPx()
                    alpha = p
                }
                .clip(shape)
                .background(panelBg)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0x33FF8FB8), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.9f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0x2A7AE3FF), Color.Transparent),
                            center = Offset(0f, size.height),
                            radius = size.width * 0.8f
                        )
                    )
                }
                .border(1.5.dp, panelBorder, shape)
        ) {
            // Marca de agua de flor de sakura.
            Text(
                "✿",
                color = Color.White.copy(alpha = 0.05f),
                fontSize = 150.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 18.dp, y = 28.dp)
            )
            Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "✿  CONFIGURACIÓN",
                            color = PINK, fontSize = 11.sp, letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            CATS[cat].second, color = Color.White, fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text("✦", color = VIOLET.copy(alpha = 0.9f), fontSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                CategoryBar(cat, Modifier.autoFocus()) { cat = it }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                when (cat) {
                    0 -> {
                        ActionRow("Cambiar fondo de pantalla") { onWalls() }
                        SliderRow("Oscurecer fondo", cfg.dim, 0, 80, 5, "%") { cfg.setDim(it) }
                        ChoiceRow(
                            "Rotación de fondos",
                            listOf("Desactivada", "Cada 30 min", "Cada hora", "Según la hora"),
                            cfg.rot
                        ) { cfg.setRot(it) }
                        SwitchRow("Transición suave", cfg.fade) { cfg.setFade(it) }
                        ChoiceRow("Brillo de color", GLOW_NAMES, style.glow) { onSet("glow", it) }
                    }
                    1 -> {
                        SliderRow("Tamaño general", style.scalePct, 50, 180, 10, "%") { onSet("scale", it) }
                        SliderRow("Separación", style.spacing, 0, 48, 2, " dp") { onSet("spacing", it) }
                        SliderRow("Redondeo", style.corner, 0, 40, 2, " dp") { onSet("corner", it) }
                        SliderRow("Margen inferior", style.bottom, 0, 80, 2, " dp") { onSet("bottom", it) }
                        ActionRow("Restablecer dock") { onResetDock() }
                    }
                    2 -> {
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
                        ActionRow("Establecer como inicio predeterminado") { onSetHome() }
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
                            
