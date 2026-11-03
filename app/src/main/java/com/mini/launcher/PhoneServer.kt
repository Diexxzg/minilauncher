package com.mini.launcher

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.random.Random

/**
 * Servidor local para usar el celular: subir fondos, escribir en la busqueda e importar o
 * exportar la configuracion. Solo corre mientras la pantalla "Conexion con el celular" esta abierta.
 * Cada peticion lleva un codigo de 4 digitos que se muestra en la TV.
 */
class PhoneServer(
    private val ctx: Context,
    private val prefs: Prefs,
    private val onText: (String) -> Unit,
    private val onWall: (String) -> Unit,
    private val onConfig: () -> Unit
) {
    val port = 8080
    val code: String = (1000 + Random.nextInt(9000)).toString()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var running = false

    fun address(): String = (localIp() ?: "sin red") + ":" + port

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(port)
            server = s
            running = true
            Thread {
                while (running) {
                    try {
                        val c = s.accept()
                        Thread { handle(c) }.start()
                    } catch (e: Exception) {
                        break
                    }
                }
            }.start()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }

    private fun reply(c: Socket, status: String, type: String, body: ByteArray, extra: String = "") {
        val o = c.getOutputStream()
        o.write(
            ("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\n$extra" + "Connection: close\r\n\r\n").toByteArray()
        )
        o.write(body)
        o.flush()
    }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 15000
            val i = BufferedInputStream(c.getInputStream())
            val first = readLine(i) ?: return
            val parts = first.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val full = parts[1]
            val path = full.substringBefore("?")
            val q = HashMap<String, String>()
            if (full.contains("?")) {
                for (kv in full.substringAfter("?").split("&")) {
                    val k = kv.substringBefore("=")
                    q[k] = URLDecoder.decode(kv.substringAfter("=", ""), "UTF-8")
                }
            }
            var len = 0L
            while (true) {
                val h = readLine(i) ?: break
                if (h.isEmpty()) break
                if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(":").trim().toLongOrNull() ?: 0L
            }
            if (path == "/") {
                reply(c, "200 OK", "text/html; charset=utf-8", PAGE.toByteArray())
                return
            }
            if (q["k"] != code) {
                reply(c, "403 Forbidden", "text/plain; charset=utf-8", "Código incorrecto".toByteArray())
                return
            }
            when {
                method == "POST" && path == "/upload" -> {
                    if (len <= 0 || len > 300L * 1024 * 1024) {
                        reply(c, "413 Too Large", "text/plain; charset=utf-8", "Archivo demasiado grande".toByteArray())
                        return
                    }
                    val ext = (q["name"] ?: "").substringAfterLast(".", "jpg").lowercase()
                    val ok = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "mp4", "webm", "mkv", "3gp")
                    val name = "fondo_" + System.currentTimeMillis() + "." + (if (ok) ext else "jpg")
                    val f = File(wallDir(ctx), name)
                    f.outputStream().use { out ->
                        val buf = ByteArray(16384)
                        var left = len
                        while (left > 0) {
                            val n = i.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) break
                            out.write(buf, 0, n)
                            left -= n
                        }
                    }
                    onWall(name)
                    reply(c, "200 OK", "text/plain; charset=utf-8", "Fondo subido".toByteArray())
                }
                method == "POST" && path == "/text" -> {
                    val b = ByteArray(minOf(len, 4000L).toInt())
                    var r = 0
                    while (r < b.size) {
                        val n = i.read(b, r, b.size - r)
                        if (n < 0) break
                        r += n
                    }
                    onText(String(b, 0, r, Charsets.UTF_8))
                    reply(c, "200 OK", "text/plain; charset=utf-8", "Texto enviado".toByteArray())
                }
                method == "GET" && path == "/config" -> {
                    reply(
                        c, "200 OK", "application/json; charset=utf-8", prefs.exportJson().toByteArray(),
                        "Content-Disposition: attachment; filename=\"" + backupName() + "\"\r\n"
                    )
                }
                method == "POST" && path == "/config" -> {
                    if (len <= 0 || len > 2_000_000L) {
                        reply(c, "413 Too Large", "text/plain; charset=utf-8", "Archivo no válido".toByteArray())
                        return
                    }
                    val b = ByteArray(len.toInt())
                    var r = 0
                    while (r < b.size) {
                        val n = i.read(b, r, b.size - r)
                        if (n < 0) break
                        r += n
                    }
                    val text = String(b, 0, r, Charsets.UTF_8)
                    val valid = try {
                        val o = JSONObject(text)
                        listOf("init", "favs", "theme", "scale", "spacing", "corner", "glow", "dim", "wall").any { o.has(it) }
                    } catch (e: Exception) {
                        false
                    }
                    if (valid && prefs.importJson(text)) {
                        onConfig()
                        reply(c, "200 OK", "text/plain; charset=utf-8", "Configuración importada".toByteArray())
                    } else {
                        reply(c, "400 Bad Request", "text/plain; charset=utf-8", "Ese archivo no es una copia válida".toByteArray())
                    }
                }
                else -> reply(c, "404 Not Found", "text/plain; charset=utf-8", "No existe".toByteArray())
            }
        } catch (e: Exception) {
        } finally {
            try {
                c.close()
            } catch (e: Exception) {
            }
        }
    }

    private val PAGE = """<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>One Piece</title>
<style>body{font-family:sans-serif;background:#10131a;color:#eee;margin:0;padding:16px;max-width:520px}
h2{margin:18px 0 6px}input,textarea,button{width:100%;box-sizing:border-box;font-size:16px;padding:12px;margin:6px 0;border-radius:10px;border:0}
button{background:#9fb4ff;color:#10131a;font-weight:bold}#m{color:#8fe3a8;min-height:20px}</style></head><body>
<h1>One Piece</h1><p>Escribe el código que se ve en la TV:</p><input id="k" inputmode="numeric" maxlength="4" placeholder="Código">
<h2>Subir fondo</h2><input id="f" type="file" accept="image/*,video/*"><button onclick="up()">Subir</button>
<h2>Escribir en la búsqueda</h2><textarea id="t" rows="2"></textarea><button onclick="tx()">Enviar</button>
<h2>Configuración</h2><a href="#" onclick="ex();return false" style="color:#9fb4ff">Descargar copia</a>
<input id="c" type="file" accept=".json,application/json"><button onclick="im()">Importar copia</button>
<p id="m"></p><script>
const K=()=>encodeURIComponent(document.getElementById('k').value),M=s=>document.getElementById('m').textContent=s;
async function send(u,b){try{const r=await fetch(u,{method:'POST',body:b});M(await r.text())}catch(e){M('No se pudo conectar')}}
function up(){const f=document.getElementById('f').files[0];if(!f)return M('Elige un archivo');M('Subiendo...');send('/upload?k='+K()+'&name='+encodeURIComponent(f.name),f)}
function tx(){send('/text?k='+K(),document.getElementById('t').value)}
function ex(){location.href='/config?k='+K()}
function im(){const f=document.getElementById('c').files[0];if(!f)return M('Elige un archivo');send('/config?k='+K(),f)}
</script></body></html>"""
} 
