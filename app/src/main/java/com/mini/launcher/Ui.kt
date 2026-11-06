package com.mini.launcher

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.View
import android.view.SoundEffectConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

/** Escala global de la interfaz (1f = 100%). */
val LocalUiScale = compositionLocalOf { 1f }

/** Margen de seguridad (overscan) en dp, medido sin escala. */
val LocalOverscan = compositionLocalOf { 0 }

/** Color de acento tomado del fondo; null = usar el color de cada app. */
val LocalAccent = compositionLocalOf<Color?> { null }

/** Sonido suave al mover el foco. */
val LocalSounds = compositionLocalOf { false }

/** Volumen del sonido de navegacion (0..1). */
val LocalSoundVol = compositionLocalOf { 0.3f }

/** Estilo de foco de las fichas: 0 clasico, 1 aro grueso, 2 solo brillo, 3 solo escala. */
val LocalFocusStyle = compositionLocalOf { 0 }

/** Accesibilidad (33): aro de foco grueso y alto contraste. */
val LocalThickFocus = compositionLocalOf { false }
val LocalHighContrast = compositionLocalOf { false }

/** Sonido corto al abrir (true) o cerrar (false) un panel (26). Usa los sonidos del sistema. */
fun playPanelSound(view: View, vol: Float, open: Boolean) {
    val am = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (am != null) {
        am.playSoundEffect(
            if (open) AudioManager.FX_KEYPRESS_STANDARD else AudioManager.FX_KEYPRESS_RETURN, vol
        )
    }
}

/** Reproduce el sonido de foco a volumen bajo (si el sistema tiene los sonidos activados). */
fun playSoftFocus(view: View, vol: Float) {
    val am = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (am != null) {
        am.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, vol)
    } else {
        view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
    }
}

/** Insignias: apps nuevas y notificaciones sin leer por paquete. */
class Badges(val newPkgs: Set<String>, val unread: Map<String, Int>)

val LocalBadges = compositionLocalOf { Badges(emptySet(), emptyMap()) }

object TileBounds {
    var last: Rect? = null
}

