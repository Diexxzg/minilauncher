package com.mini.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Hora de la ultima notificacion (la anota MediaListener); el modo ambiente atenua la hora. */
object Notif {
    var at by mutableLongStateOf(0L)
    fun recent(): Boolean = at != 0L && SystemClock.elapsedRealtime() - at < 60_000L
}

/** Fotos del marco: imagenes de una carpeta elegida con el selector del sistema. */
object FramePhotos {
    private var list: List<Uri> = emptyList()
    private var key = ""

    fun load(ctx: Context, tree: String) {
        if (key == tree && list.isNotEmpty()) return
        key = tree
        list = try {
            val t = Uri.parse(tree)
            val ch = DocumentsContract.buildChildDocumentsUriUsingTree(t, DocumentsContract.getTreeDocumentId(t))
            val out = ArrayList<Uri>()
            ctx.contentResolver.query(
                ch, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1)?.startsWith("image/") == true) {
                        out.add(DocumentsContract.buildDocumentUriUsingTree(t, c.getString(0)))
                    }
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun bitmap(ctx: Context, i: Int): ImageBitmap? {
        if (list.isEmpty()) return null
        val u = list[Math.floorMod(i, list.size)]
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= 1920) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

private class Pal(val start: Float, val top: Color, val bottom: Color)

private val PALS = listOf(
    Pal(0f, Color(0xFF050816), Color(0xFF0B1230)),
    Pal(5f, Color(0xFF1B2A55), Color(0xFFE08A5A)),
    Pal(8f, Color(0xFF1F5F9A), Color(0xFF7FB7E6)),
    Pal(17f, Color(0xFF5A2D6B), Color(0xFFE0703F)),
    Pal(20f, Color(0xFF0A0F26), Color(0xFF1A1030)),
    Pal(24f, Color(0xFF050816), Color(0xFF0B1230))
)

/** Degradado simple que cambia cada 30 minutos (sin animacion continua). */
fun liveBgBrush(): Brush {
    val c = Calendar.getInstance()
    val t = c.get(Calendar.HOUR_OF_DAY) + (if (c.get(Calendar.MINUTE) >= 30) 0.5f else 0f)
    var i = PALS.lastIndex - 1
    for (k in 0 until PALS.lastIndex) if (t >= PALS[k].start && t < PALS[k + 1].start) { i = k; break }
    val a = PALS[i]; val b = PALS[i + 1]
    val f = ((t - a.start) / (b.start - a.start)).coerceIn(0f, 1f)
    return Brush.verticalGradient(listOf(lerp(a.top, b.top, f), lerp(a.bottom, b.bottom, f)))
}

/** Margen de seguridad guiado: marco verde = zona segura; las esquinas rojas deben verse completas. */
@Composable
fun OverscanGuide(cfg: Cfg, onClose: () -> Unit) {
    val ov = cfg.overscan
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionRight, Key.DirectionUp -> cfg.setOverscan((cfg.overscan + 2).coerceAtMost(60))
                        Key.DirectionLeft, Key.DirectionDown -> cfg.setOverscan((cfg.overscan - 2).coerceAtLeast(0))
                        else -> onClose()
                    }
                }
                true
            }
            .autoFocus()
    ) {
        val red = Color(0xFFFF5555)
        Box(Modifier.align(Alignment.TopStart).size(48.dp).border(3.dp, red))
        Box(Modifier.align(Alignment.TopEnd).size(48.dp).border(3.dp, red))
        Box(Modifier.align(Alignment.BottomStart).size(48.dp).border(3.dp, red))
        Box(Modifier.align(Alignment.BottomEnd).size(48.dp).border(3.dp, red))
        Box(Modifier.fillMaxSize().padding(ov.dp).border(2.dp, Color(0xFF3DDC84)), contentAlignment = Alignment.Center) {
            Text(
                "Margen: $ov dp\nLas 4 esquinas rojas deben verse completas.\nDerecha/Arriba = más margen · Izquierda/Abajo = menos · OK = listo",
                color = Color(0xFFCCD6E6), fontSize = 18.sp
            )
        }
    }
}
