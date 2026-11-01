package com.mini.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MediaListener : NotificationListenerService()

class MediaInfo(
    val title: String,
    val artist: String,
    val bitmap: Bitmap?,
    val playing: Boolean,
    val controller: MediaController
)

fun readMedia(ctx: Context): MediaInfo? {
    try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
            ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return MediaInfo(title, artist, bmp, playing, c)
    } catch (e: Exception) {
        return null
    }
}

fun sameMedia(a: MediaInfo?, b: MediaInfo?): Boolean {
    if (a == null || b == null) return a == null && b == null
    return a.title == b.title && a.artist == b.artist && a.playing == b.playing &&
        (a.bitmap == null) == (b.bitmap == null) &&
        a.controller.sessionToken == b.controller.sessionToken
}

fun livePosition(c: MediaController): Pair<Long, Long> {
    return try {
        val st = c.playbackState
        val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        if (pos < 0L) pos = 0L
        if (dur > 0L && pos > dur) pos = dur
        Pair(pos, dur)
    } catch (e: Exception) {
        Pair(0L, 0L)
    }
}

fun seekBy(c: MediaController, d: Long) {
    val r = livePosition(c)
    var t = r.first + d
    if (t < 0L) t = 0L
    if (r.second > 0L && t > r.second) t = r.second
    c.transportControls.seekTo(t)
}

fun DrawScope.drawPlay(c: Color) {
    val w = size.width
    val h = size.height
    val p = Path()
    p.moveTo(w * 0.3f, h * 0.18f)
    p.lineTo(w * 0.85f, h * 0.5f)
    p.lineTo(w * 0.3f, h * 0.82f)
    p.close()
    drawPath(p, c)
}

fun DrawScope.drawPause(c: Color) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(w * 0.06f, w * 0.06f)
    drawRoundRect(c, Offset(w * 0.24f, h * 0.18f), Size(w * 0.2f, h * 0.64f), r)
    drawRoundRect(c, Offset(w * 0.56f, h * 0.18f), Size(w * 0.2f, h * 0.64f), r)
}

fun DrawScope.drawNext(c: Color) {
    val w = size.width
    val h = size.height
    val p = Path()
    p.moveTo(w * 0.2f, h * 0.2f)
    p.lineTo(w * 0.68f, h * 0.5f)
    p.lineTo(w * 0.2f, h * 0.8f)
    p.close()
    drawPath(p, c)
    drawRoundRect(c, Offset(w * 0.74f, h * 0.2f), Size(w * 0.1f, h * 0.6f), CornerRadius(w * 0.04f, w * 0.04f))
}

fun DrawScope.drawPrev(c: Color) {
    val w = size.width
    val h = size.height
    val p = Path()
    p.moveTo(w * 0.8f, h * 0.2f)
    p.lineTo(w * 0.32f, h * 0.5f)
    p.lineTo(w * 0.8f, h * 0.8f)
    p.close()
    drawPath(p, c)
    drawRoundRect(c, Offset(w * 0.16f, h * 0.2f), Size(w * 0.1f, h * 0.6f), CornerRadius(w * 0.04f, w * 0.04f))
}

@Composable
fun Icon24(kind: String, color: Color, size: Int = 22) {
    Canvas(Modifier.size(size.dp)) {
        when (kind) {
            "play" -> drawPlay(color)
            "pause" -> drawPause(color)
            "next" -> drawNext(color)
            else -> drawPrev(color)
        }
    }
}

@Composable
fun CircleBtn(size: Int, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    GlassBox(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        content = content
    )
}

fun DrawScope.eqBars(hs: List<Float>, color: Color) {
    val bw = size.width / 7f
    for (i in 0 until 4) {
        val bh = size.height * hs[i]
        drawRoundRect(
            color,
            Offset(i * 2f * bw, size.height - bh),
            Size(bw, bh),
            CornerRadius(bw / 2f, bw / 2f)
        )
    }
}

@Composable
fun EqAnimated(color: Color) {
    val t = rememberInfiniteTransition(label = "eq")
    val a = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val c = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse), label = "c")
    val d = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), label = "d")
    Canvas(Modifier.size(22.dp, 18.dp)) {
        eqBars(listOf(a.value, b.value, c.value, d.value), color)
    }
}

@Composable
fun Equalizer(playing: Boolean, color: Color) {
    if (playing) {
        EqAnimated(color)
    } else {
        Canvas(Modifier.size(22.dp, 18.dp)) {
            eqBars(listOf(0.3f, 0.3f, 0.3f, 0.3f), color)
        }
    }
}

@Composable
fun MusicPill(m: MediaInfo, art: ImageBitmap?) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m.controller, m.title, m.playing) {
        while (true) {
            val r = withContext(Dispatchers.IO) { livePosition(m.controller) }
            pos = r.first
            dur = r.second
            delay(500)
        }
    }
    val tc = m.controller.transportControls
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0x44FFFFFF), Color(0x1AFFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), shape)
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Text("♪", color = Color.White, fontSize = 20.sp)
                }
            }
            Column(Modifier.widthIn(max = 200.dp)) {
                Text(
                    "REPRODUCIENDO", color = Color(0xB3FFFFFF), fontSize = 9.sp,
                    letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    m.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    m.artist, color = Color(0xB3FFFFFF), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Equalizer(m.playing, Color.White)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleBtn(32, { tc.skipToPrevious() }) { Icon24("prev", Color.White, 16) }
                CircleBtn(32, { seekBy(m.controller, -15000L) }) {
                    Text("-15", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                CircleBtn(40, { if (m.playing) tc.pause() else tc.play() }) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon24(if (m.playing) "pause" else "play", Color(0xFF101418), 16)
                    }
                }
                CircleBtn(32, { seekBy(m.controller, 15000L) }) {
                    Text("+15", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                CircleBtn(32, { tc.skipToNext() }) { Icon24("next", Color.White, 16) }
            }
        }
        val frac = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        Canvas(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)) {
            drawRect(Color(0x33FFFFFF))
            drawRect(Color.White, size = Size(size.width * frac, size.height))
        }
    }
}
