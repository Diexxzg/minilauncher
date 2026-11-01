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
fun WifiIcon(color: Color, s: Int = 22) {
    Canvas(Modifier.size(s.dp)) {
        val w = size.width
        val cx = w / 2f
        val cy = w * 0.82f
        val sw = w * 0.1f
        for (k in 1..3) {
            val r = w * 0.2f * k
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2f * r, 2f * r),
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        drawCircle(color, sw * 0.8f, Offset(cx, cy))
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
