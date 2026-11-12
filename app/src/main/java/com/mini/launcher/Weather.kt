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

private fun placeFor(city: String): Place? = cityPlace(city)

/** Devuelve (codigo del clima, temperatura, nombre de la ciudad). */
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

@Composable
fun WeatherText(
    city: String,
    fahrenheit: Boolean,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp
) {
    val key = city + "|" + fahrenheit
    var text by remember(key) {
        mutableStateOf<String?>(if (WeatherCache.key == key) WeatherCache.text else null)
    }
    // Mientras llega el primer dato se muestra un esqueleto; si falla, no se muestra nada.
    var tried by remember(key) { mutableStateOf(text != null) }
    LaunchedEffect(key) {
        while (true) {
            val r = withContext(Dispatchers.IO) { fetchWeather(city, fahrenheit) }
            tried = true
            if (r != null) {
                text = r
                WeatherCache.key = key
                WeatherCache.text = r
            }
            delay(if (text == null) 60_000L else 1_800_000L)
        }
    }
    val t = text
    if (t != null) {
            Text(
                t, color = Color(0xEBFFFFFF), fontSize = size,
                fontWeight = FontWeight.Medium, modifier = modifier
            )
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
