package com.mini.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import androidx.core.app.NotificationManagerCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// Estilo Apple TV / Google TV: el elemento enfocado se queda en el centro y la fila se mueve.
@OptIn(ExperimentalFoundationApi::class)
private val CenterSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 200, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = (containerSize - size) / 2f
        return offset - target
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    dock: List<AppInfo>,
    media: MediaInfo?,
    art: ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onAll: () -> Unit,
    onSettings: () -> Unit,
    onWifi: () -> Unit,
    onInputs: () -> Unit,
    cfg: Cfg,
    recents: List<AppInfo> = emptyList(),
    onMenu: (AppInfo) -> Unit = {}
) {
    val st = LocalDock.current
    val f = st.scalePct / 100f
    val ctx = LocalContext.current
    var listenerOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(3000)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(32.dp)) {
            if (cfg.showWeather) WeatherText(cfg.city, cfg.fahrenheit)
            if (cfg.showDate) DateText(Modifier.padding(top = 2.dp))
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (media != null) {
                if (cfg.showMusic && cfg.musicPos == 0) ScaledPill(media, art, cfg.musicSize)
            } else if (!listenerOn) {
                Text(
                    "Música: falta permitir el acceso a notificaciones",
                    color = Color(0xB3FFFFFF), fontSize = 14.sp
                )
            }
            Clock(cfg.clock24)
            GlassBox(onClick = onInputs, modifier = Modifier.size(44.dp), shape = CircleShape) {
                InputIcon(Color.White)
            }
            GlassBox(onClick = onWifi, modifier = Modifier.size(44.dp), shape = CircleShape) {
                WifiIcon(Color.White)
            }
            GlassBox(onClick = onSettings, modifier = Modifier.size(44.dp), shape = CircleShape) {
                GearIcon(Color.White)
            }
        }
        if (media != null && cfg.showMusic && cfg.musicPos == 1) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 32.dp)) {
                ScaledPill(media, art, cfg.musicSize)
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides CenterSpec) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = st.bottom.dp)
        ) {
        if (cfg.showRecents && recents.isNotEmpty()) {
            Text(
                "Recientes", color = Color(0xCCFFFFFF), fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 32.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 4.dp)
            ) {
                items(recents, key = { it.pkg }) { app ->
                    AppTile(
                        app, Modifier, useSize = false, fixedScale = 0.7f,
                        onLongClick = { onMenu(app) }
                    ) { onLaunch(app) }
                }
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(st.spacing.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 36.dp)
        ) {
            itemsIndexed(dock, key = { _, app -> app.pkg }) { i, app ->
                AppTile(
                    app, if (i == 0) Modifier.autoFocus() else Modifier,
                    onLongClick = { onMenu(app) }
                ) { onLaunch(app) }
            }
            item(key = "all_apps") {
                GlassBox(
                    onClick = onAll,
                    modifier = (if (dock.isEmpty()) Modifier.autoFocus() else Modifier)
                        .size((144 * f).dp, (84 * f).dp),
                    shape = RoundedCornerShape(st.corner.dp),
                    glow = Color.White,
                    glowLevel = st.glow,
                    cornerDp = st.corner.dp
                ) {
                    Text("Todas las apps", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(if (focused) Color(0x44FFFFFF) else Color(0x14FFFFFF))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color(0xDDFFFFFF) else Color(0x22FFFFFF),
                shape
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty()) {
            Text("Buscar apps…", color = Color(0x99FFFFFF), fontSize = 18.sp)
        }
    }
}

@Composable
fun AllApps(
    apps: List<AppInfo>,
    usage: Map<String, Int>,
    recents: List<String>,
    sort: Int,
    onSort: (Int) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val list = remember(apps, query, sort, usage, recents) {
        val q = query.trim()
        val f = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
        when (sort) {
            1 -> f.sortedByDescending { usage[it.pkg] ?: 0 }
            2 -> f.sortedBy { val i = recents.indexOf(it.pkg); if (i < 0) Int.MAX_VALUE else i }
            else -> f
        }
    }
    val firstPkg = remember { list.firstOrNull()?.pkg }
    Column(
        Modifier.fillMaxSize().background(Color(0x99000000)).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            Box(Modifier.width(320.dp)) {
                ChoiceRow("Orden", listOf("A-Z", "Más usadas", "Recientes"), sort) { onSort(it) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            gridItems(list, key = { it.pkg }) { app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(
                        app,
                        if (query.isEmpty() && app.pkg == firstPkg) Modifier.autoFocus() else Modifier,
                        useSize = false,
                        onLongClick = { onMenu(app) }
                    ) { onLaunch(app) }
                    Text(
                        app.label, color = Color.White, fontSize = 14.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    onDock: () -> Unit,
    onWalls: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .padding(32.dp)
                .width(380.dp)
                .clip(shape)
                .background(Color(0x55FFFFFF))
                .border(1.dp, Color(0x55FFFFFF), shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configuración", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            PanelItem("Personalizar dock", Modifier.autoFocus()) { onDock() }
            PanelItem("Cambiar fondo de pantalla") { onWalls() }
            PanelItem("Editar apps (favoritas, orden, tamaño)") { onEdit() }
            PanelItem("Wi-Fi") { onOpen("wifi") }
            PanelItem("Todos los ajustes de Android") { onOpen("all") }
            PanelItem("Cerrar") { onClose() }
        }
    }
}

@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    firstFocus: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        SmallBtn("−", if (firstFocus) Modifier.autoFocus() else Modifier) { onMinus() }
        Text(
            value, color = Color.White, fontSize = 16.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(72.dp)
        )
        SmallBtn("+") { onPlus() }
    }
}

@Composable
fun DockSettings(style: DockStyle, onSet: (String, Int) -> Unit, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).padding(48.dp)) {
        Column(
            Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Personalizar dock", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(
                "El tamaño de cada app se cambia en Editar apps.",
                color = Color(0xCCFFFFFF), fontSize = 13.sp
            )
            Stepper(
                "Tamaño general", style.scalePct.toString() + "%",
                { onSet("scale", (style.scalePct - 10).coerceIn(50, 180)) },
                { onSet("scale", (style.scalePct + 10).coerceIn(50, 180)) },
                true
            )
            Stepper(
                "Separación", style.spacing.toString() + " dp",
                { onSet("spacing", (style.spacing - 2).coerceIn(0, 48)) },
                { onSet("spacing", (style.spacing + 2).coerceIn(0, 48)) }
            )
            Stepper(
                "Redondeo", style.corner.toString() + " dp",
                { onSet("corner", (style.corner - 2).coerceIn(0, 40)) },
                { onSet("corner", (style.corner + 2).coerceIn(0, 40)) }
            )
            Stepper(
                "Margen inferior", style.bottom.toString() + " dp",
                { onSet("bottom", (style.bottom - 2).coerceIn(0, 80)) },
                { onSet("bottom", (style.bottom + 2).coerceIn(0, 80)) }
            )
            Stepper(
                "Brillo de color", if (style.glow == 0) "Apagado" else style.glow.toString(),
                { onSet("glow", (style.glow - 1).coerceIn(0, 4)) },
                { onSet("glow", (style.glow + 1).coerceIn(0, 4)) }
            )
            SmallBtn("Restablecer todo") { onReset() }
        }
    }
}

@Composable
fun WallThumb(f: File, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, f) {
        value = withContext(Dispatchers.IO) { loadBitmap(f, 480) }
    }
    GlassBox(onClick, modifier.size(200.dp, 112.dp)) {
        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (selected) {
            Text(
                "✓", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}

@Composable
fun WallPicker(files: List<File>, current: String, onPick: (String) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).padding(48.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Fondos de pantalla", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
            ) {
                item {
                    GlassBox(onClick = onAdd, modifier = Modifier.autoFocus().size(200.dp, 112.dp)) {
                        Text("+ Agregar fondo", color = Color.White, fontSize = 16.sp)
                    }
                }
                items(files, key = { it.name }) { f ->
                    WallThumb(f, f.name == current) { onPick(f.name) }
                }
            }
            Text(
                "Elige uno con OK. Para ver la lista completa mueve el control a la derecha.",
                color = Color(0xCCFFFFFF), fontSize = 13.sp
            )
        }
    }
}

