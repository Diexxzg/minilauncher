package com.mini.launcher

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val CITY = "Caracas"

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

fun fetchWeather(city: String): String? {
    try {
        val g = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&name=" +
                URLEncoder.encode(city, "UTF-8")
        ) ?: return null
        val r = JSONObject(g).optJSONArray("results") ?: return null
        if (r.length() == 0) return null
        val o = r.getJSONObject(0)
        val lat = o.getDouble("latitude")
        val lon = o.getDouble("longitude")
        val w = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code"
        ) ?: return null
        val cur = JSONObject(w).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m"))
        return codeToEmoji(cur.getInt("weather_code")) + " " + t + "° · " + o.optString("name", city)
    } catch (e: Exception) {
        return null
    }
}

@Composable
fun WeatherText(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { fetchWeather(CITY) }
            if (r != null) text = r
            delay(if (text == null) 60_000L else 1_800_000L)
        }
    }
    val t = text
    if (t != null) {
        Text(
            t, color = Color(0xEBFFFFFF), fontSize = 20.sp,
            fontWeight = FontWeight.Medium, modifier = modifier
        )
    }
}
