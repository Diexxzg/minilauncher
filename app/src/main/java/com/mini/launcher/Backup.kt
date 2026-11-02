package com.mini.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nombre sugerido para la copia: ajustes_launcher_2026-11-02.json */
fun backupName(): String =
    "ajustes_launcher_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

/** Guarda los ajustes en el archivo que eligio el usuario (selector de documentos). */
fun writeBackupTo(ctx: Context, prefs: Prefs, uri: Uri): Boolean =
    try {
        val out = ctx.contentResolver.openOutputStream(uri, "wt")
        if (out == null) {
            false
        } else {
            out.use { it.write(prefs.exportJson().toByteArray(Charsets.UTF_8)) }
            true
        }
    } catch (e: Exception) {
        false
    }

/** Ajustes que seguro aparecen en una copia hecha por este launcher. */
private val BACKUP_KEYS = listOf("init", "favs", "theme", "scale", "spacing", "corner", "glow", "dim", "wall")

/**
 * Restaura desde cualquier archivo elegido. Antes de borrar nada comprueba que sea JSON y que
 * parezca una copia de este launcher, para no vaciar los ajustes con un archivo equivocado.
 */
fun readBackupFrom(ctx: Context, prefs: Prefs, uri: Uri): Boolean =
    try {
        val stream = ctx.contentResolver.openInputStream(uri)
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
        if (text == null || text.isBlank() || text.length > 2_000_000) {
            false
        } else {
            val o = JSONObject(text)
            if (BACKUP_KEYS.none { o.has(it) }) false else prefs.importJson(text)
        }
    } catch (e: Exception) {
        false
    }

/** Abre el menu de compartir de Android con la copia como archivo adjunto. */
fun shareBackup(ctx: Context, prefs: Prefs): Boolean =
    try {
        val f = File(ctx.cacheDir, "shared/" + backupName())
        f.parentFile?.mkdirs()
        f.writeText(prefs.exportJson())
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
        send.type = "application/json"
        send.putExtra(Intent.EXTRA_STREAM, uri)
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Compartir copia de seguridad"))
        true
    } catch (e: Exception) {
        false
    }
