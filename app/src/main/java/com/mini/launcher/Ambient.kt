package com.mini.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.produceState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modo ambiente: hora arriba al centro, fecha y clima debajo, y abajo el reproductor
 * con tres iconos. Cualquier tecla lo cierra.
 */
@Composable
fun AmbientScreen(cfg: Cfg, media: MediaInfo?, onExit: () -> Unit) {
    val conf = LocalConfiguration.current
    var now by remember { mutableStateOf(Date()) }
    var drift by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            drift = System.currentTimeMillis() / 60_000L
            delay(10_000)
        }
    }
    val key = cfg.city + "|" + cfg.fahrenheit
    var wline by remember(key) {
        mutableStateOf<String?>(if (WeatherCache.lineKey == key) WeatherCache.line else null)
    }
    LaunchedEffect(key, cfg.showWeather) {
        while (cfg.showWeather) {
            val r = withContext(Dispatchers.IO) { fetchWeatherLine(cfg.city, cfg.fahrenheit) }
            if (r != null) {
                wline = r
                WeatherCache.lineKey = key
                WeatherCache.line = r
            }
            delay(if (wline == null) 60_000L else 1_800_000L)
        }
    }
    val tf = remember(cfg.clock24) {
        SimpleDateFormat(if (cfg.clock24) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val af = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val df = remember(cfg.dateFmt) { SimpleDateFormat(datePattern(cfg.dateFmt), Locale.forLanguageTag("es-VE")) }
    val ampm = if (cfg.clock24) "" else " " + af.format(now).replace(Regex("[\\s\\u00A0\\u202F]"), "").lowercase()
    val timeText = tf.format(now) + ampm
    val w = wline
    val dateText = df.format(now).replaceFirstChar { it.uppercase() } +
        (if (cfg.showWeather && w != null) " - " + w else "")
    val shadow = Shadow(Color(0xAA000000), Offset(0f, 3f), 12f)
    val h = conf.screenHeightDp.toFloat()
    // Proteccion contra quemado: cada minuto el reloj y el reproductor se deslizan unos pocos
    // dp por una trayectoria lenta (ciclo de varios minutos). Solo se anima unos segundos por
    // minuto, asi que no gasta recursos. Se puede apagar en Configuracion > Inicio.
    val tmin = drift.toDouble()
    val mv = when (cfg.ex("ambMove")) { "1" -> 1.5f; "2" -> 2f; else -> 1f }
    val ampX = conf.screenWidthDp * 0.12f * mv
    val ampY = conf.screenHeightDp * 0.07f * mv
    val targetX = if (cfg.burnIn) (sin(tmin / 3.0) * ampX).toFloat() else 0f
    val targetY = if (cfg.burnIn) (cos(tmin / 4.2) * ampY).toFloat() else 0f
    val moveX by animateFloatAsState(targetX, tween(4000, easing = FastOutSlowInEasing), label = "burnX")
    val moveY by animateFloatAsState(targetY, tween(4000, easing = FastOutSlowInEasing), label = "burnY")
    val art = remember(media?.title, media?.artist, media?.bitmap != null) {
        media?.bitmap?.asImageBitmap()
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val frameOn = cfg.ex("frameOn") == "1" && cfg.ex("frameUri").isNotEmpty()
    var pidx by remember { mutableStateOf((0..999).random()) }
    LaunchedEffect(frameOn) { while (frameOn) { delay(30_000); pidx++ } }
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pidx, frameOn) {
        value = if (!frameOn) null else withContext(Dispatchers.IO) {
            FramePhotos.load(ctx, cfg.ex("frameUri")); FramePhotos.bitmap(ctx, pidx)
        }
    }
    val liveBg = cfg.ex("liveBg") == "1" && !frameOn
    val notifDim = Notif.recent() && now.time > 0L
    val timeCol = if (notifDim) Color(0x66FFFFFF) else Color(0xF2FFFFFF)
    val dimAlpha = if (cfg.ambDimOn) cfg.ambDimLvl / 100f else 0f
    val pos = intArrayOf(cfg.ambPosTime, cfg.ambPosDate, cfg.ambPosMusic, cfg.ambPosIcons)
    val gx = (conf.screenWidthDp * 0.05f).dp
    val gy = (h * 0.08f).dp
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    onExit()
                    true
                } else {
                    false
                }
            }
            .autoFocus()
            .clickable { onExit() }
    ) {
        if (liveBg) Box(Modifier.fillMaxSize().background(liveBgBrush()))
        photo?.let { ph ->
            androidx.compose.foundation.Image(ph, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        if (frameOn) Box(
            Modifier.fillMaxSize()
                .border(14.dp, Color(0xFF2B2B2B)).padding(14.dp).border(2.dp, Color(0x66000000))
        )
        // Cada elemento (hora, fecha, reproductor, iconos) va en la celda de la cuadricula 3x3 que elijas.
        for (cell in 0..8) {
            val inCell = (0..3).filter { pos[it] == cell }
            if (inCell.isEmpty()) continue
            Column(
                Modifier
                    .align(AMB_ALIGN[cell])
                    .padding(horizontal = gx, vertical = gy)
                    .graphicsLayer {
                        translationX = moveX.dp.toPx()
                        translationY = moveY.dp.toPx()
                    },
                horizontalAlignment = when (cell % 3) {
                    0 -> Alignment.Start
                    1 -> Alignment.CenterHorizontally
                    else -> Alignment.End
                },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (el in inCell) {
                    when (el) {
                        0 -> Text(
                            timeText, color = timeCol, fontSize = 40.sp,
                            fontWeight = FontWeight.Light, style = TextStyle(shadow = shadow)
                        )
                        1 -> Text(
                            dateText, color = Color(0xCCFFFFFF), fontSize = 16.sp,
                            fontWeight = FontWeight.Normal, style = TextStyle(shadow = shadow)
                        )
                        2 -> if (media != null && cfg.showMusic) {
                            ScaledPill(media, art, 95)
                        }
                        else -> Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                PowerIcon(Color(0xE6FFFFFF), 20)
                            }
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                WifiIcon(Color(0xE6FFFFFF), 20)
                            }
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                GearIcon(Color.White, 20)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Opciones de posicion en el modo ambiente (indice 9 = oculto). */
val AMB_POS_OPTIONS = listOf(
    "Arriba izquierda", "Arriba centro", "Arriba derecha",
    "Centro izquierda", "Centro", "Centro derecha",
    "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"
)

private val AMB_ALIGN = listOf(
    Alignment.TopStart, Alignment.TopCenter, Alignment.TopEnd,
    Alignment.CenterStart, Alignment.Center, Alignment.CenterEnd,
    Alignment.BottomStart, Alignment.BottomCenter, Alignment.BottomEnd
)
