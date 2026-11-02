package com.mini.launcher

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Una peticion de PIN: titulo y que hacer cuando se completan los 4 digitos. */
class PinAsk(val title: String, val onCode: (String) -> Unit)

fun hashPin(pin: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val b = md.digest(("onepiece:" + pin).toByteArray())
    return b.joinToString("") { "%02x".format(it) }
}

private fun digitOf(k: Key): String? = when (k) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

/**
 * Teclado numerico para el control remoto. Tambien acepta los numeros del control
 * (teclas 0-9) y la tecla de borrar.
 */
@Composable
fun PinPad(ask: PinAsk, error: String?, onCancel: () -> Unit) {
    var digits by remember(ask) { mutableStateOf("") }
    fun push(d: String) {
        if (digits.length >= 4) return
        val nd = digits + d
        if (nd.length == 4) {
            digits = ""
            ask.onCode(nd)
        } else {
            digits = nd
        }
    }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Borrar", "0", "Cancelar")
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    val d = digitOf(e.key)
                    if (d != null) {
                        push(d)
                        true
                    } else if (e.key == Key.Backspace) {
                        digits = digits.dropLast(1)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(ask.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (i in 0 until 4) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < digits.length) Color.White else Color(0x44FFFFFF))
                    )
                }
            }
            Text(
                error ?: " ", color = Color(0xFFFF8A8A), fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            for (r in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0 until 3) {
                        val k = keys[r * 3 + c]
                        GlassBox(
                            onClick = {
                                when (k) {
                                    "Borrar" -> digits = digits.dropLast(1)
                                    "Cancelar" -> onCancel()
                                    else -> push(k)
                                }
                            },
                            modifier = (if (k == "5") Modifier.autoFocus() else Modifier)
                                .size(92.dp, 56.dp)
                        ) {
                            Text(
                                k, color = Color.White,
                                fontSize = if (k.length == 1) 22.sp else 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
