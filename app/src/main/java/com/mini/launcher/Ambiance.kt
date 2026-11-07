package com.mini.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

/** Tema desde la caratula, temas por fechas, filtro calido, tarjeta en esquina, noche y despertador. */
object Amb2 {
    /** ARGB de un tema temporal (0 = ninguno). Lo lee MainActivity al fijar el tema. */
    var themeOverride by mutableIntStateOf(0)

    private var artKey = ""
    private var artColor = 0
    private var filterV: View? = null
    private var filterKey = ""
    private var cardV: TextView? = null
    private var cardKey = ""
    private var minuteTick = 0
    private var rampStart = 0L
    private var rampOrigDim = 0
    private var wakeDay = -1

    fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0.0; var g = 0.0; var bl = 0.0; var ws = 0.0
        val hsv = FloatArray(3)
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = s.getPixel(x, y)
            AColor.colorToHSV(p, hsv)
            val w = hsv[1] * hsv[2] + 0.05
            r += AColor.red(p) * w; g += AColor.green(p) * w; bl += AColor.blue(p) * w; ws += w
        }
        val c = AColor.rgb((r / ws).toInt(), (g / ws).toInt(), (bl / ws).toInt())
        AColor.colorToHSV(c, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.5f); hsv[2] = hsv[2].coerceAtLeast(0.7f)
        return AColor.HSVToColor(hsv)
    }

    /** Color dominante de la caratula actual (0 si no hay). */
    fun artColor(media: MediaInfo?): Int {
        val b = media?.bitmap ?: return 0
        val k = media.title + "|" + media.artist
        if (k != artKey) { artKey = k; artColor = try { dominant(b) } catch (e: Exception) { 0 } }
        return artColor
    }

    private fun dateTheme(): Int {
        val c = Calendar.getInstance()
        val m = c.get(Calendar.MONTH); val d = c.get(Calendar.DAY_OF_MONTH)
        return when {
            m == Calendar.OCTOBER && d >= 24 -> 0xFFFF7A1A.toInt()
            m == Calendar.DECEMBER && d in 15..26 -> 0xFFE53935.toInt()
            (m == Calendar.DECEMBER && d == 31) || (m == Calendar.JANUARY && d <= 2) -> 0xFFFFD54F.toInt()
            m == Calendar.FEBRUARY && d in 10..14 -> 0xFFFF4D8D.toInt()
            else -> 0
        }
    }

    private fun wm(c: Context) = c.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun lp(w: Int, h: Int, grav: Int, mx: Int = 0, my: Int = 0) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = grav; x = mx; y = my }

    private fun filter(c: Context, on: Boolean, pct: Int) {
        val w = wm(c)
        val key = if (on) "on$pct" else "off"
        if (key == filterKey) return
        filterKey = key
        try {
            if (!on || !Settings.canDrawOverlays(c)) {
                filterV?.let { w.removeView(it) }; filterV = null; return
            }
            val v = filterV ?: View(c.applicationContext).also {
                filterV = it
                w.addView(it, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START))
            }
            v.setBackgroundColor(AColor.argb(pct.coerceIn(5, 50) * 255 / 100, 255, 150, 40))
        } catch (e: Exception) { }
    }

    private fun card(c: Context, on: Boolean, corner: Int) {
        val w = wm(c)
        try {
            if (!on || !Settings.canDrawOverlays(c)) {
                cardV?.let { w.removeView(it) }; cardV = null; cardKey = ""; return
            }
            if (cardKey != "c$corner") {
                cardV?.let { w.removeView(it) }; cardV = null; cardKey = "c$corner"
            }
            val tv = cardV ?: TextView(c.applicationContext).also { t ->
                t.setTextColor(AColor.WHITE); t.textSize = 20f; t.setPadding(36, 22, 36, 22)
                t.background = GradientDrawable().apply { setColor(AColor.argb(230, 18, 23, 34)); cornerRadius = 36f }
                val g = when (corner) {
                    1 -> Gravity.TOP or Gravity.START; 2 -> Gravity.BOTTOM or Gravity.END; 3 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.END
                }
                cardV = t
                w.addView(t, lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, g, 48, 48))
            }
            val cal = Calendar.getInstance()
            tv.text = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)) +
                (WeatherCache.line?.let { "  ·  $it" } ?: "")
        } catch (e: Exception) { }
    }

    private fun vol(c: Context): Pair<AudioManager, Int> {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    /** Se llama cada 15 s desde HomeOverlays. */
    fun tick(c: Context, cfg: Cfg, media: MediaInfo?, onWake: () -> Unit) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // Tema temporal: caratula > fecha especial.
        themeOverride = when {
            cfg.ex("artTheme") == "1" && media != null && media.playing -> artColor(media)
            cfg.ex("dateThemes") == "1" -> dateTheme()
            else -> 0
        }
        filter(c, cfg.ex("warmOn") == "1", cfg.ex("warmPct", "20").toIntOrNull() ?: 20)
        card(c, cfg.ex("cornerOn") == "1", cfg.ex("cornerPos", "0").toIntOrNull() ?: 0)
        // Modo noche por contenido: cada minuto baja un paso el volumen mientras suena algo.
        minuteTick = (minuteTick + 1) % 4
        if (minuteTick == 0 && cfg.ex("nightOn") == "1" && (hour >= 22 || hour < 6) && media?.playing == true) {
            try {
                val (am, mx) = vol(c)
                val floor = mx * (cfg.ex("nightVol", "20").toIntOrNull() ?: 20) / 100
                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (cur > floor) am.setStreamVolume(AudioManager.STREAM_MUSIC, cur - 1, 0)
            } catch (e: Exception) { }
        }
        // Despertador con rampa de brillo (fondo) y volumen.
        if (cfg.ex("wakeOn") == "1") {
            val day = cal.get(Calendar.DAY_OF_YEAR)
            val t = cfg.ex("wakeTime", "07:00")
            val nowT = "%02d:%02d".format(hour, cal.get(Calendar.MINUTE))
            if (rampStart == 0L && wakeDay != day && nowT == t) {
                wakeDay = day; rampStart = System.currentTimeMillis(); rampOrigDim = cfg.dim
                cfg.setDim(80)
                onWake()
            }
        }
        if (rampStart != 0L) {
            val mins = (cfg.ex("wakeMin", "10").toIntOrNull() ?: 10).coerceAtLeast(1)
            val f = ((System.currentTimeMillis() - rampStart) / (mins * 60000f)).coerceIn(0f, 1f)
            cfg.setDim((80 - (80 - rampOrigDim) * f).toInt())
            try {
                val (am, mx) = vol(c)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (mx * (0.1f + 0.5f * f)).toInt().coerceAtLeast(1), 0)
            } catch (e: Exception) { }
            if (f >= 1f) { rampStart = 0L; cfg.setDim(rampOrigDim) }
        }
    }

    /** Cuentas regresivas: lineas «yyyy-MM-dd Nombre». Devuelve «Nombre · N dias». */
    fun countdowns(cfg: Cfg): List<String> {
        val now = Calendar.getInstance()
        val today = now.get(Calendar.YEAR) * 366 + now.get(Calendar.DAY_OF_YEAR)
        return cfg.ex("cdList").split("\n").mapNotNull { l ->
            val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})\\s+(.+)$").find(l.trim()) ?: return@mapNotNull null
            val c = Calendar.getInstance().apply {
                set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), 12, 0)
            }
            val ms = c.timeInMillis - System.currentTimeMillis()
            val d = Math.floor(ms / 86400000.0).toInt() + 1
            if (d < 0) null else m.groupValues[4] + " · " + (if (d == 0) "hoy" else "$d días")
        }.take(3)
    }

    /** «25/12/2026 Navidad» → «2026-12-25 Navidad», o null. */
    fun parseCd(s: String): String? {
        val m = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s+(.+)$").find(s.trim()) ?: return null
        val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
        if (d !in 1..31 || mo !in 1..12) return null
        return "%s-%02d-%02d %s".format(m.groupValues[3], mo, d, m.groupValues[4])
    }
}
