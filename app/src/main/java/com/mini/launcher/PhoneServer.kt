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
    private val onConfig: () -> Unit,
    private val onShop: () -> Unit = {},
    private val onPair: () -> Unit = {},
    private val onKey: (Int) -> Unit = {}
) {
    val port = 8080
    val code: String get() = Phones.pin(prefs)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var running = false

    fun address(): String = (localIp() ?: "sin red") + ":" + port

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(port)
            server = s
            running = true
            Nsd.register(ctx, port)
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
        Nsd.unregister()
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
            // App del celular: /hello dice quien eres; /pair cambia el PIN (una sola vez) por un token.
            if (method == "GET" && path == "/hello") {
                val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
                val o = JSONObject().put("app", "onepiece").put("name", android.os.Build.MODEL).put("ver", ver)
                reply(c, "200 OK", "application/json; charset=utf-8", o.toString().toByteArray())
                return
            }
            if (method == "POST" && path == "/pair") {
                if (Phones.locked()) {
                    reply(c, "429 Too Many Requests", "text/plain; charset=utf-8", "Demasiados intentos".toByteArray())
                    return
                }
                if (q["pin"] != code) {
                    Phones.fail()
                    reply(c, "403 Forbidden", "text/plain; charset=utf-8", "PIN incorrecto".toByteArray())
                    return
                }
                Phones.ok()
                val t = Phones.add(prefs, q["name"] ?: "")
                onPair()
                val o = JSONObject().put("token", t).put("name", android.os.Build.MODEL)
                reply(c, "200 OK", "application/json; charset=utf-8", o.toString().toByteArray())
                return
            }
            // Autorizado por token (app) o por PIN (pagina de respaldo). Los intentos con PIN malo se limitan.
            if (!Phones.valid(prefs, q["t"])) {
                if (q["k"] == null) {
                    reply(c, "401 Unauthorized", "text/plain; charset=utf-8", "Autorización no válida".toByteArray())
                    return
                }
                if (Phones.locked()) {
                    reply(c, "429 Too Many Requests", "text/plain; charset=utf-8", "Demasiados intentos".toByteArray())
                    return
                }
                if (q["k"] != code) {
                    Phones.fail()
                    reply(c, "403 Forbidden", "text/plain; charset=utf-8", "Código incorrecto".toByteArray())
                    return
                }
                Phones.ok()
            }
            when {
                method == "GET" && path == "/ping" -> {
                    reply(c, "200 OK", "text/plain; charset=utf-8", "ok".toByteArray())
                }
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
                method == "GET" && path == "/shop" -> {
                    reply(c, "200 OK", "text/plain; charset=utf-8", Shop.raw(prefs).toByteArray())
                }
                method == "POST" && path == "/shopadd" -> {
                    val b = ByteArray(minOf(len, 400L).toInt())
                    var r = 0
                    while (r < b.size) {
                        val n = i.read(b, r, b.size - r)
                        if (n < 0) break
                        r += n
                    }
                    Shop.add(prefs, String(b, 0, r, Charsets.UTF_8))
                    onShop()
                    reply(c, "200 OK", "text/plain; charset=utf-8", Shop.raw(prefs).toByteArray())
                }
                method == "POST" && path == "/shoptoggle" -> {
                    Shop.toggle(prefs, q["i"]?.toIntOrNull() ?: -1)
                    onShop()
                    reply(c, "200 OK", "text/plain; charset=utf-8", Shop.raw(prefs).toByteArray())
                }
                method == "POST" && path == "/shopdel" -> {
                    Shop.remove(prefs, q["i"]?.toIntOrNull() ?: -1)
                    onShop()
                    reply(c, "200 OK", "text/plain; charset=utf-8", Shop.raw(prefs).toByteArray())
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
                method == "GET" && path == "/state" -> {
                    reply(c, "200 OK", "application/json; charset=utf-8", RemoteCmd.state(ctx).toByteArray())
                }
                method == "POST" && path == "/key" -> {
                    val r = RemoteCmd.key(ctx, q["n"] ?: "", onKey)
                    reply(c, if (r.first == 200) "200 OK" else if (r.first == 409) "409 Conflict" else "400 Bad Request", "text/plain; charset=utf-8", r.second.toByteArray())
                }
                method == "POST" && path == "/vol" -> {
                    reply(c, "200 OK", "application/json; charset=utf-8", RemoteCmd.vol(ctx, q["d"], q["p"]?.toIntOrNull()).toByteArray())
                }
                method == "POST" && path == "/open" -> {
                    val b = ByteArray(minOf(len, 2000L).toInt())
                    var r = 0
                    while (r < b.size) {
                        val n = i.read(b, r, b.size - r)
                        if (n < 0) break
                        r += n
                    }
                    val res = RemoteCmd.open(ctx, String(b, 0, r, Charsets.UTF_8))
                    reply(c, if (res.first == 200) "200 OK" else "400 Bad Request", "text/plain; charset=utf-8", res.second.toByteArray())
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
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#0b0b14"><title>One Piece</title>
<style>
:root{--a:#a78bfa;--a2:#7c5cf0;--bg:#0b0b14;--card:#161626;--tx:#f2f1fb;--mu:#9b98b8;--ok:#5eead4}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;min-height:100vh;font-family:system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;color:var(--tx);
background:radial-gradient(120% 60% at 85% -10%,rgba(124,92,240,.35),transparent 60%),radial-gradient(90% 50% at -10% 110%,rgba(94,234,212,.12),transparent 60%),var(--bg);
padding:22px 16px 120px;max-width:560px;margin:0 auto}
.eye{font-size:11px;letter-spacing:.28em;color:var(--mu);text-transform:uppercase}
h1{font-size:30px;margin:6px 0 2px;font-weight:700}h1 b{color:var(--a)}
.sub{color:var(--mu);margin:0 0 18px;font-size:15px}
.card{background:linear-gradient(160deg,#1b1b30,#13131f);border-radius:26px;padding:18px;margin:14px 0;
box-shadow:8px 10px 24px rgba(0,0,0,.55),-4px -4px 14px rgba(255,255,255,.03),inset 0 1px 0 rgba(255,255,255,.07)}
.card h2{margin:0 0 4px;font-size:17px;font-weight:600;display:flex;align-items:center;gap:10px}
.dot{width:10px;height:10px;border-radius:50%;background:var(--a);box-shadow:0 0 12px var(--a)}
.card p{margin:0 0 12px;color:var(--mu);font-size:13px}
input,textarea{width:100%;font:inherit;font-size:16px;color:var(--tx);background:#0d0d18;border:1px solid rgba(255,255,255,.08);
border-radius:16px;padding:14px;outline:0;box-shadow:inset 3px 3px 8px rgba(0,0,0,.5)}
input:focus,textarea:focus{border-color:var(--a);box-shadow:0 0 0 3px rgba(167,139,250,.25)}
#k{text-align:center;font-size:30px;letter-spacing:.5em;padding:14px 0 14px .5em;font-weight:700;color:var(--a)}
input[type=file]{display:none}
.drop{display:block;text-align:center;padding:22px 12px;border:1.5px dashed rgba(167,139,250,.55);border-radius:18px;
color:var(--a);font-size:14px;background:rgba(167,139,250,.06);margin-bottom:10px;word-break:break-all}
button,.btn{width:100%;font:inherit;font-size:16px;font-weight:700;color:#fff;border:0;border-radius:18px;padding:14px;margin-top:8px;cursor:pointer;
background:linear-gradient(135deg,var(--a2),var(--a));box-shadow:0 8px 22px rgba(124,92,240,.45),inset 0 1px 0 rgba(255,255,255,.3)}
button:active{transform:scale(.98)}
.ghost{background:#1f1f35;color:var(--a);box-shadow:inset 0 0 0 1px rgba(167,139,250,.4);display:block;text-align:center;text-decoration:none}
#m{position:fixed;left:50%;transform:translateX(-50%);bottom:92px;max-width:88%;background:rgba(30,30,52,.92);color:var(--ok);
border:1px solid rgba(94,234,212,.4);border-radius:999px;padding:10px 18px;font-size:14px;box-shadow:0 8px 24px rgba(0,0,0,.5)}
#m:empty{display:none}
.it{display:flex;justify-content:space-between;padding:10px 4px;border-bottom:1px solid rgba(255,255,255,.06)}.it span{flex:1}.it.dn span{text-decoration:line-through;color:var(--mu)}.it b{color:var(--mu);padding:0 8px}
nav{position:fixed;left:14px;right:14px;bottom:14px;max-width:532px;margin:0 auto;display:flex;justify-content:space-around;padding:12px 8px;
background:rgba(24,24,42,.78);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);border-radius:30px;
border:1px solid rgba(255,255,255,.1);box-shadow:0 10px 30px rgba(0,0,0,.55)}
nav a{color:var(--mu);text-decoration:none;font-size:12px;text-align:center;padding:4px 14px;border-radius:18px}
nav a span{display:block;font-size:20px}nav a:active{color:var(--a);background:rgba(167,139,250,.15)}
</style></head><body>
<div class="eye">One Piece · Control remoto</div>
<h1>Tu <b>launcher</b></h1><p class="sub">Maneja la TV desde el celular.</p>
<div class="card"><h2><i class="dot"></i>Código</h2><p>Escribe el código de 4 dígitos que se ve en la TV.</p>
<input id="k" inputmode="numeric" maxlength="4" placeholder="••••"></div>
<div class="card" id="s1"><h2><i class="dot"></i>Subir fondo</h2><p>Foto o video para el fondo de la TV.</p>
<label class="drop" for="f" id="fl">Toca para elegir un archivo</label>
<input id="f" type="file" accept="image/*,video/*" onchange="nm(this,'fl')"><button onclick="up()">Subir</button></div>
<div class="card" id="s2"><h2><i class="dot"></i>Escribir en la búsqueda</h2><p>Teclear en el celular es más cómodo.</p>
<textarea id="t" rows="2" placeholder="Escribe aquí"></textarea><button onclick="tx()">Enviar a la TV</button></div>
<div class="card" id="s3"><h2><i class="dot"></i>Configuración</h2><p>Descarga o restaura tu copia de ajustes.</p>
<a class="btn ghost" href="#" onclick="ex();return false">Descargar copia</a>
<label class="drop" for="c" id="cl" style="margin-top:12px">Elegir copia para importar</label>
<input id="c" type="file" accept=".json,application/json" onchange="nm(this,'cl')"><button onclick="im()">Importar copia</button></div>
<div class="card" id="s4"><h2><i class="dot"></i>Lista de compras</h2><p>Toca un producto para marcarlo; × lo quita.</p>
<div id="sl"></div><input id="si" placeholder="Nuevo producto"><button onclick="sa()">Agregar</button><button class="ghost" onclick="sh('/shop?')">Actualizar lista</button></div>
<div id="m"></div>
<nav><a href="#s1"><span>🖼</span>Fondo</a><a href="#s2"><span>⌨</span>Texto</a><a href="#s3"><span>⚙</span>Ajustes</a></nav>
<script>
const K=()=>encodeURIComponent(document.getElementById('k').value),M=s=>{const e=document.getElementById('m');e.textContent=s;setTimeout(()=>{if(e.textContent===s)e.textContent=''},5000)};
function nm(i,l){document.getElementById(l).textContent=i.files[0]?i.files[0].name:'Elegir archivo'}
async function send(u,b){try{const r=await fetch(u,{method:'POST',body:b});M(await r.text())}catch(e){M('No se pudo conectar')}}
function up(){const f=document.getElementById('f').files[0];if(!f)return M('Elige un archivo');M('Subiendo...');send('/upload?k='+K()+'&name='+encodeURIComponent(f.name),f)}
function tx(){send('/text?k='+K(),document.getElementById('t').value)}
function ex(){location.href='/config?k='+K()}
async function sh(u,b){try{const r=await fetch(u+'k='+K(),{method:b===undefined?'GET':'POST',body:b});const t=await r.text();if(!r.ok)return M(t);rs(t)}catch(e){M('No se pudo conectar')}}
function rs(t){const l=document.getElementById('sl');l.innerHTML='';t.split('\n').forEach((x,i)=>{if(x.length<3)return;const d=document.createElement('div');d.className='it'+(x.charAt(0)==='1'?' dn':'');const s=document.createElement('span');s.textContent=x.substring(2);s.onclick=()=>sh('/shoptoggle?i='+i+'&',' ');const b=document.createElement('b');b.textContent='\u00d7';b.onclick=()=>sh('/shopdel?i='+i+'&',' ');d.appendChild(s);d.appendChild(b);l.appendChild(d)})}
function sa(){const e=document.getElementById('si');if(!e.value.trim())return;sh('/shopadd?',e.value);e.value=''}
(function(){const u=new URLSearchParams(location.search).get('k');if(u){document.getElementById('k').value=u;sh('/shop?')}})();
function im(){const f=document.getElementById('c').files[0];if(!f)return M('Elige un archivo');send('/config?k='+K(),f)}
</script></body></html>
"""
} 
