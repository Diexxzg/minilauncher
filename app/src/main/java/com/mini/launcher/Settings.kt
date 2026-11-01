package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/** Ajustes nuevos (inicio y fondo). Los del dock siguen en DockStyle. */
class Cfg(private val p: Prefs) {
    var clock24 by mutableStateOf(p.getInt("clock24", 0) == 1)
        private set
    var showDate by mutableStateOf(p.getInt("showDate", 1) == 1)
        private set
    var showWeather by mutableStateOf(p.getInt("showWeather", 1) == 1)
        private set
    var city by mutableStateOf(p.getStr("city", CITY))
        private set
    var fahrenheit by mutableStateOf(p.getInt("fahrenheit", 0) == 1)
        private set
    var showMusic by mutableStateOf(p.getInt("showMusic", 1) == 1)
        private set
    var dim by mutableStateOf(p.getInt("dim", 0))
        private set
    var showRecents by mutableStateOf(p.getInt("showRecents", 1) == 1)
        private set
    var rot by mutableStateOf(p.getInt("rot", 0))
        private set
    var ambient by mutableStateOf(p.getInt("ambient", 2))
        private set
    var fade by mutableStateOf(p.getInt("fade", 1) == 1)
        private set
    var sort by mutableStateOf(p.getInt("sort", 0))
        private set
    var musicSize by mutableStateOf(p.getInt("musicSize", 80))
        private set
    var musicPos by mutableStateOf(p.getInt("musicPos", 0))
        private set

    fun setMusicSize(v: Int) {
        musicSize = v
        p.putInt("musicSize", v)
    }

    fun setMusicPos(v: Int) {
        musicPos = v
        p.putInt("musicPos", v)
    }

    fun setShowRecents(v: Boolean) {
        showRecents = v
        p.putInt("showRecents", if (v) 1 else 0)
    }

    fun setRot(v: Int) {
        rot = v
        p.putInt("rot", v)
    }

    fun setAmbient(v: Int) {
        ambient = v
        p.putInt("ambient", v)
    }

    fun setFade(v: Boolean) {
        fade = v
        p.putInt("fade", if (v) 1 else 0)
    }

    fun setSort(v: Int) {
        sort = v
        p.putInt("sort", v)
    }

    fun setClock24(v: Boolean) {
        clock24 = v
        p.putInt("clock24", if (v) 1 else 0)
    }

    fun setShowDate(v: Boolean) {
        showDate = v
        p.putInt("showDate", if (v) 1 else 0)
    }

    fun setShowWeather(v: Boolean) {
        showWeather = v
        p.putInt("showWeather", if (v) 1 else 0)
    }

    fun setCity(v: String) {
        city = v
        p.putStr("city", v)
    }

    fun setFahrenheit(v: Boolean) {
        fahrenheit = v
        p.putInt("fahrenheit", if (v) 1 else 0)
    }

    fun setShowMusic(v: Boolean) {
        showMusic = v
        p.putInt("showMusic", if (v) 1 else 0)
    }

    fun setDim(v: Int) {
        dim = v
        p.putInt("dim", v)
    }
}

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
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (focused) Color(0x44FFFFFF) else Color(0x14FFFFFF))
        .border(
            if (focused) 2.dp else 1.dp,
            if (focused) Color(0xDDFFFFFF) else Color(0x22FFFFFF),
            shape
        )
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
        clickable.padding(horizontal = 16.dp, vertical = 12.dp),
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
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(220.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF))
        ) {
            val frac = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(frac)
                    .background(if (focused) Color.White else Color(0xAAFFFFFF))
            )
        }
        Text(
            value.toString() + unit, color = Color.White, fontSize = 18.sp,
            textAlign = TextAlign.End, modifier = Modifier.width(84.dp)
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
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) Color(0xFF3DDC84) else Color(0x66FFFFFF))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White))
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
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(
            (if (focused) "‹  " else "") + options[sel] + (if (focused) "  ›" else ""),
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ActionRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier, onClick = onClick) {
        Text(text, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text("›", color = Color(0xAAFFFFFF), fontSize = 20.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xCCFFFFFF), fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CategoryItem(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fm = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (focused) Color(0x55FFFFFF)
                else if (selected) Color(0x26FFFFFF)
                else Color.Transparent
            )
            .then(if (focused) Modifier.border(2.dp, Color(0xDDFFFFFF), shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { fm.moveFocus(FocusDirection.Right) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
    var cat by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(26.dp)

    // El inicio se ve detras casi sin oscurecer para ver los cambios en vivo.
    Box(Modifier.fillMaxSize().background(Color(0x22000000))) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 48.dp, end = 48.dp, top = 88.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.58f)
                .clip(shape)
                .background(Color(0xCC101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
        ) {
            Column(
                Modifier.width(250.dp).fillMaxHeight().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Configuración", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                )
                CATS.forEachIndexed { i, c ->
                    CategoryItem(
                        c.first, c.second, cat == i,
                        if (i == 0) Modifier.autoFocus() else Modifier
                    ) { cat = i }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    CATS[cat].second, color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
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
