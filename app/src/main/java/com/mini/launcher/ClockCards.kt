package com.mini.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Tarjetas pequenas junto al reloj (14): evento del calendario, nota rapida y cuenta regresiva. */
@Composable
fun ClockCards(cfg: Cfg, onEditNote: () -> Unit, modifier: Modifier = Modifier) {
    if (!cfg.cardEvent && !cfg.cardNote && !cfg.cardCountdown) return
    val ctx = LocalContext.current
    val ev by produceState<String?>(null, cfg.cardEvent) {
        if (!cfg.cardEvent) {
            value = null
            return@produceState
        }
        while (true) {
            value = withContext(Dispatchers.IO) { nextEventText(ctx) }
            delay(300_000)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cfg.cardEvent) {
            InfoCard(
                "Calendario",
                ev ?: if (hasCalendarPermission(ctx)) "Sin eventos próximos" else "Falta el permiso del calendario"
            )
        }
        if (cfg.cardNote) {
            GlassBox(onClick = onEditNote, shape = RoundedCornerShape(14.dp), cornerDp = 14.dp) {
                Column(Modifier.widthIn(max = 220.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Nota", color = Color(0x99FFFFFF), fontSize = 11.sp)
                    Text(
                        cfg.noteText.ifBlank { "Pulsa OK para escribir" },
                        color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (cfg.cardCountdown) {
            InfoCard(
                "Cuenta regresiva",
                countdownText(cfg.countdownDate, cfg.countdownLabel) ?: "Elige la fecha en Configuración"
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(Color(0x66121416))
            .border(1.dp, Color(0x26FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = Color(0x99FFFFFF), fontSize = 11.sp)
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Cuadro para escribir un texto con el teclado de la TV (nota, fecha, etiqueta...).
 * Atras cierra sin guardar; "Guardar" o el boton de listo del teclado guardan.
 */
@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    hint: String = "",
    maxLen: Int = 80,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(shape)
                .background(Color(0xF0101624))
                .border(1.dp, Color(0x33FFFFFF), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = Color.White, fontSize = 20.sp)
            if (hint.isNotEmpty()) Text(hint, color = Color(0xB3FFFFFF), fontSize = 13.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= maxLen) draft = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(draft.trim()) }),
                    modifier = Modifier.fillMaxWidth().autoFocus()
                )
            }
            ActionRow("Guardar") { onSave(draft.trim()) }
            ActionRow("Borrar el texto") { onSave("") }
            ActionRow("Cancelar") { onClose() }
        }
    }
}
