package com.mini.launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

class AppInfo(
    val pkg: String,
    val label: String,
    val art: ImageBitmap,
    val banner: Boolean,
    val intent: Intent
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    fun favs(): List<String> =
        (sp.getString("favs", "") ?: "").split(",").filter { it.isNotEmpty() }
    fun saveFavs(l: List<String>) {
        sp.edit().putString("favs", l.joinToString(",")).apply()
    }
    fun hidden(): Set<String> = (sp.getStringSet("hidden", emptySet()) ?: emptySet()).toSet()
    fun saveHidden(s: Set<String>) {
        sp.edit().putStringSet("hidden", s).apply()
    }
    var wallpaper: String
        get() = sp.getString("wall", "") ?: ""
        set(v) { sp.edit().putString("wall", v).apply() }
    var initDone: Boolean
        get() = sp.getBoolean("init", false)
        set(v) { sp.edit().putBoolean("init", v).apply() }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val map = LinkedHashMap<String, AppInfo>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || map.containsKey(pkg)) continue
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: continue
            val bn = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val isBanner = bn != null
            val bmp = if (bn != null) bn.toBitmap(288, 168) else ri.loadIcon(pm).toBitmap(128, 128)
            map[pkg] = AppInfo(pkg, ri.loadLabel(pm).toString(), bmp.asImageBitmap(), isBanner, launch)
        }
    }
    return map.values.sortedBy { it.label.lowercase() }
}

fun wallDir(ctx: Context): File {
    val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
    return File(base, "wallpapers").also { it.mkdirs() }
}

fun listWalls(ctx: Context): List<File> =
    (wallDir(ctx).listFiles() ?: emptyArray())
        .filter { it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
        .sortedBy { it.name }

fun loadBitmap(f: File, target: Int): ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= target) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    return BitmapFactory.decodeFile(f.path, o2)?.asImageBitmap()
}

fun importWall(ctx: Context, uri: Uri): String? {
    val name = "fondo_" + System.currentTimeMillis() + ".jpg"
    return try {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            File(wallDir(ctx), name).outputStream().use { out -> input.copyTo(out) }
        }
        name
    } catch (e: Exception) {
        null
    }
}

fun move(list: List<String>, p: String, d: Int): List<String> {
    val i = list.indexOf(p)
    val j = i + d
    if (i < 0 || j < 0 || j >= list.size) return list
    val m = list.toMutableList()
    m.removeAt(i)
    m.add(j, p)
    return m
}
