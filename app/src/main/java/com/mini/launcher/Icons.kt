package com.mini.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import androidx.core.app.NotificationManagerCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WifiIcon(color: Color, s: Int = 22, level: Int = 3, off: Boolean = false) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val cx = w / 2f
        val cy = w * 0.82f
        val sw = w * 0.1f
        for (k in 1..3) {
            val r = w * 0.2f * k
            val c = if (k <= level && !off) color else color.copy(alpha = color.alpha * 0.3f)
            drawArc(
                color = c,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2f * r, 2f * r),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        drawCircle(if (off) color.copy(alpha = color.alpha * 0.3f) else color, sw * 0.8f, Offset(cx, cy))
        if (off) {
            drawLine(
                color, Offset(w * 0.12f, w * 0.12f), Offset(w * 0.88f, w * 0.88f),
                strokeWidth = sw * 0.9f, cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun EthernetIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.08f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, w * 0.1f),
            size = Size(w * 0.28f, w * 0.24f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            style = st
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, w * 0.66f),
            size = Size(w * 0.28f, w * 0.24f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            style = st
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.66f, w * 0.66f),
            size = Size(w * 0.28f, w * 0.24f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
            style = st
        )
        drawLine(color, Offset(w * 0.5f, w * 0.34f), Offset(w * 0.5f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, w * 0.5f), Offset(w * 0.8f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, w * 0.5f), Offset(w * 0.2f, w * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, w * 0.5f), Offset(w * 0.8f, w * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun BluetoothIcon(color: Color, s: Int = 18) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val p = Path()
        p.moveTo(w * 0.28f, w * 0.32f)
        p.lineTo(w * 0.72f, w * 0.68f)
        p.lineTo(w * 0.5f, w * 0.88f)
        p.lineTo(w * 0.5f, w * 0.12f)
        p.lineTo(w * 0.72f, w * 0.32f)
        p.lineTo(w * 0.28f, w * 0.68f)
        drawPath(p, color, style = Stroke(w * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun MicIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, w * 0.08f),
            size = Size(w * 0.28f, w * 0.46f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(sw)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, w * 0.3f),
            size = Size(w * 0.52f, w * 0.46f),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.5f, w * 0.76f), Offset(w * 0.5f, w * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun InputIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.09f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, w * 0.16f),
            size = Size(w * 0.52f, w * 0.68f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(sw)
        )
        drawLine(color, Offset(w * 0.08f, w * 0.5f), Offset(w * 0.6f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.34f), Offset(w * 0.6f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, w * 0.66f), Offset(w * 0.6f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun GearIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val c = Offset(w / 2f, w / 2f)
        drawCircle(color, radius = w * 0.2f, center = c, style = Stroke(w * 0.1f))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color,
                Offset(c.x + dx * w * 0.32f, c.y + dy * w * 0.32f),
                Offset(c.x + dx * w * 0.46f, c.y + dy * w * 0.46f),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Round
            )
        }
    }
}


/** Iconos de linea fina para las pestanas del panel de configuracion. */
@Composable
fun LineIcon(kind: String, color: Color, s: Int = 24) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.075f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            "palette" -> {
                drawCircle(color, radius = w * 0.4f, center = Offset(w * 0.5f, w * 0.5f), style = st)
                drawCircle(color, radius = sw * 0.7f, center = Offset(w * 0.34f, w * 0.42f))
                drawCircle(color, radius = sw * 0.7f, center = Offset(w * 0.52f, w * 0.3f))
                drawCircle(color, radius = sw * 0.7f, center = Offset(w * 0.69f, w * 0.44f))
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.56f, w * 0.68f), style = st)
            }
            "folder" -> {
                val p = Path()
                p.moveTo(w * 0.1f, w * 0.26f)
                p.lineTo(w * 0.38f, w * 0.26f)
                p.lineTo(w * 0.47f, w * 0.38f)
                p.lineTo(w * 0.9f, w * 0.38f)
                p.lineTo(w * 0.9f, w * 0.8f)
                p.lineTo(w * 0.1f, w * 0.8f)
                p.close()
                drawPath(p, color, style = st)
            }
            "home" -> {
                val roof = Path()
                roof.moveTo(w * 0.1f, w * 0.48f)
                roof.lineTo(w * 0.5f, w * 0.14f)
                roof.lineTo(w * 0.9f, w * 0.48f)
                drawPath(roof, color, style = st)
                val body = Path()
                body.moveTo(w * 0.2f, w * 0.42f)
                body.lineTo(w * 0.2f, w * 0.84f)
                body.lineTo(w * 0.8f, w * 0.84f)
                body.lineTo(w * 0.8f, w * 0.42f)
                drawPath(body, color, style = st)
                val door = Path()
                door.moveTo(w * 0.42f, w * 0.84f)
                door.lineTo(w * 0.42f, w * 0.62f)
                door.lineTo(w * 0.58f, w * 0.62f)
                door.lineTo(w * 0.58f, w * 0.84f)
                drawPath(door, color, style = st)
            }
            "music" -> {
                drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.38f, w * 0.74f), style = st)
                drawLine(color, Offset(w * 0.51f, w * 0.74f), Offset(w * 0.51f, w * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.51f, w * 0.18f), Offset(w * 0.8f, w * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "device" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.3f, w * 0.1f),
                    size = Size(w * 0.4f, w * 0.8f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = st
                )
                drawLine(color, Offset(w * 0.44f, w * 0.78f), Offset(w * 0.56f, w * 0.78f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "globe" -> {
                drawCircle(color, radius = w * 0.4f, center = Offset(w * 0.5f, w * 0.5f), style = st)
                drawOval(color, topLeft = Offset(w * 0.3f, w * 0.1f), size = Size(w * 0.4f, w * 0.8f), style = st)
                drawLine(color, Offset(w * 0.1f, w * 0.5f), Offset(w * 0.9f, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.22f, w * 0.44f),
                    size = Size(w * 0.56f, w * 0.4f),
                    cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
                    style = st
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.32f, w * 0.14f),
                    size = Size(w * 0.36f, w * 0.6f),
                    style = st
                )
            }
            else -> {
                drawCircle(color, radius = w * 0.4f, center = Offset(w * 0.5f, w * 0.5f), style = st)
                drawCircle(color, radius = sw * 0.7f, center = Offset(w * 0.5f, w * 0.31f))
                drawLine(color, Offset(w * 0.5f, w * 0.45f), Offset(w * 0.5f, w * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}


@Composable
fun PowerIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val sw = w * 0.09f
        drawArc(
            color = color,
            startAngle = -50f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, w * 0.18f),
            size = Size(w * 0.68f, w * 0.68f),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w * 0.5f, w * 0.1f), Offset(w * 0.5f, w * 0.48f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
