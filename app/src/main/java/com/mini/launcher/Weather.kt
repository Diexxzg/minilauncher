package com.mini.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

const val CITY = "Caracas"

object WeatherCache {
    var key: String = ""
    var text: String? = null
    var lineKey: String = ""
    var line: String? = null
}

/**
 * Ajustes del clima que se leen fuera de Configuracion (1 = activado). OnePieceApp los llena al
 * abrir y Configuracion los mantiene al dia.
 */
object WeatherOpts {
    var forecast by mutableIntStateOf(0)
    var autoLoc by mutableIntStateOf(0)
}

fun httpGet(url: String): String? {
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

fun codeToEmoji(c: Int): String {
    return when (c) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }
}

// ---------- Lugar: ubicacion automatica o ciudad elegida ----------

private class Place(val lat: Double, val lon: Double, val name: String)

private val placeCache = HashMap<String, Place>()

fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Ultima ubicacion conocida de la TV, solo si el ajuste esta activo y hay permiso; si no, null. */
@Suppress("DEPRECATION")
private fun autoPlace(): Place? {
    val ctx = OnePieceApp.instance ?: return null
    if (Prefs(ctx).getInt("autoLoc", 0) != 1) return null
    if (!hasLocationPermission(ctx)) return null
    return try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: android.location.Location? = null
        for (prov in lm.getProviders(true)) {
            val l0 = try {
                lm.getLastKnownLocation(prov)
            } catch (e: SecurityException) {
                null
            }
            val l = l0 ?: continue
            val b0 = best
            if (b0 == null || l.time > b0.time) best = l
        }
        val b = best ?: return null
        var name = "Mi ubicación"
        try {
            if (Geocoder.isPresent()) {
                val a = Geocoder(ctx, Locale.forLanguageTag("es")).getFromLocation(b.latitude, b.longitude, 1)
                val first = a?.firstOrNull()
                val n = first?.locality ?: first?.subAdminArea
                if (!n.isNullOrBlank()) name = n
            }
        } catch (e: Exception) {
        }
        Place(b.latitude, b.longitude, name)
    } catch (e: Exception) {
        null
    }
}

private fun cityPlace(city: String): Place? {
    synchronized(placeCache) {
        val c = placeCache[city]
        if (c != null) return c
    }
    val g = httpGet(
        "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name=" +
            URLEncoder.encode(city, "UTF-8")
    ) ?: return null
    val r = JSONObject(g).optJSONArray("results") ?: return null
    if (r.length() == 0) return null
    val o = r.getJSONObject(0)
    val p = Place(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("name", city))
    synchronized(placeCache) { placeCache[city] = p }
    return p
}

private fun placeFor(city: String): Place? = autoPlace() ?: cityPlace(city)

/** Devuelve (codigo del clima, temperatura, nombre de la ciudad). Respeta la ubicacion automatica. */
fun fetchWeatherRaw(city: String, fahrenheit: Boolean): Triple<Int, Long, String>? {
    try {
        val pl = placeFor(city) ?: return null
        val w = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=${pl.lat}&longitude=${pl.lon}" +
                "&current=temperature_2m,weather_code" +
                (if (fahrenheit) "&temperature_unit=fahrenheit" else "")
        ) ?: return null
        val cur = JSONObject(w).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m"))
        return Triple(cur.getInt("weather_code"), t, pl.name)
    } catch (e: Exception) {
        return null
    }
}

fun fetchWeather(city: String, fahrenheit: Boolean = false): String? {
    val r = fetchWeatherRaw(city, fahrenheit) ?: return null
    return codeToEmoji(r.first) + " " + r.second + "° · " + r.third
}

/** Texto para el modo ambiente: "Caracas, 21°C". */
fun fetchWeatherLine(city: String, fahrenheit: Boolean = false): String? {
    val r = fetchWeatherRaw(city, fahrenheit) ?: return null
    return r.third + ", " + r.second + (if (fahrenheit) "°F" else "°C")
}

// ---------- Pronostico de 3 dias (25) ----------

class DayForecast(val label: String, val code: Int, val max: Long, val min: Long)

private val DAY_NAMES = arrayOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Hoy y los 2 dias siguientes. Null si no hay red o no se encuentra el lugar. */
fun fetchForecast(city: String, fahrenheit: Boolean): List<DayForecast>? {
    try {
        val pl = placeFor(city) ?: return null
        val w = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=${pl.lat}&longitude=${pl.lon}" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=auto" +
                (if (fahrenheit) "&temperature_unit=fahrenheit" else "")
        ) ?: return null
        val d = JSONObject(w).getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val mx = d.getJSONArray("temperature_2m_max")
        val mn = d.getJSONArray("temperature_2m_min")
        val out = ArrayList<DayForecast>()
        for (i in 0 until dates.length()) {
            val label = if (i == 0) "Hoy" else DAY_NAMES[LocalDate.parse(dates.getString(i)).dayOfWeek.value - 1]
            out.add(DayForecast(label, codes.getInt(i), Math.round(mx.getDouble(i)), Math.round(mn.getDouble(i))))
        }
        return if (out.isEmpty()) null else out
    } catch (e: Exception) {
        return null
    }
}

/** "Hoy 🌧️ 31°/24°  ·  Mié ☀️ 30°/23°  ·  Jue ..." */
fun forecastLine(list: List<DayForecast>): String =
    list.joinToString("  ·  ") { it.label + " " + codeToEmoji(it.code) + " " + it.max + "°/" + it.min + "°" }

@Composable
fun WeatherText(
    city: String,
    fahrenheit: Boolean,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp
) {
    val auto = WeatherOpts.autoLoc
    val showFc = WeatherOpts.forecast == 1
    val key = city + "|" + fahrenheit + "|" + auto
    var text by remember(key) {
        mutableStateOf<String?>(if (WeatherCache.key == key) WeatherCache.text else null)
    }
    var fc by remember(key) { mutableStateOf<String?>(null) }
    // Mientras llega el primer dato se muestra un esqueleto; si falla, no se muestra nada.
    var tried by remember(key) { mutableStateOf(text != null) }
    LaunchedEffect(key, showFc) {
        while (true) {
            val r = withContext(Dispatchers.IO) { fetchWeather(city, fahrenheit) }
            tried = true
            if (r != null) {
                text = r
                WeatherCache.key = key
                WeatherCache.text = r
            }
            if (showFc) {
                val f = withContext(Dispatchers.IO) { fetchForecast(city, fahrenheit) }
                if (f != null) fc = forecastLine(f)
            }
            delay(if (text == null) 60_000L else 1_800_000L)
        }
    }
    val t = text
    val f = fc
    if (t != null) {
        if (showFc && f != null) {
            Column(modifier) {
                Text(t, color = Color(0xEBFFFFFF), fontSize = size, fontWeight = FontWeight.Medium)
                Text(f, color = Color(0xB3FFFFFF), fontSize = size * 0.7f, maxLines = 1)
            }
        } else {
            Text(
                t, color = Color(0xEBFFFFFF), fontSize = size,
                fontWeight = FontWeight.Medium, modifier = modifier
            )
        }
    } else if (!tried) {
        SkeletonBox(
            modifier.width((size.value * 7f).dp).height((size.value * 1.15f).dp),
            corner = 6.dp
        )
    } else {
        Text(
            "Clima no disponible", color = Color(0x80FFFFFF), fontSize = size * 0.75f,
            maxLines = 1, modifier = modifier
        )
    }
}
