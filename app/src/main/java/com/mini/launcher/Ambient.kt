package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Modo ambiente: reloj grande, fecha, clima y musica sobre el fondo. Cualquier tecla lo cierra. */
@Composable
fun AmbientScreen(cfg: Cfg, media: MediaInfo?, onExit: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    var drift by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            drift = System.currentTimeMillis() / 60_000L
            delay(10_000)
        }
    }
    val tf = remember(cfg.clock24) {
        SimpleDateFormat(if (cfg.clock24) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val df = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-VE")) }
    val shadow = Shadow(Color(0xAA000000), Offset(0f, 3f), 12f)
    // El texto se mueve un poco cada minuto para no marcar la pantalla.
    val dx = ((drift % 5L).toInt() - 2) * 10
    val dy = ((drift % 3L).toInt() - 1) * 10
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
            .clickable { onExit() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.offset(dx.dp, dy.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                tf.format(now), color = Color.White, fontSize = 110.sp,
                fontWeight = FontWeight.Light, style = TextStyle(shadow = shadow)
            )
            Text(
                df.format(now).replaceFirstChar { it.uppercase() },
                color = Color(0xE6FFFFFF), fontSize = 28.sp, style = TextStyle(shadow = shadow)
            )
            if (cfg.showWeather) {
                WeatherText(cfg.city, cfg.fahrenheit, Modifier.padding(top = 10.dp), 30.sp)
            }
            if (media != null && cfg.showMusic) {
                Text(
                    "♪  " + media.title + " — " + media.artist,
                    color = Color(0xCCFFFFFF), fontSize = 22.sp,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}
