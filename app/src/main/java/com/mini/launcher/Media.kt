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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MediaListener : NotificationListenerService()

class MediaInfo(
    val title: String,
    val artist: String,
    val bitmap: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
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
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        var pos = st?.position ?: 0L
        if (st != null && playing) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (pos < 0L) pos = 0L
        if (dur > 0L && pos > dur) pos = dur
        return MediaInfo(title, artist, bmp, playing, pos, dur, c)
    } catch (e: Exception) {
        return null
    }
}

fun timeText(ms: Long): String {
    val s = if (ms < 0L) 0L else ms / 1000L
    return "%d:%02d".format(s / 60L, s % 60L)
}

fun seekBy(m: MediaInfo, d: Long) {
    var t = m.position + d
    if (t < 0L) t = 0L
    if (m.duration > 0L && t > m.duration) t = m.duration
    m.controller.transportControls.seekTo(t)
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
fun Icon24(kind: String, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        when (kind) {
            "play" -> drawPlay(color)
            "pause" -> drawPause(color)
            "next" -> drawNext(color)
            else -> drawPrev(color)
        }
    }
}

@Composable
fun CircleBtn(
    size: Int,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    GlassBox(
        onClick = onClick,
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        content = content
    )
}

@Composable
fun ControlsRow(m: MediaInfo) {
    val tc = m.controller.transportControls
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircleBtn(44, { tc.skipToPrevious() }) { Icon24("prev", Color.White) }
        CircleBtn(44, { seekBy(m, -15000L) }) {
            Text("-15", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        CircleBtn(64, { if (m.playing) tc.pause() else tc.play() }) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon24(if (m.playing) "pause" else "play", Color(0xFF101418))
            }
        }
        CircleBtn(44, { seekBy(m, 15000L) }) {
            Text("+15", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        CircleBtn(44, { tc.skipToNext() }) { Icon24("next", Color.White) }
    }
}

@Composable
fun GlassCurrent(m: MediaInfo, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val target = if (m.duration > 0L) (m.position.toFloat() / m.duration.toFloat()).coerceIn(0f, 1f) else 0f
    val frac by animateFloatAsState(target, tween(900, easing = LinearEasing), label = "progress")
    Column(modifier) {
        Column(Modifier.widthIn(max = 440.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown &&
                            (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)
                        ) {
                            seekBy(m, if (e.key == Key.DirectionRight) 15000L else -15000L)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
            ) {
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val w = size.width
                    val h = size.height
                    val th = (if (focused) 8f else 6f).dp.toPx()
                    val y = (h - th) / 2f
                    val rad = CornerRadius(th / 2f, th / 2f)
                    drawRoundRect(Color(0x44FFFFFF), Offset(0f, y), Size(w, th), rad)
                    val pw = w * frac
                    if (pw > 0f) {
                        drawRoundRect(Color.White, Offset(0f, y), Size(pw, th), rad)
                    }
                    if (focused) {
                        drawCircle(Color.White, 9.dp.toPx(), Offset(pw, h / 2f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeText(m.position), color = Color(0xCCFFFFFF), fontSize = 12.sp)
                Text("-" + timeText(m.duration - m.position), color = Color(0xCCFFFFFF), fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ControlsRow(m)
            }
        }
    }
}

@Composable
fun MusicBar(m: MediaInfo, art: ImageBitmap?, onListen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(96.dp)
                .clip(shape)
                .background(Color(0x33FFFFFF))
                .border(1.dp, Color(0x44FFFFFF), shape),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text("♪", color = Color.White, fontSize = 36.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            m.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 440.dp)
        )
        Text(
            m.artist, color = Color(0xB3FFFFFF), fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 440.dp)
        )
    }
}
