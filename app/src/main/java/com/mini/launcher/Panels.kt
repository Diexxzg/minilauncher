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

// ---------- Editar apps ----------

private val EDIT_ACC: Color get() = Themes.current.acc

/** Boton compacto de ancho fijo: asi todas las filas quedan alineadas en columnas. */
@Composable
private fun EditChip(
    text: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    dim: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg = when {
        focused -> EDIT_ACC.copy(alpha = 0.30f)
        active -> EDIT_ACC.copy(alpha = 0.18f)
        else -> Color(0x14FFFFFF)
    }
    val base = modifier
        .width(if (wide) 76.dp else 36.dp)
        .height(34.dp)
        .alpha(if (!enabled) 0.18f else if (dim) 0.4f else 1f)
        .clip(shape)
        .background(bg)
        .then(if (focused) Modifier.border(1.dp, EDIT_ACC, shape) else Modifier)
    val full = if (enabled) {
        base.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick)
    } else {
        base
    }
    Box(full, contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (active || focused) Color.White else Color(0xCCFFFFFF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun EditSectionLabel(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, color = EDIT_ACC, fontSize = 12.sp, letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), color = Color(0x80FFFFFF), fontSize = 12.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color(0x1FFFFFFF)))
    }
}

@Composable
private fun AppEditRow(
    app: AppInfo,
    isFav: Boolean,
    isHidden: Boolean,
    pct: Int,
    canUp: Boolean,
    canDown: Boolean,
    focusFirst: Boolean,
    modifier: Modifier = Modifier,
    onFav: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onHide: () -> Unit,
    onSize: (Int) -> Unit
) {
    var within by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val sub = buildString {
        if (isFav) append("★ Favorita  ·  ")
        append(if (isHidden) "Oculta" else "Visible")
        append("  ·  ")
        append(pct)
        append("%")
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (within) Color(0x1FFFFFFF) else Color(0x0DFFFFFF))
            .then(if (within) Modifier.border(1.dp, Color(0x33FFFFFF), shape) else Modifier)
            .onFocusChanged { within = it.hasFocus }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                app.art, null, Modifier.fillMaxSize(),
                contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                app.label,
                color = if (isHidden) Color(0x80FFFFFF) else Color(0xF2FFFFFF),
                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                sub, color = Color(0x80FFFFFF), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        EditChip(
            if (isFav) "★" else "☆",
            modifier = if (focusFirst) Modifier.autoFocus() else Modifier,
            active = isFav,
            onClick = onFav
        )
        EditChip("▲", enabled = isFav, dim = !canUp, onClick = onUp)
        EditChip("▼", enabled = isFav, dim = !canDown, onClick = onDown)
        EditChip("−", onClick = { onSize((pct - 10).coerceIn(50, 200)) })
        Text(
            pct.toString() + "%", color = Color(0xE6FFFFFF), fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.width(46.dp)
        )
        EditChip("+", onClick = { onSize((pct + 10).coerceIn(50, 200)) })
        EditChip(if (isHidden) "Mostrar" else "Ocultar", wide = true, active = isHidden, onClick = onHide)
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val favApps = favs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
    val otherApps = apps.filter { it.pkg !in favs }
    // El foco inicial se fija una sola vez (si no, al subir una app al primer lugar robaba el foco).
    val firstPkg = remember { (favApps.firstOrNull() ?: otherApps.firstOrNull())?.pkg }
    val listState = rememberLazyListState()
    var movedPkg by remember { mutableStateOf<String?>(null) }
    val orderKey = favApps.map { it.pkg }

    // Despues de mover o marcar favorita, la fila se queda a la vista.
    LaunchedEffect(orderKey) {
        val pkg = movedPkg ?: return@LaunchedEffect
        delay(90)
        val fi = favApps.indexOfFirst { it.pkg == pkg }
        val idx = if (fi >= 0) {
            1 + fi
        } else {
            val oi = otherApps.indexOfFirst { it.pkg == pkg }
            if (oi < 0) return@LaunchedEffect
            (if (favApps.isEmpty()) 1 else favApps.size + 2) + oi
        }
        val vis = listState.layoutInfo.visibleItemsInfo
        val first = vis.firstOrNull()?.index ?: 0
        val last = vis.lastOrNull()?.index ?: 0
        if (idx <= first || idx >= last) listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
    }

    GlassPanelFrame(0.66f, 0.82f, 560f, 920f, false) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GearIcon(Color(0x99FFFFFF), 14)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONFIGURACIÓN", color = Color(0x99FFFFFF), fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Editar apps", color = Color.White, fontSize = 28.sp,
                    fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f)
                )
                Text(
                    favApps.size.toString() + " favoritas  ·  " +
                        apps.count { it.pkg in hidden }.toString() + " ocultas",
                    color = EDIT_ACC, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            Text(
                "Las favoritas salen primero y en este orden en la barra de abajo.",
                color = Color(0x99FFFFFF), fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp)
            ) {
                if (favApps.isNotEmpty()) {
                    item(key = "h-fav") {
                        EditSectionLabel("FAVORITAS", favApps.size, Modifier.animateItem())
                    }
                    itemsIndexed(favApps, key = { _, app -> app.pkg }) { i, app ->
                        AppEditRow(
                            app = app,
                            isFav = true,
                            isHidden = app.pkg in hidden,
                            pct = sizes[app.pkg] ?: 100,
                            canUp = i > 0,
                            canDown = i < favApps.lastIndex,
                            focusFirst = app.pkg == firstPkg,
                            modifier = Modifier.animateItem(),
                            onFav = { movedPkg = app.pkg; onFav(app.pkg) },
                            onUp = { movedPkg = app.pkg; onUp(app.pkg) },
                            onDown = { movedPkg = app.pkg; onDown(app.pkg) },
                            onHide = { onHide(app.pkg) },
                            onSize = { onSize(app.pkg, it) }
                        )
                    }
                }
                item(key = "h-all") {
                    EditSectionLabel("TODAS LAS APPS", otherApps.size, Modifier.animateItem())
                }
                itemsIndexed(otherApps, key = { _, app -> app.pkg }) { _, app ->
                    AppEditRow(
                        app = app,
                        isFav = false,
                        isHidden = app.pkg in hidden,
                        pct = sizes[app.pkg] ?: 100,
                        canUp = false,
                        canDown = false,
                        focusFirst = app.pkg == firstPkg,
                        modifier = Modifier.animateItem(),
                        onFav = { movedPkg = app.pkg; onFav(app.pkg) },
                        onUp = {},
                        onDown = {},
                        onHide = { onHide(app.pkg) },
                        onSize = { onSize(app.pkg, it) }
                    )
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
