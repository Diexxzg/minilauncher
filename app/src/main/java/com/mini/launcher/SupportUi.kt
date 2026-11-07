package com.mini.launcher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.delay

private fun qrMatrix(text: String): BitMatrix? =
    try {
        val h = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0)
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, h)
    } catch (e: Exception) {
        null
    }

/** QR de ayuda: abre en el celular un reporte con los datos del equipo y el último error. */
@Composable
fun HelpQrDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val m = remember { qrMatrix(helpUrl(ctx)) }
    ExtraDialog("Ayuda y reporte de problemas", onClose, 560, scroll = true) {
        Text(
            "Escanea el código con tu celular: se abre un reporte en GitHub con la versión, el equipo y el " +
                "último error ya escritos. Ahí puedes adjuntar capturas de pantalla.",
            color = Color(0xE6FFFFFF), fontSize = 14.sp
        )
        if (m != null) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(250.dp).background(Color.White).padding(10.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val n = m.width
                    val cell = size.width / n
                    for (y in 0 until n) for (x in 0 until n) {
                        if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
                    }
                }
            }
        } else {
            Text("No se pudo generar el código QR.", color = Color(0xE6FFFFFF), fontSize = 15.sp)
        }
        ActionRow(stringResource(R.string.x_close), Modifier.autoFocus()) { onClose() }
    }
}

/** Test de pantalla: colores sólidos a pantalla completa para ver píxeles muertos o manchas. */
@Composable
fun ScreenTestDialog(onClose: () -> Unit) {
    val cols = listOf(Color.Red, Color.Green, Color.Blue, Color.White, Color.Black, Color.Gray)
    var i by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf(true) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(i) {
        hint = true
        delay(2500)
        hint = false
    }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(cols[i])
                .focusRequester(fr)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || e.nativeKeyEvent.repeatCount != 0) {
                        false
                    } else if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.DirectionRight) {
                        i = (i + 1) % cols.size
                        true
                    } else if (e.key == Key.DirectionLeft) {
                        i = (i + cols.size - 1) % cols.size
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            if (hint) {
                Text(
                    "OK o → siguiente color · ← anterior · Atrás para salir",
                    color = if (i == 3) Color.Black else Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(28.dp)
                )
            }
        }
    }
}

private fun playTone(ch: Int) {
    Thread {
        try {
            val rate = 44100
            val n = rate
            val buf = ShortArray(n * 2)
            for (k in 0 until n) {
                val fade = minOf(1.0, k / 2000.0, (n - k) / 2000.0)
                val v = (Math.sin(2 * Math.PI * 440.0 * k / rate) * 12000 * fade).toInt().toShort()
                if (ch != 2) buf[2 * k] = v
                if (ch != 1) buf[2 * k + 1] = v
            }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(buf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(buf, 0, buf.size)
            t.play()
            Thread.sleep(1200)
            t.release()
        } catch (e: Exception) {
        }
    }.start()
}

/** Test de sonido: tono de 1 s por el canal izquierdo, derecho o ambos. */
@Composable
fun SoundTestDialog(onClose: () -> Unit) {
    ExtraDialog("Test de sonido", onClose, 480) {
        Text("Sube el volumen de la TV. Suena un tono de 1 segundo.", color = Color(0xE6FFFFFF), fontSize = 14.sp)
        ActionRow("Canal izquierdo") { playTone(1) }
        ActionRow("Canal derecho") { playTone(0) }
        ActionRow("Ambos canales") { playTone(2) }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}

/** Test del mando: muestra cada tecla que se pulsa. Atrás cierra. */
@Composable
fun RemoteTestDialog(onClose: () -> Unit) {
    val hist = remember { mutableStateOf(listOf<String>()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .size(width = 460.dp, height = 380.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF0101624))
                .focusRequester(fr)
                .focusable()
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    if (code == android.view.KeyEvent.KEYCODE_BACK) {
                        false
                    } else {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                            val nm = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                            hist.value = (listOf("$nm ($code)") + hist.value).take(8)
                        }
                        true
                    }
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Test del mando", color = Color.White, fontSize = 20.sp)
            Text("Pulsa cualquier botón. Atrás para salir.", color = Color(0xB3FFFFFF), fontSize = 13.sp)
            Text(hist.value.firstOrNull() ?: "—", color = Themes.current.acc, fontSize = 28.sp)
            for (h in hist.value.drop(1)) Text(h, color = Color(0xCCFFFFFF), fontSize = 14.sp)
        }
    }
}

/** Restaurar una copia automática (o la guardada antes del modo seguro). */
@Composable
fun AutoRestoreDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val files = remember {
        val l = ArrayList<Pair<String, java.io.File>>()
        for (f in AutoBackup.list(ctx)) l.add("Copia del " + AutoBackup.label(f) to f)
        val s = Safe.safeFile(ctx)
        if (s.exists()) l.add("Antes del modo seguro (" + AutoBackup.label(s) + ")" to s)
        l
    }
    ExtraDialog("Restaurar copia automática", onClose, 520, scroll = true) {
        if (files.isEmpty()) {
            Text("Aún no hay copias automáticas. Activa la copia semanal.", color = Color(0xE6FFFFFF), fontSize = 15.sp)
        }
        for ((name, f) in files) {
            ActionRow(name) {
                val ok = AutoBackup.restore(ctx, f)
                toast(ctx, if (ok) "Copia restaurada." else "No se pudo restaurar.")
                if (ok) findActivity(ctx)?.recreate()
                onClose()
            }
        }
        ActionRow(stringResource(R.string.x_close)) { onClose() }
    }
}
