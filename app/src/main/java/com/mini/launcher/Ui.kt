package com.mini.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DockStyle(
    val scalePct: Int,
    val spacing: Int,
    val corner: Int,
    val bottom: Int,
    val glow: Int,
    val sizes: Map<String, Int>
)

val LocalDock = compositionLocalOf { DockStyle(100, 16, 18, 0, 2, emptyMap()) }

object TileBounds {
    var last: Rect? = null
}

fun Modifier.softGlow(color: Color, level: Int, amount: () -> Float, corner: Dp): Modifier {
    if (level <= 0) return this
    return this.drawBehind {
        val a = amount()
        if (a > 0.01f) {
            val layers = 18
            val reach = (4f + level * 3f).dp.toPx()
            val base = corner.toPx()
            val hole = Path()
            hole.addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(base, base)))
            // El brillo solo se dibuja por fuera de la tarjeta y se desvanece poco a poco.
            clipPath(hole, ClipOp.Difference) {
                val p = (0.018f + 0.006f * level) * a
                for (i in layers downTo 1) {
                    val e = reach * i / layers
                    drawRoundRect(
                        color = color.copy(alpha = p),
                        topLeft = Offset(-e, -e),
                        size = Size(size.width + 2f * e, size.height + 2f * e),
                        cornerRadius = CornerRadius(base + e, base + e)
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.autoFocus(): Modifier {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    return this.focusRequester(fr)
}

@Composable
fun GlassBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    glow: Color? = null,
    glowLevel: Int = 2,
    cornerDp: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val sc = animateFloatAsState(
        if (focused) 1.06f else 1f,
        spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "scale"
    )
    val gl = animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "glow")
    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = sc.value
                scaleY = sc.value
            }
            .then(
                if (glow != null) Modifier.softGlow(glow, glowLevel, { gl.value }, cornerDp)
                else Modifier
            )
            .clip(shape)
            .background(if (focused) Color(0x44FFFFFF) else Color(0x22FFFFFF))
            .then(
                if (glow == null) {
                    Modifier.border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) Color(0xDDFFFFFF) else Color(0x33FFFFFF),
                        shape
                    )
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun AppTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    useSize: Boolean = true,
    onClick: () -> Unit
) {
    val st = LocalDock.current
    val f = if (useSize) (st.scalePct / 100f) * ((st.sizes[app.pkg] ?: 100) / 100f) else 1f
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    GlassBox(
        onClick = {
            val c = holder[0]
            TileBounds.last = if (c != null && c.isAttached) c.boundsInRoot() else null
            onClick()
        },
        modifier = modifier
            .size((144 * f).dp, (84 * f).dp)
            .onGloballyPositioned { holder[0] = it },
        shape = RoundedCornerShape(st.corner.dp),
        glow = Color(app.glow),
        glowLevel = st.glow,
        cornerDp = st.corner.dp
    ) {
        if (app.banner) {
            Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.art, null, Modifier.size((40 * f).dp))
                Text(
                    app.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val f = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Text(
        f.format(now), color = Color(0xEBFFFFFF), fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 8f))
    )
}

@Composable
fun DateText(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000)
        }
    }
    val f = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-VE")) }
    Text(
        f.format(now).replaceFirstChar { it.uppercase() },
        color = Color(0xCCFFFFFF), fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 8f)),
        modifier = modifier
    )
}

@Composable
fun PanelItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.fillMaxWidth()) {
        Text(
            text, color = Color.White, fontSize = 16.sp,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        )
    }
}

@Composable
fun SmallBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassBox(onClick, modifier.height(40.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