fun Modifier.softGlow(color: Color, level: Int, amount: () -> Float, corner: Dp): Modifier {
    if (level <= 0) return this
    return this.drawBehind {
        val a = amount()
        if (a > 0.01f) {
            // 5 anillos finos hacia afuera, sin recorte: mucho mas barato que 18 capas con clipPath.
            val layers = 5
            val reach = (4f + level * 3f).dp.toPx()
            val base = corner.toPx()
            val step = reach / layers
            val peak = (0.22f + 0.06f * level) * a
            for (i in 0 until layers) {
                val e = step * (i + 0.5f)
                val k = 1f - i.toFloat() / layers
                drawRoundRect(
                    color = color.copy(alpha = peak * k * k),
                    topLeft = Offset(-e, -e),
                    size = Size(size.width + 2f * e, size.height + 2f * e),
                    cornerRadius = CornerRadius(base + e, base + e),
                    style = Stroke(width = step + 0.5f)
                )
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

/** Igual que autoFocus, pero vuelve a pedir el foco cada vez que cambia key. */
@Composable
fun Modifier.autoFocusOn(key: Any?, requester: FocusRequester? = null): Modifier {
    val own = remember { FocusRequester() }
    val fr = requester ?: own
    LaunchedEffect(key) {
        delay(150)
        runCatching { fr.requestFocus() }
    }
    return this.focusRequester(fr)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    glow: Color? = null,
    glowLevel: Int = 2,
    cornerDp: Dp = 18.dp,
    onFocusChange: ((Boolean) -> Unit)? = null,
    preview: Boolean = false,
    extraScale: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // preview: se ve como enfocada aunque no lo este (vista previa en vivo en Configuracion).
    val lit = focused || preview
    val view = LocalView.current
    val sounds = LocalSounds.current
    val vol = LocalSoundVol.current
    val born = remember { SystemClock.elapsedRealtime() }
    val th = Themes.current
    // Estilo de foco: solo afecta a las fichas con brillo (glow != null).
    val thick = LocalThickFocus.current
    val hc = LocalHighContrast.current
    val fs = if (glow != null) (if (thick) 1 else LocalFocusStyle.current) else 0
    // Sin rebote ni pulso: todo se mueve con curvas suaves y sin parpadeo.
    val sc = animateFloatAsState(
        if (lit) (when (fs) { 2 -> 1f; 3 -> 1.09f; else -> 1.05f }) + extraScale else 1f,
        tween(220, easing = FastOutSlowInEasing),
        label = "scale"
    )
    val lift = animateFloatAsState(
        if (lit && fs != 2) 1f else 0f,
        tween(220, easing = FastOutSlowInEasing),
        label = "lift"
    )
    val gl = animateFloatAsState(if (lit) 1f else 0f, tween(260), label = "glow")
    val bgColor by animateColorAsState(
        if (hc) (if (lit) Color(0xFF2A2A2A) else Color(0xE6000000))
        else if (lit) th.focusBg else Color(0x66121416), tween(200), label = "bg"
    )
    val borderColor by animateColorAsState(
        if (hc) (if (lit) Color.White else Color(0xAAFFFFFF))
        else if (lit) th.focusBorder else Color(0x26FFFFFF), tween(200), label = "border"
    )
    val borderW by animateDpAsState(if (lit) (if (fs == 1 || thick) 4.dp else 2.dp) else if (hc) 2.dp else 1.dp, tween(200), label = "borderW")
    Box(
        modifier = modifier
            .zIndex(if (lit) 1f else 0f)
            .graphicsLayer {
                scaleX = sc.value
                scaleY = sc.value
                translationY = -3.dp.toPx() * lift.value
            }
            .then(
                if (glow != null && fs != 1 && fs != 3) Modifier.softGlow(
                    glow, glowLevel, { gl.value }, cornerDp
                )
                else Modifier
            )
            .clip(shape)
            .background(bgColor)
            .then(
                if (glow == null || (fs == 1 && lit)) {
                    Modifier.border(borderW, borderColor, shape)
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                if (it.isFocused && !focused && sounds &&
                    SystemClock.elapsedRealtime() - born > 600L
                ) {
                    playSoftFocus(view, vol)
                }
                focused = it.isFocused
                onFocusChange?.invoke(it.isFocused)
            }
            .then(
                if (onLongClick != null) Modifier.combinedClickable(
                    onClick = onClick, onLongClick = onLongClick
                ) else Modifier.clickable(onClick = onClick)
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Opciones visuales globales que leen las fichas (las pone MainActivity desde Cfg). */
object UiPrefs {
    var uniform by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
fun AppTile(
    app: AppInfo,
    modifier: Modifier = Modifier,
    useSize: Boolean = true,
    fixedScale: Float? = null,
    bannerStyle: Boolean = false,
    tileStyle: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    preview: Boolean = false,
    samsung: Boolean = false,
    onClick: () -> Unit
) {
    val st = LocalDock.current
    val f = fixedScale
        ?: if (useSize) (st.scalePct / 100f) * ((st.sizes[app.pkg] ?: 100) / 100f) else 1f
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val badges = LocalBadges.current
    val unread = badges.unread[app.pkg] ?: 0
    val isNew = app.pkg in badges.newPkgs
    // Estilo de ficha (solo el dock lo pasa): medidas y forma.
    val dims = tileDims(tileStyle)
    val round = tileStyle == 3
    GlassBox(
        onClick = {
            val c = holder[0]
            TileBounds.last = if (c != null && c.isAttached) c.boundsInRoot() else null
            onClick()
        },
        onLongClick = onLongClick,
        modifier = modifier
            .size((dims.first * f).dp, (dims.second * f).dp)
            .onGloballyPositioned { holder[0] = it },
        shape = if (round) CircleShape else RoundedCornerShape(st.corner.dp),
        glow = LocalAccent.current ?: Color(app.glow),
        glowLevel = st.glow,
        cornerDp = if (round) (dims.second * f / 2f).dp else st.corner.dp,
        onFocusChange = onFocusChange,
        preview = preview,
        extraScale = if (samsung) 0.04f else 0f
    ) {
        if (tileStyle >= 2) {
            // Icono cuadrado o circulo: solo la imagen (el nombre sale en la tarjeta bajo el dock).
            if (app.banner && !UiPrefs.uniform) {
                Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (app.banner) {
                // Iconos uniformes: la imagen completa sobre un fondo del color de la app.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(app.glow).copy(alpha = 0.55f), Color(0xE60B1020)))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        app.art, app.label,
                        Modifier.fillMaxSize().padding((8 * f).dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Image(
                    app.art, app.label,
                    Modifier.fillMaxSize().padding((12 * f).dp),
                    contentScale = ContentScale.Fit
                )
            }
        } else if (app.banner) {
            Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (bannerStyle) {
            // Apps sin banner: se les arma uno con el color de la app y su icono al centro.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(app.glow).copy(alpha = 0.6f), Color(0xE60B1020))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(app.art, null, Modifier.size((40 * f).dp))
                    Text(
                        app.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.art, null, Modifier.size((40 * f).dp))
                Text(
                    app.label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (samsung && (unread > 0 || isNew)) {
            // Punto de novedades: rojo si hay avisos, verde si la app es nueva.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (unread > 0) Color(0xFFE5484D) else Color(0xFF3DD68C))
            )
        } else if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (unread > 99) "99+" else unread.toString(),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        } else if (isNew) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3DD68C))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    "NUEVA", color = Color(0xFF062B1B), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun Clock(h24: Boolean = false, size: TextUnit = 32.sp, ampmSize: TextUnit = 17.sp) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val timeF = remember(h24) { SimpleDateFormat(if (h24) "HH:mm" else "h:mm", Locale.getDefault()) }
    val ampmF = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val txt = buildAnnotatedString {
        append(timeF.format(now))
        if (!h24) {
            withStyle(SpanStyle(fontSize = ampmSize, fontWeight = FontWeight.Normal, color = Color(0xCCFFFFFF))) {
                append(" " + ampmF.format(now))
            }
        }
    }
    Text(
        txt, color = Color(0xF2FFFFFF), fontSize = size,
        fontWeight = if (size.value > 40f) FontWeight.Light else FontWeight.Normal,
        style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 8f))
    )
}

@Composable
fun DateText(modifier: Modifier = Modifier, size: TextUnit = 16.sp, fmt: Int = 0) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000)
        }
    }
    val f = remember(fmt) { SimpleDateFormat(datePattern(fmt), Locale.forLanguageTag("es-VE")) }
    Text(
        f.format(now).replaceFirstChar { it.uppercase() },
        color = Color(0xCCFFFFFF), fontSize = size,
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
