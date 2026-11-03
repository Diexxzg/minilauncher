package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Cantidad maxima de carpetas del dock. */
const val MAX_FOLDERS = 3

/** Ficha de una carpeta en el dock: nombre y cantidad de apps. */
@Composable
fun FolderTile(name: String, apps: List<AppInfo>, f: Float, tileStyle: Int, onClick: () -> Unit) {
    val st = LocalDock.current
    val dims = tileDims(tileStyle)
    val round = tileStyle == 3
    GlassBox(
        onClick = onClick,
        modifier = Modifier.size((dims.first * f).dp, (dims.second * f).dp),
        shape = if (round) CircleShape else RoundedCornerShape(st.corner.dp),
        glow = LocalAccent.current ?: Color(0xFFFFD27A),
        glowLevel = st.glow,
        cornerDp = if (round) (dims.second * f / 2f).dp else st.corner.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📁", fontSize = (22 * f).sp)
            if (tileStyle < 2) {
                Text(
                    name, color = Color.White, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp)
                )
                Text(apps.size.toString() + " apps", color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
        }
    }
}

/** Contenido de una carpeta: cuadricula de apps. Atras cierra. Mantener OK abre el menu de la app. */
@Composable
fun FolderDialog(
    name: String,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .heightIn(max = 460.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("📁 " + name, color = Color.White, fontSize = 20.sp)
            if (apps.isEmpty()) {
                Text(
                    "Carpeta vacía. Mantén OK sobre una app y elige \"En la carpeta $name\".",
                    color = Color(0xB3FFFFFF), fontSize = 14.sp
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppTile(
                            app,
                            if (app.pkg == apps.first().pkg) Modifier.autoFocus() else Modifier,
                            useSize = false,
                            onLongClick = { onMenu(app) }
                        ) { onLaunch(app) }
                        Text(
                            app.label, color = Color.White, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
