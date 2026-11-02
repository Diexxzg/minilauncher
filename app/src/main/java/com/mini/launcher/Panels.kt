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
    GlassPanelFrame(0.62f, 0.8f, 520f, 860f, false) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GearIcon(Color(0x99FFFFFF), 14)
                Spacer(Modifier.width(8.dp))
                Text(
                    "CONFIGURACIÓN", color = Color(0x99FFFFFF), fontSize = 13.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Normal
                )
            }
            Text(
                "Editar apps", color = Color.White, fontSize = 28.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Text(
                "Las favoritas salen primero y en este orden en la barra de abajo.",
                color = Color(0x99FFFFFF), fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(ordered, key = { _, app -> app.pkg }) { i, app ->
                    val isFav = app.pkg in favs
                    val pct = sizes[app.pkg] ?: 100
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(app.art, null, Modifier.size(32.dp))
                        Text(
                            app.label + if (app.pkg in hidden) "  (oculta)" else "",
                            color = Color(0xF2FFFFFF), fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        GhostBtn(
                            if (isFav) "★ Favorita" else "☆ Favorita",
                            if (i == 0) Modifier.autoFocus() else Modifier
                        ) { onFav(app.pkg) }
                        if (isFav) {
                            GhostBtn("▲") { onUp(app.pkg) }
                            GhostBtn("▼") { onDown(app.pkg) }
                        }
                        GhostBtn("−") { onSize(app.pkg, (pct - 10).coerceIn(50, 200)) }
                        Text(
                            pct.toString() + "%", color = Color(0xE6FFFFFF), fontSize = 14.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.width(46.dp)
                        )
                        GhostBtn("+") { onSize(app.pkg, (pct + 10).coerceIn(50, 200)) }
                        GhostBtn(if (app.pkg in hidden) "Mostrar" else "Ocultar") { onHide(app.pkg) }
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
