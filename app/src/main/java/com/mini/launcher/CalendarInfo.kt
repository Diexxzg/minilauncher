package com.mini.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

fun hasCalendarPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Proximo evento de los siguientes 7 dias, o null si no hay permiso o no hay eventos. */
fun nextEventText(ctx: Context): String? {
    if (!hasCalendarPermission(ctx)) return null
    return try {
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + 7L * 24 * 3600 * 1000)
        val c = ctx.contentResolver.query(
            b.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, CalendarContract.Instances.BEGIN + " ASC"
        ) ?: return null
        c.use {
            if (!it.moveToFirst()) return null
            val title = it.getString(0)?.takeIf { t -> t.isNotBlank() } ?: "Evento"
            val begin = it.getLong(1)
            val allDay = it.getInt(2) == 1
            val pattern = if (allDay) "EEE d MMM" else "EEE d MMM, h:mm a"
            title + " · " + SimpleDateFormat(pattern, Locale.forLanguageTag("es-VE")).format(begin)
        }
    } catch (e: Exception) {
        null
    }
}

/** Texto del contador de dias hasta una fecha "aaaa-mm-dd"; null si la fecha no es valida. */
fun countdownText(date: String, label: String): String? {
    val d = try {
        LocalDate.parse(date.trim())
    } catch (e: Exception) {
        return null
    }
    val n = ChronoUnit.DAYS.between(LocalDate.now(), d)
    val name = label.ifBlank { "la fecha" }
    return when {
        n > 1L -> "Faltan $n días para $name"
        n == 1L -> "Mañana: $name"
        n == 0L -> "Hoy: $name"
        else -> "Pasaron ${-n} días desde $name"
    }
}
