package com.mini.launcher

import android.provider.Settings
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
    onSettings: () -> Unit,
    onListen: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        WeatherText(Modifier.align(Alignment.TopStart).padding(32.dp))
        Row(
            Modifier.align(Alignment.TopEnd).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Clock()
            Spacer(Modifier.width(16.dp))
            GlassBox(onClick = onSettings, modifier = Modifier.size(44.dp), shape = CircleShape) {
                Text("⚙", color = Color.White, fontSize = 20.sp)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp)
        ) {
            if (media != null) {
                MusicBar(media, art, onListen)
                Spacer(Modifier.height(12.dp))
                GlassCurrent(media, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                Spacer(Modifier.height(25.dp))
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
            ) {
                itemsIndexed(dock) { i, app ->
                    AppTile(app, if (i == 0) Modifier.autoFocus() else Modifier) { onLaunch(app) }
                }
                item {
                    GlassBox(
                        onClick = onAll,
                        modifier = (if (dock.isEmpty()) Modifier.autoFocus() else Modifier)
                            .size(144.dp, 84.dp)
                    ) {
                        Text("Todas las apps", color = Color.White, fontSize = 14.sp)
                    }
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
                    AppTile(app) { onLaunch(app) }
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
            PanelItem("Cambiar fondo de pantalla", Modifier.autoFocus()) { onWalls() }
            PanelItem("Editar apps (favoritas, orden, ocultar)") { onEdit() }
            PanelItem("Wi-Fi") { onOpen(Settings.ACTION_WIFI_SETTINGS) }
            PanelItem("Bluetooth") { onOpen(Settings.ACTION_BLUETOOTH_SETTINGS) }
            PanelItem("Sonido") { onOpen(Settings.ACTION_SOUND_SETTINGS) }
            PanelItem("Pantalla") { onOpen(Settings.ACTION_DISPLAY_SETTINGS) }
            PanelItem("Todos los ajustes de Android") { onOpen(Settings.ACTION_SETTINGS) }
            PanelItem("Cerrar") { onClose() }
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
    onFav: (String) -> Unit,
    onUp: (String) -> Unit,
    onDown: (String) -> Unit,
    onHide: (String) -> Unit
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
                    SmallBtn(if (app.pkg in hidden) "Mostrar" else "Ocultar") { onHide(app.pkg) }
                }
            }
        }
    }
}
