package com.mini.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.draw.alpha
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
    Box(Modifier.fillMaxSize().padding(48.dp)) {
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
fun GhostBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(34.dp)
            .widthIn(min = 34.dp)
            .clip(shape)
            .background(if (focused) Color(0x2BFFFFFF) else Color(0x14FFFFFF))
            .then(if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (focused) Color.White else Color(0xCCFFFFFF), fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

// ---------- Editar apps (panel lateral derecho) ----------
//
// Dos vistas, pensadas para control remoto:
//  - "Todas las apps": lista fija en orden alfabetico. Nada se mueve de lugar al marcar una
//    favorita, ocultar o cambiar el tamano, asi el foco se queda en la misma app.
//  - "Orden del dock": solo las favoritas, en el orden del dock. OK (o mantener OK) toma la app,
//    las flechas la mueven y OK la suelta.

private val EDIT_ACC: Color get() = Themes.current.acc

/** Tamanos que recorre el boton de tamano de cada app (OK pasa al siguiente). */
private val SIZE_STEPS = listOf(60, 80, 100, 120, 150, 200)

private fun nextSize(cur: Int): Int = SIZE_STEPS.firstOrNull { it > cur } ?: SIZE_STEPS.first()

/** Pestana de la parte de arriba (Todas las apps / Orden del dock). */
@Composable
private fun EditTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color(0x40FFFFFF)
                    selected -> EDIT_ACC.copy(alpha = 0.25f)
                    else -> Color(0x14FFFFFF)
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color(0xDDFFFFFF)
                    selected -> EDIT_ACC
                    else -> Color(0x22FFFFFF)
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected || focused) Color.White else Color(0xB3FFFFFF),
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** Boton de una fila: favorita, ocultar o tamano. on = esta activado. */
@Composable
private fun EditChip(
    text: String,
    on: Boolean,
    minWidth: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(36.dp)
            .widthIn(min = minWidth.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color(0x40FFFFFF)
                    on -> EDIT_ACC.copy(alpha = 0.22f)
                    else -> Color(0x14FFFFFF)
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> Color(0xDDFFFFFF)
                    on -> EDIT_ACC.copy(alpha = 0.7f)
                    else -> Color(0x22FFFFFF)
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                focused -> Color.White
                on -> EDIT_ACC
                else -> Color(0xCCFFFFFF)
            },
            fontSize = 14.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun AppThumb(app: AppInfo) {
    Box(
        Modifier
            .width(56.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            app.art, null, Modifier.fillMaxSize(),
            contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit
        )
    }
}

/**
 * Fila de "Todas las apps": icono, nombre y tres botones (favorita, ocultar, tamano).
 * Izquierda/derecha cambia de boton; arriba/abajo cambia de app sin salirse de la columna.
 */
@Composable
private fun AppRowAll(
    app: AppInfo,
    favPos: Int,
    isHidden: Boolean,
    pct: Int,
    focusFirst: Boolean,
    onFav: () -> Unit,
    onHide: () -> Unit,
    onSize: (Int) -> Unit
) {
    val isFav = favPos > 0
    val shape = RoundedCornerShape(12.dp)
    val sub = when {
        isHidden -> "Oculta (no sale en el dock)"
        isFav -> "★ Favorita · puesto " + favPos.toString()
        else -> "No es favorita"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFav) EDIT_ACC.copy(alpha = 0.10f) else Color.Transparent)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppThumb(app)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                app.label,
                color = if (isHidden) Color(0x80FFFFFF) else Color(0xF2FFFFFF),
                fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                sub,
                color = if (isFav && !isHidden) EDIT_ACC else Color(0x80FFFFFF),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        EditChip(
            if (isFav) "★ " + favPos.toString() else "☆",
            isFav, 54,
            if (focusFirst) Modifier.autoFocus() else Modifier
        ) { onFav() }
        Spacer(Modifier.width(6.dp))
        EditChip(if (isHidden) "Oculta" else "Ocultar", isHidden, 72) { onHide() }
        Spacer(Modifier.width(6.dp))
        EditChip(pct.toString() + "%", pct != 100, 56) { onSize(nextSize(pct)) }
    }
}

