package com.mini.launcher

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FillVideoView(ctx: Context) : VideoView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            View.getDefaultSize(0, widthMeasureSpec),
            View.getDefaultSize(0, heightMeasureSpec)
        )
    }
}

@Composable
fun WallpaperLayer(file: File?) {
    if (file == null) return
    val ext = file.extension.lowercase()
    if (ext in setOf("mp4", "webm", "mkv", "3gp")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                FillVideoView(c).apply {
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                    }
                    setOnErrorListener { _, _, _ -> true }
                }
            },
            update = { v ->
                if (v.tag != file.path) {
                    v.tag = file.path
                    v.setVideoPath(file.path)
                    v.start()
                } else if (!v.isPlaying) {
                    v.start()
                }
            }
        )
    } else if (ext == "gif" || ext == "webp") {
        val drawable by produceState<Drawable?>(null, file) {
            value = withContext(Dispatchers.IO) {
                try {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                } catch (e: Exception) {
                    null
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            },
            update = { iv ->
                val d = drawable
                iv.setImageDrawable(d)
                if (d is AnimatedImageDrawable) {
                    d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    d.start()
                }
            }
        )
    } else {
        val bmp by produceState<ImageBitmap?>(null, file) {
                        value = withContext(Dispatchers.IO) { loadBitmap(file, 1920) }
        }
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
