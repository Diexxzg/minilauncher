package com.mini.launcher

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** Categorias de "Todas las apps". El indice 0 (Todas) no filtra nada. */
val APP_CATS = listOf("Todas", "Juegos", "Streaming", "Música", "Herramientas", "Otras")

private val MUSIC_HINTS = listOf("spotify", "music", "deezer", "tidal", "soundcloud", "podcast", "radio")
private val STREAM_HINTS = listOf(
    "netflix", "youtube", "disney", "hbo", "primevideo", "avod", "hulu", "plex", "twitch",
    "crunchyroll", "paramount", "apple.atv", "peacocktv", "tubitv", "pluto", "kodi"
)
private val TOOL_HINTS = listOf(
    "settings", "filemanager", "files", "documentsui", "browser", "chrome", "calculator",
    "vpn", "speedtest", "cleaner", "packageinstaller", "puffin", "tools"
)

/** Devuelve el indice (de APP_CATS) de una app: 1 Juegos, 2 Streaming, 3 Musica, 4 Herramientas, 5 Otras. */
@Suppress("DEPRECATION")
fun appCategory(pm: PackageManager, pkg: String): Int {
    val low = pkg.lowercase()
    val ai = try {
        pm.getApplicationInfo(pkg, 0)
    } catch (e: Exception) {
        null
    }
    if (ai != null) {
        if (ai.category == ApplicationInfo.CATEGORY_GAME ||
            (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        ) return 1
    }
    if (low.contains("game")) return 1
    if (MUSIC_HINTS.any { low.contains(it) }) return 3
    if (STREAM_HINTS.any { low.contains(it) }) return 2
    if (TOOL_HINTS.any { low.contains(it) }) return 4
    if (ai != null) {
        when (ai.category) {
            ApplicationInfo.CATEGORY_VIDEO -> return 2
            ApplicationInfo.CATEGORY_AUDIO -> return 3
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS -> return 4
        }
    }
    return 5
}
