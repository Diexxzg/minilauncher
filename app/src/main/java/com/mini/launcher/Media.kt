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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        var pos = st?.position ?: 0L
        if (st != null && playing) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (pos < 0L) pos = 0L
        if (dur > 0L && pos > dur) pos = dur
        return MediaInfo(title, artist, null, playing, pos, dur, c)
    } catch (e: Exception) {
        return null
    }
}

fun timeText(ms: Long): String {
    val s = if (ms < 0L) 0L else ms / 1000L
    return "%d:%02d".format(s / 60L, s % 60L)
}

@Composable
fun GlassCurrent(m: MediaInfo, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val frac = if (m.duration > 0L) (m.position.toFloat() / m.duration.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                    val d = if (e.key == Key.DirectionRight) 15000L else -15000L
                    var t = m.position + d
                    if (t < 0L) t = 0L
                    if (m.duration > 0L && t > m.duration) t = m.duration
                    m.controller.transportControls.seekTo(t)
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val w = size.width
            val h = size.height
            val path = Path()
            path.moveTo(0f, h * 0.8f)
            path.cubicTo(w * 0.3f, h * 0.15f, w * 0.7f, h * 0.15f, w, h * 0.8f)
            val pm = PathMeasure()
            pm.setPath(path, false)
            val done = Path()
            pm.getSegment(0f, pm.length * frac, done, true)
            val sw = (if (focused) 4.5f else 3.2f).dp.toPx()
            drawPath(path, Color(0x44FFFFFF), style = Stroke(width = sw, cap = StrokeCap.Round))
            drawPath(done, Color.White, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeText(m.position), color = Color(0xCCFFFFFF), fontSize = 12.sp)
            Text("-" + timeText(m.duration - m.position), color = Color(0xCCFFFFFF), fontSize = 12.sp)
        }
    }
}

@Composable
fun MusicBar(m: MediaInfo, art: ImageBitmap?, onListen: () -> Unit) {
    val tc = m.controller.transportControls
    Row(
        Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.widthIn(max = 360.dp)) {
            Text(
                m.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                m.artist, color = Color(0xB3FFFFFF), fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        SmallBtn("⏮") { tc.skipToPrevious() }
        SmallBtn(if (m.playing) "⏸" else "▶") { if (m.playing) tc.pause() else tc.play() }
        SmallBtn("⏭") { tc.skipToNext() }
    }
}
