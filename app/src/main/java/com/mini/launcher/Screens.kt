package com.mini.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.platform.LocalConfiguration
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    dock: List<AppInfo>,
    media: MediaInfo?,
    art: ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onAll: () -> Unit,
    onSettings: () -> Unit,
    onWifi: () -> Unit,
    cfg: Cfg,
    recents: List<AppInfo> = emptyList(),
    onMenu: (AppInfo) -> Unit = {},
    rows: List<Pair<String, List<AppInfo>>> = emptyList(),
    net: NetState = NetState(),
    onMore: () -> Unit = {},
    moreBack: Int = 0,
    onFocusApp: (AppInfo, Boolean) -> Unit = { _, _ -> },
    dockState: LazyListState? = null,
    focusTick: Int = 0,
    loading: Boolean = false,
    previewDock: Boolean = false,
    onQuick: () -> Unit = {},
    quickBack: Int = 0,
    focusedApp: AppInfo? = null,
    lastUsed: Map<String, Long> = emptyMap(),
    /** true mientras un panel lateral (Configuracion / Editar apps) ocupa la derecha. */
    sidePanel: Boolean = false
) {
    val st = LocalDock.current
    val f = st.scalePct / 100f
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    val us = LocalUiScale.current
    val accent = LocalAccent.current
    val ownState = rememberLazyListState()
    val dState = dockState ?: ownState
    var listenerOn by remember { mutableStateOf(true) }
    // Al cerrar el panel "Mas" el foco vuelve al boton de tres puntos.
    val moreFr = remember { FocusRequester() }
    var seenMore by remember { mutableIntStateOf(moreBack) }
    LaunchedEffect(moreBack) {
        if (moreBack != seenMore) {
            seenMore = moreBack
            delay(150)
            runCatching { moreFr.requestFocus() }
        }
    }
    // Al cerrar los accesos rapidos el foco vuelve al boton de ajustes.
    val gearFr = remember { FocusRequester() }
    // Destino de "Abajo" desde la barra superior: la primera app del dock (o "Todas las apps").
    val dockFr = remember { FocusRequester() }
    var seenQuick by remember { mutableIntStateOf(quickBack) }
    LaunchedEffect(quickBack) {
        if (quickBack != seenQuick) {
            seenQuick = quickBack
            delay(150)
            runCatching { gearFr.requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
            delay(3000)
        }
    }
    // Alto maximo de las filas para que nunca tapen la barra superior.
    val maxH = (conf.screenHeightDp / us * 0.64f).dp
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopStart).padding(32.dp)) {
            if (cfg.bigClock) {
                // Reloj grande, con la fecha y el clima debajo.
                Clock(cfg.clock24, size = 68.sp, ampmSize = 24.sp)
                if (cfg.showDate) DateText(Modifier.padding(top = 2.dp), size = 20.sp)
                if (cfg.showWeather) {
                    WeatherText(cfg.city, cfg.fahrenheit, Modifier.padding(top = 4.dp), size = 18.sp)
                }
            } else {
                if (cfg.showWeather) WeatherText(cfg.city, cfg.fahrenheit)
                if (cfg.showDate) DateText(Modifier.padding(top = 2.dp))
            }
        }
        if (!sidePanel) Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (media != null) {
                if (cfg.showMusic && cfg.musicPos == 0) {
                    Box(Modifier.focusProperties { down = dockFr }) {
                        ScaledPill(media, art, cfg.musicSize)
                    }
                }
            } else if (!listenerOn) {
                Text(
                    "Música: falta permitir el acceso a notificaciones",
                    color = Color(0xB3FFFFFF), fontSize = 14.sp
                )
            }
            if (!cfg.bigClock) Clock(cfg.clock24)
            NetPill(net, onWifi, Modifier.focusProperties { down = dockFr })
            GlassBox(
                onClick = onMore,
                modifier = Modifier.size(44.dp).focusRequester(moreFr).focusProperties { down = dockFr },
                shape = CircleShape
            ) {
                MoreIcon(Color.White)
            }
            // Pulsacion larga: accesos rapidos (brillo del fondo, tema y sonido).
            GlassBox(
                onClick = onSettings,
                onLongClick = onQuick,
                modifier = Modifier.size(44.dp).focusRequester(gearFr).focusProperties { down = dockFr },
                shape = CircleShape
            ) {
                GearIcon(Color.White)
            }
        }
        if (media != null && cfg.showMusic && cfg.musicPos == 1 && !sidePanel) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .focusProperties { down = dockFr }
            ) {
                ScaledPill(media, art, cfg.musicSize)
            }
        }
        // De abajo hacia arriba: dock, recientes y las filas extra (Streaming, Juegos...).
        LazyColumn(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxH)
                .padding(bottom = st.bottom.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(top = 8.dp)
        ) {
            if (cfg.focusCard) {
                // Primer elemento de la columna invertida = lo mas bajo: texto bajo el dock.
                item(key = "focusCard") {
                    val fa = focusedApp
                    FocusCard(
                        fa,
                        if (fa != null) (LocalBadges.current.unread[fa.pkg] ?: 0) else 0,
                        if (fa != null) lastUsed[fa.pkg] else null,
                        Modifier.offset(y = (-14).dp)
                    )
                }
            }
            item(key = "dock") {
                CompositionLocalProvider(LocalBringIntoViewSpec provides CenterSpec) {
                    LazyRow(
                        state = dState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(st.spacing.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 36.dp)
                    ) {
                        itemsIndexed(dock, key = { _, app -> app.pkg }) { i, app ->
                            AppTile(
                                app, if (i == 0) Modifier.autoFocusOn(focusTick, dockFr) else Modifier,
                                onLongClick = { onMenu(app) },
                                onFocusChange = { onFocusApp(app, it) },
                                preview = previewDock && i == 0
                            ) { onLaunch(app) }
                        }
                        if (loading && dock.isEmpty()) {
                            // Esqueletos mientras se leen las apps instaladas.
                            items(5, key = { "skeleton$it" }) { SkeletonTile(f, st.corner) }
                        }
                        item(key = "all_apps") {
                            GlassBox(
                                onClick = onAll,
                                modifier = (if (dock.isEmpty()) Modifier.autoFocusOn(focusTick, dockFr) else Modifier)
                                    .size((144 * f).dp, (84 * f).dp),
                                shape = RoundedCornerShape(st.corner.dp),
                                glow = accent ?: Color.White,
                                glowLevel = st.glow,
                                cornerDp = st.corner.dp
                            ) {
                                Text("Todas las apps", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            if (cfg.showRecents && recents.isNotEmpty()) {
                item(key = "recents") {
                    Column {
                        Text(
                            "Recientes", color = Color(0xCCFFFFFF), fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                        CompositionLocalProvider(LocalBringIntoViewSpec provides CenterSpec) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 4.dp)
                            ) {
                                items(recents, key = { it.pkg }) { app ->
                                    AppTile(
                                        app, Modifier, useSize = false, fixedScale = 0.85f, bannerStyle = true,
                                        onLongClick = { onMenu(app) },
                                        onFocusChange = { onFocusApp(app, it) }
                                    ) { onLaunch(app) }
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(rows, key = { i, _ -> "row" + i }) { _, r ->
                Column {
                    Text(
                        r.first, color = Color(0xCCFFFFFF), fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 32.dp, top = 6.dp)
                    )
                    CompositionLocalProvider(LocalBringIntoViewSpec provides CenterSpec) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)
                        ) {
                            items(r.second, key = { it.pkg }) { app ->
                                AppTile(
                                    app, Modifier, useSize = false, fixedScale = 0.8f,
                                    onLongClick = { onMenu(app) },
                                    onFocusChange = { onFocusApp(app, it) }
                                ) { onLaunch(app) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusIt: Boolean = false,
    placeholder: String = "Buscar apps…"
) {
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
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusIt) Modifier.autoFocus() else Modifier)
                .onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty()) {
            Text(placeholder, color = Color(0x99FFFFFF), fontSize = 18.sp, maxLines = 1)
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
    focusSearch: Boolean = false,
    onHidden: (() -> Unit)? = null,
    title: String? = null,
    loading: Boolean = false,
    onLaunch: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableIntStateOf(0) }
    val pm = LocalContext.current.packageManager
    // Categoria de cada app (Juegos, Streaming, Musica, Herramientas, Otras).
    val catOf = remember(apps) { apps.associate { it.pkg to appCategory(pm, it.pkg) } }
    val catCount = remember(catOf) {
        IntArray(APP_CATS.size).also { a -> catOf.values.forEach { a[it]++ } }
    }
    // Solo se ofrecen las categorias que tienen apps; si hay una sola, no se muestran filtros.
    val shownCats = remember(catCount) { (1 until APP_CATS.size).filter { catCount[it] > 0 } }
    val catSel = if (cat in shownCats) cat else 0
    val list = remember(apps, query, sort, usage, recents, catSel, catOf) {
        val q = query.trim()
        val base = if (catSel == 0) apps else apps.filter { catOf[it.pkg] == catSel }
        val f = if (q.isEmpty()) base else base.filter { it.label.contains(q, ignoreCase = true) }
        when (sort) {
            1 -> f.sortedByDescending { usage[it.pkg] ?: 0 }
            2 -> f.sortedBy { val i = recents.indexOf(it.pkg); if (i < 0) Int.MAX_VALUE else i }
            else -> f
        }
    }
    val firstPkg = remember { list.firstOrNull()?.pkg }
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title != null) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SearchField(query, { query = it }, Modifier.weight(1f), focusIt = focusSearch)
            if (onHidden != null) {
                SmallBtn("Ocultas") { onHidden() }
            }
            Box(Modifier.width(320.dp)) {
                ChoiceRow("Orden", listOf("A-Z", "Más usadas", "Recientes"), sort) { onSort(it) }
            }
        }
        if (shownCats.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                item(key = "cat0") {
                    CatChip("Todas (" + apps.size + ")", catSel == 0) { cat = 0 }
                }
                items(shownCats, key = { "cat$it" }) { c ->
                    CatChip(APP_CATS[c] + " (" + catCount[c] + ")", catSel == c) { cat = c }
                }
            }
        }
        if (loading && apps.isEmpty()) {
            // Esqueletos mientras se leen las apps instaladas.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(8.dp),
                userScrollEnabled = false
            ) {
                items(12, key = { "sk$it" }) { _ ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonBox(Modifier.size(144.dp, 84.dp), 18.dp)
                        SkeletonBox(Modifier.padding(top = 8.dp).size(90.dp, 12.dp), 6.dp)
                    }
                }
            }
        } else if (list.isEmpty()) {
            Text(
                "Sin resultados", color = Color(0x99FFFFFF), fontSize = 16.sp,
                modifier = Modifier.padding(8.dp)
            )
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
                        if (!focusSearch && query.isEmpty() && app.pkg == firstPkg) Modifier.autoFocus() else Modifier,
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

/** Boton de filtro de categoria: la seleccionada se marca con el color del tema. */
@Composable
fun CatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    GlassBox(onClick, Modifier.height(38.dp), shape = RoundedCornerShape(19.dp)) {
        Text(
            label,
            color = if (selected) Themes.current.acc else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
