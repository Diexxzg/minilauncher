package com.mini.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Repositorio de GitHub donde se publican los Releases (publico, sin token). */
const val UPDATE_REPO = "Diexxzg/minilauncher"

private const val DAY_MS = 24L * 60L * 60L * 1000L

/** Version publicada: numero, novedades, enlace directo al APK y tamano en bytes. */
class UpdateInfo(val version: String, val notes: String, val url: String, val size: Long)

/**
 * Estado del actualizador. phase:
 * 0 = nada, 1 = buscando, 2 = ya estas al dia, 3 = hay version nueva, 4 = descargando,
 * 5 = lista para instalar, 6 = error, 7 = falta el permiso de instalar apps desconocidas.
 */
class UpdaterState(private val ctx: Context, private val prefs: Prefs) {
    var phase by mutableIntStateOf(0)
    var visible by mutableStateOf(false)
    var info by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf("")
    private var job: Job? = null

    val current: String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
    }.getOrNull() ?: "0"

    private fun apkFile() = File(ctx.cacheDir, "shared/update.apk")

    /** Borra un APK viejo que haya quedado en la cache (llamar en segundo plano). */
    fun cleanOld() {
        runCatching { apkFile().delete() }
        runCatching { File(ctx.cacheDir, "shared/update.part").delete() }
    }

    /** true si ya paso un dia desde la ultima consulta correcta. */
    fun dueToday(): Boolean {
        val last = prefs.getStr("updLast", "").toLongOrNull() ?: 0L
        return System.currentTimeMillis() - last > DAY_MS
    }

    /** Abre el cuadro desde Configuracion: muestra lo que ya se sabe o busca de nuevo. */
    fun open(scope: CoroutineScope) {
        visible = true
        if (phase == 3 || phase == 4 || phase == 5 || phase == 7) return
        scope.launch { check(true) }
    }

    /** Cierra el cuadro. Si se estaba descargando, cancela la descarga. */
    fun dismiss() {
        if (phase == 4) {
            job?.cancel()
            job = null
            phase = 3
        }
        visible = false
    }

    /**
     * Consulta el ultimo Release. manual = true muestra "buscando", "al dia" y los errores;
     * manual = false (aviso diario) es silencioso. Devuelve true si hay una version nueva.
     */
    suspend fun check(manual: Boolean): Boolean {
        // El aviso diario no interrumpe una busqueda, descarga o instalacion en curso.
        if (!manual && (phase == 1 || phase == 4 || phase == 5 || phase == 7)) return false
        if (manual) {
            phase = 1
            error = ""
        }
        val r = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        val latest = r.getOrNull()
        if (latest == null) {
            if (manual) {
                phase = 6
                error = r.exceptionOrNull()?.message
                    ?: "No se pudo consultar GitHub. Revisa la conexión."
            }
            return false
        }
        prefs.putStr("updLast", System.currentTimeMillis().toString())
        if (newer(latest.version, current)) {
            info = latest
            phase = 3
            return true
        }
        if (manual) phase = 2
        return false
    }

    /** Descarga el APK con barra de progreso y lo comprueba antes de ofrecer instalarlo. */
    fun startDownload(scope: CoroutineScope) {
        val i = info ?: return
        if (phase == 4) return
        phase = 4
        progress = 0f
        error = ""
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { download(i) } }
            if (phase != 4) return@launch
            val problem = r.exceptionOrNull()?.message ?: r.getOrNull()
            if (problem == null) {
                phase = 5
            } else {
                phase = 6
                error = problem
            }
        }
    }

    /** Abre el instalador de Android. Si falta el permiso, pasa a la fase 7. */
    fun install() {
        val f = apkFile()
        if (!f.exists()) {
            phase = 3
            return
        }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            phase = 7
            return
        }
        // Instalacion por sesion: Android no marca la app como "restringida" y la accesibilidad sobrevive.
        try {
            installSession(f)
            return
        } catch (e: Exception) {
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            phase = 6
            error = "Esta TV no tiene instalador de paquetes."
        }
    }

    private fun installSession(f: java.io.File) {
        val pi = ctx.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setAppPackageName(ctx.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { ss ->
            f.inputStream().use { inp ->
                ss.openWrite("app.apk", 0, f.length()).use { out ->
                    inp.copyTo(out)
                    ss.fsync(out)
                }
            }
            val it = Intent(ctx, InstallReceiver::class.java).setAction("com.mini.launcher.INSTALL_DONE")
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pend = android.app.PendingIntent.getBroadcast(ctx, id, it, flags)
            ss.commit(pend.intentSender)
        }
    }

    /** Lleva a la pantalla "Instalar apps desconocidas" de este launcher. */
    fun openPermission() {
        try {
            val i = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + ctx.packageName)
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            try {
                val i = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
            } catch (e2: Exception) {
                phase = 6
                error = "No se pudo abrir el permiso. Actívalo en Ajustes > Seguridad."
            }
        }
    }

    /** Al volver de la pantalla de permisos: si ya lo dieron, instala sin pedir nada mas. */
    fun onResume() {
        if (visible && phase == 7 && ctx.packageManager.canRequestPackageInstalls()) install()
    }

    private fun fetchLatest(): UpdateInfo {
        val c = URL("https://api.github.com/repos/" + UPDATE_REPO + "/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "OnePieceLauncher")
            val code = c.responseCode
            if (code == 404) throw IOException("Todavía no hay versiones publicadas.")
            if (code != 200) throw IOException("GitHub respondió con error " + code + ".")
            val o = JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
            val tag = o.optString("tag_name").trim()
            if (tag.isEmpty()) throw IOException("La versión publicada no tiene número.")
            val assets = o.optJSONArray("assets")
            var url = ""
            var size = 0L
            if (assets != null) {
                for (k in 0 until assets.length()) {
                    val a = assets.optJSONObject(k) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size", 0L)
                        break
                    }
                }
            }
            if (url.isEmpty()) throw IOException("La última versión no trae un APK.")
            return UpdateInfo(tag.removePrefix("v").removePrefix("V"), cleanNotes(o.optString("body")), url, size)
        } finally {
            c.disconnect()
        }
    }

    /** Devuelve null si todo salio bien o el texto del problema. */
    private suspend fun download(i: UpdateInfo): String? {
        val dir = File(ctx.cacheDir, "shared")
        dir.mkdirs()
        val part = File(dir, "update.part")
        val dest = apkFile()
        part.delete()
        dest.delete()
        val c = URL(i.url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "OnePieceLauncher")
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) return "No se pudo descargar (error " + c.responseCode + ")."
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else i.size
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100L / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                progress = pct / 100f
                            }
                        }
                    }
                }
            }
            if (total > 0 && part.length() != total) {
                part.delete()
                return "La descarga quedó incompleta. Inténtalo de nuevo."
            }
            val bad = verify(part)
            if (bad != null) {
                part.delete()
                return bad
            }
            if (!part.renameTo(dest)) return "No se pudo guardar el archivo descargado."
            return null
        } catch (e: IOException) {
            part.delete()
            return "Se cortó la descarga. Revisa la conexión."
        } finally {
            c.disconnect()
        }
    }

    /** Comprueba que sea un APK de este launcher y que lo firme la misma llave que el instalado. */
    private fun verify(f: File): String? {
        try {
            val pm = ctx.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
            val arch = pm.getPackageArchiveInfo(f.path, flags)
                ?: return "El archivo descargado no es un APK válido."
            if (arch.packageName != ctx.packageName) return "El APK descargado no es de este launcher."
            if (Build.VERSION.SDK_INT >= 28) {
                val mine = pm.getPackageInfo(ctx.packageName, flags)
                val a = signers(arch)
                val b = signers(mine)
                if (a.isNotEmpty() && b.isNotEmpty() && a.intersect(b).isEmpty()) {
                    return "La actualización está firmada con otra llave, y Android no la instalará encima. " +
                        "Haz una copia de seguridad, desinstala el launcher una sola vez e instala este APK."
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    @RequiresApi(28)
    private fun signers(p: PackageInfo): Set<String> {
        val si = p.signingInfo ?: return emptySet()
        val arr = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return arr?.map { it.toCharsString() }?.toSet() ?: emptySet()
    }
}

/** true si la version a (por ejemplo 2.10) es mayor que b (2.9). Compara numero a numero. */
fun newer(a: String, b: String): Boolean {
    fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").split(".", "-", " ")
        .map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
    val x = parts(a)
    val y = parts(b)
    for (k in 0 until maxOf(x.size, y.size)) {
        val p = x.getOrElse(k) { 0 }
        val q = y.getOrElse(k) { 0 }
        if (p != q) return p > q
    }
    return false
}

/** Quita simbolos de Markdown del texto de las novedades y lo recorta. */
private fun cleanNotes(raw: String): String {
    val lines = raw.replace("\r", "").split("\n").map { l ->
        var t = l.trim().trimStart('#').trim().replace("**", "").replace("`", "")
        if (t.startsWith("- ") || t.startsWith("* ")) t = "• " + t.substring(2)
        t
    }.filter { it.isNotEmpty() }
    val text = lines.joinToString("\n")
    return if (text.length > 700) text.substring(0, 700).trimEnd() + "…" else text
}

private fun sizeText(bytes: Long): String =
    if (bytes <= 0L) "" else String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)

/**
 * Cuadro de actualizacion (mismo estilo que el aviso de modo ahorro). Cada fase se dibuja
 * dentro de key(phase) para que el foco pase solo al primer boton de la fase nueva.
 */
@Composable
fun UpdateDialog(st: UpdaterState, scope: CoroutineScope, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        key(st.phase) {
            Column(
                Modifier
                    .width(520.dp)
                    .clip(shape)
                    .background(Color(0xF0101624))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val i = st.info
                val title = when (st.phase) {
                    1 -> "Buscando actualizaciones…"
                    2 -> "Ya tienes la última versión"
                    3 -> "Versión " + (i?.version ?: "") + " disponible"
                    4 -> "Descargando " + (st.progress * 100f).toInt() + " %"
                    5 -> "Descarga lista"
                    7 -> "Falta un permiso"
                    else -> "No se pudo actualizar"
                }
                Text(
                    title, color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp)
                )
                val body = when (st.phase) {
                    2 -> "Versión instalada: " + st.current
                    3 -> "Tienes la " + st.current + ((i?.size ?: 0L).let { if (it > 0L) "  ·  Descarga de " + sizeText(it) else "" }) +
                        (if (!i?.notes.isNullOrBlank()) "\n\n" + i?.notes else "")
                    4 -> "No apagues la TV. Atrás cancela la descarga."
                    5 -> "Android te pedirá confirmar la instalación. El launcher se reiniciará al terminar."
                    7 -> "Android pide permitir que One Piece instale apps. Activa el interruptor, " +
                        "vuelve atrás y la instalación seguirá sola."
                    6 -> st.error
                    else -> ""
                }
                if (body.isNotEmpty()) {
                    Text(
                        body, color = Color(0xCCFFFFFF), fontSize = 14.sp,
                        maxLines = 12, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)
                    )
                }
                if (st.phase == 4) {
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(st.progress.coerceIn(0f, 1f))
                                .background(Color(0xFF4FC3F7))
                        )
                    }
                }
                when (st.phase) {
                    1 -> ActionRow("Cerrar", Modifier.autoFocus()) { onClose() }
                    2 -> ActionRow("Cerrar", Modifier.autoFocus()) { onClose() }
                    3 -> {
                        ActionRow("Actualizar ahora", Modifier.autoFocus()) { st.startDownload(scope) }
                        ActionRow("Ahora no") { onClose() }
                    }
                    4 -> ActionRow("Cancelar descarga", Modifier.autoFocus()) { onClose() }
                    5 -> {
                        ActionRow("Instalar ahora", Modifier.autoFocus()) { st.install() }
                        ActionRow("Ahora no") { onClose() }
                    }
                    7 -> {
                        ActionRow("Abrir el permiso", Modifier.autoFocus()) { st.openPermission() }
                        ActionRow("Ya lo activé: instalar") { st.install() }
                        ActionRow("Ahora no") { onClose() }
                    }
                    else -> {
                        ActionRow("Reintentar", Modifier.autoFocus()) {
                            if (st.info != null) st.startDownload(scope) else scope.launch { st.check(true) }
                        }
                        ActionRow("Cerrar") { onClose() }
                    }
                }
            }
        }
    }
}