/**
 * Fila de "Orden del dock". Con la app tomada (grabbed), arriba/abajo la mueven y el foco se
 * queda en ella; OK la suelta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockOrderRow(
    app: AppInfo,
    pos: Int,
    isHidden: Boolean,
    grabbed: Boolean,
    modifier: Modifier = Modifier,
    onGrab: () -> Unit,
    onDrop: () -> Unit,
    onMove: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    grabbed -> EDIT_ACC.copy(alpha = 0.30f)
                    focused -> Color(0x2BFFFFFF)
                    else -> Color(0x0DFFFFFF)
                }
            )
            .then(
                if (grabbed) Modifier.border(2.dp, EDIT_ACC, shape)
                else if (focused) Modifier.border(1.dp, Color(0x66FFFFFF), shape)
                else Modifier
            )
            .onPreviewKeyEvent { e ->
                if (!grabbed) {
                    false
                } else if (e.key == Key.DirectionUp || e.key == Key.DirectionDown) {
                    if (e.type == KeyEventType.KeyDown) onMove(if (e.key == Key.DirectionUp) -1 else 1)
                    true
                } else if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) {
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                onClick = { if (grabbed) onDrop() else onGrab() },
                onLongClick = { if (!grabbed) onGrab() }
            )
            .padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            pos.toString(),
            color = if (grabbed) Color.White else EDIT_ACC,
            fontSize = 18.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.width(30.dp)
        )
        AppThumb(app)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(
                app.label,
                color = if (isHidden) Color(0x80FFFFFF) else Color(0xF2FFFFFF),
                fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    grabbed -> "▲ ▼ mueven la app · OK la suelta"
                    isHidden -> "Oculta (no sale en el dock)"
                    focused -> "OK para tomarla y moverla"
                    else -> " "
                },
                color = if (grabbed) Color.White else Color(0x80FFFFFF),
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            if (grabbed) "↕" else "≡",
            color = if (grabbed || focused) EDIT_ACC else Color(0x80FFFFFF),
            fontSize = 22.sp
        )
    }
}

@Composable
fun EditApps(
    apps: List<AppInfo>,
    favs: List<String>,
    hidden: Set<String>,
    sizes: Map<String, Int>,
    onFav: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onHide: (String) -> Unit,
    onSize: (String, Int) -> Unit
) {
    // Orden alfabetico fijo: no cambia al marcar favoritas, ocultar o cambiar tamanos.
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val favApps = remember(apps, favs) { favs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } } }
    val posOf = remember(favApps) { favApps.withIndex().associate { it.value.pkg to (it.index + 1) } }
    val hiddenCount = apps.count { it.pkg in hidden }
    // El foco inicial se fija una sola vez, para que nada le robe el foco despues.
    val firstPkg = remember { sorted.firstOrNull()?.pkg }

    var tab by remember { mutableIntStateOf(0) }
    var grabbed by remember { mutableStateOf<String?>(null) }
    val allState = rememberLazyListState()
    val orderState = rememberLazyListState()
    val orderKey = favApps.map { it.pkg }

    // Atras con una app tomada: la suelta (no cierra el panel).
    BackHandler(enabled = grabbed != null) { grabbed = null }

    // Mientras se mueve una app, se mantiene a la vista.
    LaunchedEffect(orderKey) {
        val pkg = grabbed ?: return@LaunchedEffect
        delay(60)
        val idx = favApps.indexOfFirst { it.pkg == pkg }
        val vis = orderState.layoutInfo.visibleItemsInfo
        if (idx < 0 || vis.isEmpty()) return@LaunchedEffect
        if (idx <= vis.first().index || idx >= vis.last().index) {
            orderState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    fun moveFav(pkg: String, d: Int) {
        val order = favApps.map { it.pkg }
        // Las favoritas de apps que ya no estan instaladas se conservan al final.
        val extra = favs.filter { f -> f !in order }
        onReorder(move(order, pkg, d) + extra)
    }

    GlassPanelFrame {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GearIcon(Color(0x99FFFFFF), 14)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONFIGURACIÓN", color = Color(0x99FFFFFF), fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal
                )
                Text(
                    "   ›   APPS", color = EDIT_ACC, fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal, maxLines = 1
                )
            }
            Text(
                "Editar apps", color = Color.White, fontSize = 28.sp,
                fontWeight = FontWeight.Normal, maxLines = 1,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Text(
                apps.size.toString() + " apps  ·  " + favApps.size.toString() + " favoritas  ·  " +
                    hiddenCount.toString() + " ocultas",
                color = EDIT_ACC, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Row(
                Modifier.padding(start = 2.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditTab("Todas las apps", tab == 0) {
                    tab = 0
                    grabbed = null
                }
                EditTab("Orden del dock", tab == 1) {
                    tab = 1
                    grabbed = null
                }
            }
            Text(
                if (tab == 0) {
                    "OK en ★ marca o quita favorita · OK en Ocultar · OK en el % cambia el tamaño."
                } else {
                    "OK (o mantener OK) toma una app, ▲ ▼ la mueven y OK la suelta."
                },
                color = Color(0x80FFFFFF), fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp, end = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))

            if (tab == 0) {
                LazyColumn(
                    state = allState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp)
                ) {
                    items(sorted, key = { it.pkg }) { app ->
                        AppRowAll(
                            app = app,
                            favPos = posOf[app.pkg] ?: 0,
                            isHidden = app.pkg in hidden,
                            pct = sizes[app.pkg] ?: 100,
                            focusFirst = app.pkg == firstPkg,
                            onFav = { onFav(app.pkg) },
                            onHide = { onHide(app.pkg) },
                            onSize = { onSize(app.pkg, it) }
                        )
                    }
                }
            } else if (favApps.isEmpty()) {
                Text(
                    "Todavía no hay favoritas. Márcalas con ★ en la pestaña \"Todas las apps\".",
                    color = Color(0xCCFFFFFF), fontSize = 15.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 16.dp, end = 10.dp)
                )
            } else {
                LazyColumn(
                    state = orderState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    itemsIndexed(favApps, key = { _, a -> a.pkg }) { i, app ->
                        DockOrderRow(
                            app = app,
                            pos = i + 1,
                            isHidden = app.pkg in hidden,
                            grabbed = grabbed == app.pkg,
                            modifier = Modifier.animateItem(),
                            onGrab = { grabbed = app.pkg },
                            onDrop = { grabbed = null },
                            onMove = { d -> moveFav(app.pkg, d) }
                        )
                    }
                }
            }
        }
    }
}

class InputItem(val id: String, val label: String)
fun listInputs(ctx: Context): List<InputItem> {
    return try {
        val tm = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tm.tvInputList
            .filter { it.isPassthroughInput }
            .map {
                InputItem(
                    it.id,
                    (it.loadCustomLabel(ctx) ?: it.loadLabel(ctx)).toString()
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

fun openInput(ctx: Context, id: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (e: Exception) {
    }
}

@Composable
fun InputsPanel(items: List<InputItem>, onPick: (String) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
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
            Text("Entradas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            if (items.isEmpty()) {
                Text(
                    "No se encontraron entradas de TV (HDMI).",
                    color = Color(0xCCFFFFFF), fontSize = 14.sp
                )
                PanelItem("Cerrar", Modifier.autoFocus()) { onClose() }
            } else {
                items.forEachIndexed { i, it ->
                    PanelItem(it.label, if (i == 0) Modifier.autoFocus() else Modifier) { onPick(it.id) }
                }
                PanelItem("Cerrar") { onClose() }
            }
        }
    }
}
