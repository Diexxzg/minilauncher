package com.mini.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale

/**
 * Fondo que cambia con la app enfocada (estilo Apple TV / Google TV).
 * mode: 0 = apagado, 1 = solo degradado con el color de la app, 2 = banner de la app + degradado.
 * Se dibuja sobre el fondo de pantalla; al cambiar de app se funde suavemente.
 */
@Composable
fun FocusBackdrop(app: AppInfo?, mode: Int, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = if (mode > 0) app else null,
        modifier = modifier.fillMaxSize(),
        animationSpec = tween(650),
        label = "focusBackdrop"
    ) { a ->
        if (a != null) {
            val c = Color(a.glow)
            Box(Modifier.fillMaxSize()) {
                if (mode == 2 && a.banner) {
                    // El banner es pequeno: al estirarlo a pantalla completa queda suave y difuminado.
                    Image(
                        bitmap = a.art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                c.copy(alpha = 0.32f)
                            )
                        )
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(c.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                )
            }
        }
    }
}
