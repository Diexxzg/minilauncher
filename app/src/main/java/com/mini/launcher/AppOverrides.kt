package com.mini.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Colores de brillo que se pueden elegir por app. El primero es "Automático" (el de la app). */
val GLOW_PRESETS: List<Pair<String, Int?>> = listOf(
    "Automático" to null,
    "Blanco" to 0xFFFFFFFF.toInt(),
    "Rojo" to 0xFFFF5A5F.toInt(),
    "Naranja" to 0xFFFFA24C.toInt(),
    "Amarillo" to 0xFFFFE066.toInt(),
    "Verde" to 0xFF5BE49B.toInt(),
    "Cian" to 0xFF4FD8EB.toInt(),
    "Azul" to 0xFF6C8CFF.toInt(),
    "Violeta" to 0xFFB58CFF.toInt(),
    "Rosa" to 0xFFFF7EC8.toInt()
)

fun glowPresetIndex(prefs: Prefs, pkg: String): Int {
    val v = prefs.glows()[pkg]?.toIntOrNull() ?: return 0
    val i = GLOW_PRESETS.indexOfFirst { it.second == v }
    return if (i < 0) 0 else i
}

private fun iconFile(ctx: Context, pkg: String): File =
    File(File(ctx.filesDir, "icons").also { it.mkdirs() }, "$pkg.png")

fun hasCustomIcon(ctx: Context, pkg: String): Boolean = iconFile(ctx, pkg).exists()

fun resetIcon(ctx: Context, pkg: String) {
    iconFile(ctx, pkg).delete()
}

/** Guarda la imagen elegida como icono propio de la app (se reduce a 256 px como maximo). */
fun importIcon(ctx: Context, uri: Uri, pkg: String): Boolean = try {
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    if (bmp == null) {
        false
    } else {
        val k = 256f / maxOf(bmp.width, bmp.height).toFloat()
        val out = if (k < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
        } else {
            bmp
        }
        iconFile(ctx, pkg).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }
} catch (e: Exception) {
    false
}

/** Aplica nombre, color de brillo e icono propios sobre la lista de apps cargada. */
fun applyOverrides(ctx: Context, prefs: Prefs, list: List<AppInfo>): List<AppInfo> {
    val names = prefs.names()
    val glows = prefs.glows()
    return list.map { a ->
        val n = names[a.pkg]
        val g = glows[a.pkg]?.toIntOrNull()
        val f = iconFile(ctx, a.pkg)
        val icon = if (f.exists()) {
            try {
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        if (n == null && g == null && icon == null) {
            a
        } else {
            AppInfo(
                a.pkg, n ?: a.label, icon ?: a.art, if (icon != null) false else a.banner,
                a.intent, g ?: a.glow
            )
        }
    }.sortedBy { it.label.lowercase() }
}
