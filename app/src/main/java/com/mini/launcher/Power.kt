package com.mini.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Servicio de accesibilidad minimo: no lee la pantalla, solo permite abrir el menu de
 * energia del sistema (reiniciar / apagar) y suspender. El usuario lo activa a mano.
 */
class PowerService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: PowerService? = null
    }
}

/** kind 1 = menu de energia del sistema, kind 2 = suspender / bloquear. */
fun systemPower(ctx: Context, kind: Int) {
    val s = PowerService.instance
    if (s == null) {
        toast(ctx, "Activa «One Piece - energía» en Accesibilidad para usar esta opción.")
        openSystem(ctx, "access")
        return
    }
    val ok = if (kind == 1) {
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    } else if (Build.VERSION.SDK_INT >= 28) {
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    } else {
        false
    }
    if (!ok) toast(ctx, "Esta TV no permite esa acción.")
}

/** Menu de energia. onPick: 0 pantalla negra, 1 menu del sistema, 2 suspender, 3 activar accesibilidad. */
@Composable
fun PowerMenu(accessOn: Boolean, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(460.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PowerIcon(Color.White, 24)
                Text("Energía", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            ActionRow("Pantalla negra", Modifier.autoFocus()) { onPick(0) }
            ActionRow("Reiniciar o apagar (menú del sistema)") { onPick(1) }
            ActionRow("Suspender / bloquear") { onPick(2) }
            Text(
                if (accessOn) "Servicio de accesibilidad: activo"
                else "Para reiniciar, apagar o suspender hay que activar «One Piece - energía» en Accesibilidad.",
                color = Color(0x99FFFFFF), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            )
            if (!accessOn) ActionRow("Activar accesibilidad") { onPick(3) }
            ActionRow("Cerrar") { onClose() }
        }
    }
}

/** Pantalla negra: baja el brillo al minimo y se cierra con cualquier tecla. */
@Composable
fun BlackScreen(onExit: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = findActivity(ctx)?.window
        if (w != null) {
            val lp = w.attributes
            lp.screenBrightness = 0.01f
            w.attributes = lp
        }
        onDispose {
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
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
    )
}
