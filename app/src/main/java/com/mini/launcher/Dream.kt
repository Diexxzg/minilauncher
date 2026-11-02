package com.mini.launcher

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Protector de pantalla real del sistema (Ajustes > Preferencias del dispositivo >
 * Protector de pantalla). Usa vistas normales: hora, fecha y el fondo actual.
 */
class AmbientDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var col: LinearLayout? = null
    private var dateView: TextView? = null
    private var step = 0
    private var alive = false
    private val df = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-VE"))

    private val ticker = object : Runnable {
        override fun run() {
            if (!alive) return
            dateView?.text = df.format(Date()).replaceFirstChar { it.uppercase() }
            step++
            // Se mueve un poco para no marcar la pantalla.
            col?.translationX = (((step % 5) - 2) * 12).toFloat()
            col?.translationY = (((step % 3) - 1) * 12).toFloat()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val p = Prefs(this)
        val h24 = p.getInt("clock24", 0) == 1

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())

        val bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        bg.alpha = 0.55f
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        val clock = TextClock(this)
        val fmt = if (h24) "HH:mm" else "h:mm a"
        clock.format12Hour = fmt
        clock.format24Hour = fmt
        clock.textSize = 72f
        clock.setTextColor(0xF2FFFFFF.toInt())
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clock.gravity = Gravity.CENTER

        val date = TextView(this)
        date.textSize = 20f
        date.setTextColor(0xCCFFFFFF.toInt())
        date.gravity = Gravity.CENTER
        dateView = date

        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.addView(clock)
        c.addView(date)
        col = c
        root.addView(c, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        setContentView(root)

        // Fondo actual (si hay): se decodifica fuera del hilo principal.
        val name = p.wallpaper
        val files = listWalls(this)
        val file = files.firstOrNull { it.name == name } ?: files.firstOrNull()
        if (file != null) {
            Thread {
                val b = try {
                    loadBitmap(file, 1280)?.asAndroidBitmap()
                } catch (e: Exception) {
                    null
                }
                if (b != null) handler.post { if (alive) bg.setImageBitmap(b) }
            }.start()
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        alive = true
        handler.post(ticker)
    }

    override fun onDreamingStopped() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
