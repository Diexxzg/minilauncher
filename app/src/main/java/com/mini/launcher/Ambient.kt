package com.mini.launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
    val ampX = conf.screenWidthDp * 0.12f
    val ampY = conf.screenHeightDp * 0.07f
    val targetX = if (cfg.burnIn) (sin(tmin / 3.0) * ampX).toFloat() else 0f
    val targetY = if (cfg.burnIn) (cos(tmin / 4.2) * ampY).toFloat() else 0f
    val moveX by animateFloatAsState(targetX, tween(4000, easing = FastOutSlowInEasing), label = "burnX")
    val moveY by animateFloatAsState(targetY, tween(4000, easing = FastOutSlowInEasing), label = "burnY")
    val art = remember(media?.title, media?.artist, media?.bitmap != null) {
        media?.bitmap?.asImageBitmap()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
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
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = (h * 0.15f).dp)
                .graphicsLayer {
                    translationX = moveX.dp.toPx()
                    translationY = moveY.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                timeText, color = Color(0xF2FFFFFF), fontSize = 40.sp,
                fontWeight = FontWeight.Light, style = TextStyle(shadow = shadow)
            )
            Text(
                dateText, color = Color(0xCCFFFFFF), fontSize = 16.sp,
                fontWeight = FontWeight.Normal, style = TextStyle(shadow = shadow)
            )
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = (h * 0.58f).dp)
                .graphicsLayer {
                    translationX = moveX.dp.toPx()
                    translationY = moveY.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (media != null && cfg.showMusic) {
                ScaledPill(media, art, 95)
            }
            Row(
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
