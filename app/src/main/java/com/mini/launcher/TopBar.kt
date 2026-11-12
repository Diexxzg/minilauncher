package com.mini.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tres puntos horizontales: boton "Mas" de la barra superior. */
@Composable
fun MoreIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val r = w * 0.085f
        drawCircle(color = color, radius = r, center = Offset(w * 0.2f, w * 0.5f))
        drawCircle(color = color, radius = r, center = Offset(w * 0.5f, w * 0.5f))
        drawCircle(color = color, radius = r, center = Offset(w * 0.8f, w * 0.5f))
    }
}

@Composable
private fun MoreRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    GlassBox(onClick, modifier.fillMaxWidth().height(52.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Text(text, color = Color.White, fontSize = 16.sp)
        }
    }
}

/**
 * Panel pequeno bajo la barra superior con lo menos usado: entradas y energia.
 * Se cierra con Atras o tocando fuera.
 */
@Composable
fun MoreMenu(
    onInputs: () -> Unit,
    onPower: () -> Unit,
    onClose: () -> Unit
) {
    val os = (LocalOverscan.current / LocalUiScale.current).dp
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            Modifier
                .padding(top = os + 96.dp, end = os + 32.dp)
                .width(300.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MoreRow("Entradas", Modifier.autoFocus(), { InputIcon(Color.White) }, onInputs)
            MoreRow("Energía", Modifier, { PowerIcon(Color.White) }, onPower)
        }
    }
}
