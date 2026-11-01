package com.mini.launcher

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    dock: List<AppInfo>,
    media: MediaInfo?,
    art: ImageBitmap?,
    onLaunch: (AppInfo) -> Unit,
    onAll: () -> Unit,
    onSettings: () -> Unit
) {
    val st = LocalDock.current
    val f = st.scalePct / 100f
    Box(Modifier.fillMaxSize()) {
        WeatherText(Modifier.align(Alignment.TopStart).padding(32.dp))
        Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (media != null) {
                MusicPill(media, art)
            }
            Clock()
            GlassBox(onClick = onSettings, modifier = Modifier.size(44.dp), shape = CircleShape) {
                Text("⚙", color = Color.White, fontSize = 20.sp)
            }
        }
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = st.bottom.dp),
            horizontalArrangement = Arrangement.spacedBy(st.spacing.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 36.dp)
        ) {
            itemsIndexed(dock) { i, app ->
                AppTile(app, if (i == 0) Modifier.autoFocus() else Modifier) { onLaunch(app) }
            }
            item {
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

@Composable
fun AllApps(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).padding(48.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            gridItems(apps) { app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(app, useSize = false) { onLaunch(app) }
                    Text(
                        app.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
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
                items(files) { f ->
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
            itemsIndexed(ordered) { i, app ->
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