@Composable
fun EditApps(
    apps: List<AppInfo>,
    favs: List<String>,
    hidden: Set<String>,
    sizes: Map<String, Int>,
    onFav: (String) -> Unit,
    onUp: (String) -> Unit,
    onDown: (String) -> Unit,
    onHide: (String) -> Unit,
    onSize: (String, Int) -> Unit
) {
    val ordered = favs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } } +
        apps.filter { it.pkg !in favs }
    Column(
        Modifier.fillMaxSize().background(Color(0xAA000000)).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Editar apps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(
            "Las favoritas salen primero y en este orden en la barra de abajo.",
            color = Color(0xCCFFFFFF), fontSize = 13.sp
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
        ) {
            itemsIndexed(ordered, key = { _, app -> app.pkg }) { i, app ->
                val isFav = app.pkg in favs
                val pct = sizes[app.pkg] ?: 100
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(app.art, null, Modifier.size(36.dp))
                    Text(
                        app.label + if (app.pkg in hidden) "  (oculta)" else "",
                        color = Color.White, fontSize = 16.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    SmallBtn(
                        if (isFav) "★ Favorita" else "☆ Favorita",
                        if (i == 0) Modifier.autoFocus() else Modifier
                    ) { onFav(app.pkg) }
                    if (isFav) {
                        SmallBtn("▲") { onUp(app.pkg) }
                        SmallBtn("▼") { onDown(app.pkg) }
                    }
                    SmallBtn("−") { onSize(app.pkg, (pct - 10).coerceIn(50, 200)) }
                    Text(
                        pct.toString() + "%", color = Color.White, fontSize = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.width(52.dp)
                    )
                    SmallBtn("+") { onSize(app.pkg, (pct + 10).coerceIn(50, 200)) }
                    SmallBtn(if (app.pkg in hidden) "Mostrar" else "Ocultar") { onHide(app.pkg) }
                }
            }
        }
    }
}

@Composable
fun WifiIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val cx = w / 2f
        val cy = w * 0.82f
        val sw = w * 0.1f
        for (k in 1..3) {
            val r = w * 0.2f * k
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2f * r, 2f * r),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        drawCircle(color, sw * 0.8f, Offset(cx, cy))
    }
}

@Composable
fun InputIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, w * 0.16f),
            size = Size(w * 0.52f, w * 0.68f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(sw)
        )
        drawLine(color, Offset(w * 0.08f, w * 0.5f), Offset(w * 0.6f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.34f), Offset(w * 0.
