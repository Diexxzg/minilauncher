package com.mini.launcher.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta de One Piece Control (violeta sobre negro, como las capturas de referencia).
internal val BG0 = Color(0xFF07060C)
internal val CARD_TOP = Color(0xFF211F2B)
internal val CARD_BOT = Color(0xFF14131A)
internal val EDGE = Color(0xFF3A2F5C)
internal val P1 = Color(0xFF8A68F0)
internal val P2 = Color(0xFF5B3FB8)
internal val TXT = Color(0xFFF4F2FB)
internal val MUT = Color(0xFF8E8AA3)
internal val ACCL = Color(0xFFB79CFF)
internal val TEAL = Color(0xFF5EE0E8)
internal val AMBER = Color(0xFFE0B04C)
internal val RED = Color(0xFFE06C75)
internal val FIELD = Color(0xFF0E0D14)

internal val GRAD = Brush.verticalGradient(listOf(P1, P2))

/** Tarjeta oscura con borde fino violeta. Con onClick se puede tocar. */
@Composable
internal fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    border: Color = EDGE,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(CARD_TOP, CARD_BOT)))
            .border(1.dp, border, shape)
            .then(tap),
        content = content
    )
}

@Composable
internal fun IconBox(glyph: String, size: Dp = 46.dp, radius: Dp = 15.dp, fs: TextUnit = 20.sp) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size).clip(shape).background(GRAD).border(1.dp, Color(0x40FFFFFF), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = Color.White, fontSize = fs, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun Title(title: String, sub: String) {
    Column {
        Text(title, color = TXT, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = MUT, fontSize = 14.sp)
    }
}

@Composable
internal fun PrimaryBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val fill = if (enabled) Modifier.background(GRAD) else Modifier.background(Color(0xFF2A2640))
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(shape).then(fill)
            .border(1.dp, if (enabled) Color(0x50FFFFFF) else EDGE, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.White else MUT, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun GhostBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(CARD_TOP, CARD_BOT)))
            .border(1.dp, EDGE, shape).clickable(onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TXT, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val fill = if (selected) Modifier.background(GRAD) else Modifier.background(Brush.verticalGradient(listOf(CARD_TOP, CARD_BOT)))
    Box(
        modifier.height(46.dp).clip(shape).then(fill)
            .border(1.dp, if (selected) Color(0x60FFFFFF) else EDGE, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else TXT, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

/** Campo de texto oscuro con icono opcional. */
@Composable
internal fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    keyboard: KeyboardType = KeyboardType.Text
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().height(minHeight).clip(shape).background(FIELD).border(1.dp, EDGE, shape).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        if (glyph != null) {
            Text(glyph, color = ACCL, fontSize = 18.sp, modifier = Modifier.padding(top = if (singleLine) 0.dp else 8.dp))
            Spacer(Modifier.width(12.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = TXT, fontSize = 17.sp),
            cursorBrush = SolidColor(P1),
            modifier = Modifier.fillMaxWidth().padding(top = if (singleLine) 0.dp else 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(hint, color = MUT, fontSize = 17.sp)
                    inner()
                }
            }
        )
    }
}

/** Deslizador de cristal. Con interactive = false solo muestra el avance (barra de progreso). */
@Composable
internal fun GlassSlider(
    value: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onChange: (Float) -> Unit = {},
    onDone: (Float) -> Unit = {}
) {
    var w by remember { mutableStateOf(1f) }
    val cur = rememberUpdatedState(value)
    val change = rememberUpdatedState(onChange)
    val done = rememberUpdatedState(onDone)
    val touch = if (!interactive) Modifier else Modifier
        .pointerInput(Unit) {
            val pad = 12.dp.toPx()
            detectTapGestures { o ->
                val v = ((o.x - pad) / (w - 2 * pad)).coerceIn(0f, 1f)
                change.value(v)
                done.value(v)
            }
        }
        .pointerInput(Unit) {
            val pad = 12.dp.toPx()
            detectHorizontalDragGestures(
                onDragStart = { o -> change.value(((o.x - pad) / (w - 2 * pad)).coerceIn(0f, 1f)) },
                onDragEnd = { done.value(cur.value) },
                onDragCancel = { done.value(cur.value) }
            ) { ch, _ -> change.value(((ch.position.x - pad) / (w - 2 * pad)).coerceIn(0f, 1f)) }
        }
    Box(modifier.height(32.dp).onSizeChanged { w = it.width.toFloat().coerceAtLeast(40f) }.then(touch)) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 12.dp.toPx()
            val cy = size.height / 2
            val x1 = size.width - pad
            val xv = pad + (x1 - pad) * value.coerceIn(0f, 1f)
            drawLine(Color(0xFF0D0C12), Offset(pad, cy), Offset(x1, cy), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(P1, Offset(pad, cy), Offset(xv, cy), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            if (interactive || value > 0f) {
                drawCircle(Color(0xFF14131A), radius = 11.dp.toPx(), center = Offset(xv, cy))
                drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(xv, cy), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
