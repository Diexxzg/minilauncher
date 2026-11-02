package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Accesos rapidos: panel pequeno que se abre con una pulsacion larga en el boton de ajustes.
 * Brillo del fondo, tema y sonido sin entrar a Configuracion. Los cambios se ven al instante.
 * Se cierra con Atras o tocando fuera.
 */
@Composable
fun QuickPanel(
    cfg: Cfg,
    profile: Int,
    onProfile: (Int) -> Unit,
    onSettings: () -> Unit,
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
                .width(400.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "ACCESOS RÁPIDOS", color = Color(0x99FFFFFF), fontSize = 12.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 4.dp)
            )
            // El brillo es lo contrario de "Oscurecer fondo": 100% = sin oscurecer.
            SliderRow("Brillo del fondo", 100 - cfg.dim, 20, 100, 5, "%", Modifier.autoFocus()) {
                cfg.setDim(100 - it)
            }
            // Perfil de diseno: la primera opcion ("Personalizado") solo indica el estado actual.
            ChoiceRow("Perfil de diseño", PROFILE_OPTIONS, profile) {
                onProfile(if (it > 0) it - 1 else 0)
            }
            ChoiceRow("Tema", Themes.list.map { it.name }, cfg.theme) { cfg.setTheme(it) }
            ChoiceRow("Sonido", listOf("Apagado", "Suave", "Medio"), cfg.soundLevel) {
                cfg.setSoundLevel(it)
            }
            ActionRow("Abrir configuración") { onSettings() }
        }
    }
}
