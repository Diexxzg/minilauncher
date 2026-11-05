package com.mini.launcher

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Panel rapido (pulsacion larga en el boton de ajustes): fichas de cristal con musica, red, sonido,
 * tema, perfil, fluidez y modo ambiente, brillo del fondo, atajos y el estado de memoria.
 * Los cambios se ven al instante. Se cierra con Atras o tocando fuera.
 */
@Composable
fun QuickPanel(
    cfg: Cfg,
    profile: Int,
    onProfile: (Int) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    net: NetState = NetState(),
    media: MediaInfo? = null,
    onWifi: () -> Unit = {},
    onAmbient: () -> Unit = {},
    onWalls: () -> Unit = {},
    onInputs: () -> Unit = {},
    onSearch: () -> Unit = {},
    sceneOn: Boolean = false,
    onScene: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val os = (LocalOverscan.current / LocalUiScale.current).dp
    val shape = RoundedCornerShape(24.dp)
    val sounds = listOf("Apagado", "Suave", "Medio")
    val themes = Themes.list
    val status = remember { memoryLine(ctx) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            Modifier
                .padding(top = os + 96.dp, end = os + 32.dp)
                .width(420.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "PANEL RÁPIDO", color = Color(0x99FFFFFF), fontSize = 12.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
            if (media != null && cfg.showMusic) {
                val art = remember(media.title, media.bitmap != null) { media.bitmap?.asImageBitmap() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (art != null) {
                        Image(
                            art, null,
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(media.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(media.artist, color = Color(0x99FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SmallBtn("‹‹") { media.controller.transportControls.skipToPrevious() }
                    SmallBtn(if (media.playing) "Pausa" else "Play") {
                        if (media.playing) media.controller.transportControls.pause()
                        else media.controller.transportControls.play()
                    }
                    SmallBtn("››") { media.controller.transportControls.skipToNext() }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QTile("Wi‑Fi", net.ssid ?: (if (net.online) "Conectado" else "Sin red"), Modifier.weight(1f).autoFocus()) { onWifi() }
                QTile("Sonido", sounds[cfg.soundLevel.coerceIn(0, 2)], Modifier.weight(1f)) {
                    cfg.setSoundLevel((cfg.soundLevel + 1) % 3)
                }
                QTile("Tema", themes[cfg.theme.coerceIn(0, themes.lastIndex)].name, Modifier.weight(1f)) {
                    cfg.setTheme((cfg.theme + 1) % themes.size)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // La opcion 0 ("Personalizado") solo indica el estado; al pulsar se pasa al siguiente perfil.
                QTile("Perfil", PROFILE_OPTIONS[profile.coerceIn(0, PROFILE_OPTIONS.lastIndex)], Modifier.weight(1f)) {
                    val next = (profile % (PROFILE_OPTIONS.size - 1)) + 1
                    onProfile(next - 1)
                }
                QTile("Fluidez máxima", if (cfg.fluid) "Activada" else "Apagada", Modifier.weight(1f)) { cfg.setFluid(!cfg.fluid) }
                QTile("Reposo", "Modo ambiente", Modifier.weight(1f)) { onAmbient() }
            }
            // El brillo es lo contrario de "Oscurecer fondo": 100% = sin oscurecer.
            SliderRow("Brillo del fondo", 100 - cfg.dim, 20, 100, 5, "%") { cfg.setDim(100 - it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn(if (sceneOn) "Salir cine" else "Cine", Modifier.weight(1f)) { onScene() }
                SmallBtn("Entradas", Modifier.weight(1f)) { onInputs() }
                SmallBtn("Buscar", Modifier.weight(1f)) { onSearch() }
                SmallBtn("Ajustes", Modifier.weight(1f)) { onSettings() }
            }
            if (status.isNotEmpty()) {
                Text(
                    status, color = Color(0x80FFFFFF), fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                )
            }
        }
    }
}

/** Ficha del panel rapido: titulo pequeno y valor actual; al pulsar cambia o abre. */
@Composable
private fun QTile(title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.height(58.dp), shape = RoundedCornerShape(16.dp), cornerDp = 16.dp) {
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp)) {
            Text(title, color = Color(0x99FFFFFF), fontSize = 11.sp, maxLines = 1)
            Text(value, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "1.1 GB RAM libre · 22.6 GB almacenamiento libre" (vacio si el sistema no lo da). */
private fun memoryLine(ctx: Context): String =
    try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ram = mi.availMem / 1073741824.0
        val free = android.os.StatFs(ctx.filesDir.path).availableBytes / 1073741824.0
        String.format(java.util.Locale.getDefault(), "%.1f GB RAM libre · %.1f GB almacenamiento libre", ram, free)
    } catch (e: Exception) {
        ""
    }
