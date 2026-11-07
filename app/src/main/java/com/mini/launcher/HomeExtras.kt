package com.mini.launcher

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Camara IP en una esquina (video MJPEG). */
object Cam {
    var show by mutableStateOf(false)
}

/** Recordatorios en pantalla: lineas «HH:MM texto» (con «*» al inicio = todos los dias). */
object Remind {
    var cur by mutableStateOf<String?>(null)
    private val fired = HashSet<String>()

    fun add(cfg: Cfg, line: String) {
        val l = cfg.ex("remList")
        cfg.setEx("remList", if (l.isEmpty()) line else "$l\n$line")
    }

    /** Valida «HH:MM texto» o «*HH:MM texto»; devuelve la linea limpia o null. */
    fun parse(s: String): String? {
        val t = s.trim()
        val daily = t.startsWith("*")
        val b = t.trimStart('*').trim()
        val m = Regex("^(\\d{1,2}):(\\d{2})\\s+(.+)$").find(b) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        return (if (daily) "*" else "") + "%02d:%02d %s".format(h, mi, m.groupValues[3])
    }

    fun check(cfg: Cfg) {
        if (cur != null) return
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        val lines = cfg.ex("remList").split("\n").filter { it.isNotBlank() }
        for (line in lines) {
            val daily = line.startsWith("*")
            val b = line.trimStart('*')
            val tm = (b.substring(0, 2).toIntOrNull() ?: continue) * 60 + (b.substring(3, 5).toIntOrNull() ?: continue)
            val key = "$line@$day"
            if (nowMin in tm..(tm + 5) && key !in fired) {
                fired.add(key)
                cur = b
                if (!daily) cfg.setEx("remList", lines.filter { it != line }.joinToString("\n"))
                return
            }
        }
    }

    fun snooze(cfg: Cfg) {
        val c = cur ?: return
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
        add(cfg, "%02d:%02d %s".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), c.drop(6)))
        cur = null
    }
}

/** Ambilight casero: envia el color del acento a un WLED de la red local. */
object Ambi {
    fun send(ctx: Context, ip: String, c: Color, level: Int, say: Boolean = false) {
        val host = ip.trim().removePrefix("http://").trimEnd('/')
        if (host.isEmpty()) return
        val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt(); val b = (c.blue * 255).toInt()
        val bri = (level.coerceIn(5, 100) * 255 / 100)
        Thread {
            var ok = false
            try {
                val k = java.net.URL("http://$host/json/state").openConnection() as java.net.HttpURLConnection
                k.connectTimeout = 3000; k.readTimeout = 3000
                k.requestMethod = "POST"; k.doOutput = true
                k.setRequestProperty("Content-Type", "application/json")
                k.outputStream.use { it.write("""{"on":true,"bri":$bri,"seg":[{"col":[[$r,$g,$b]]}]}""".toByteArray()) }
                ok = k.responseCode in 200..299
                k.disconnect()
            } catch (e: Exception) { }
            if (say) android.os.Handler(android.os.Looper.getMainLooper()).post {
                toast(ctx, if (ok) "Color enviado." else "No se pudo conectar con el WLED.")
            }
        }.start()
    }
}

/** Atajos del Panel rapido: «Nombre;destino» donde destino es web, MAC o «camara». */
object QS {
    fun parse(s: String): Triple<String, String, String>? {
        val i = s.indexOf(';')
        if (i <= 0) return null
        val name = s.substring(0, i).trim()
        val d = s.substring(i + 1).trim()
        if (name.isEmpty() || d.isEmpty()) return null
        if (d.equals("camara", true) || d.equals("cámara", true)) return Triple(name, "c:", "Cámara")
        val mac = Remap.parseMac(d)
        if (mac != null) return Triple(name, "l:" + mac.joinToString(":") { "%02X".format(it) }, "Wake-on-LAN")
        return Triple(name, "w:" + (if (d.startsWith("http://") || d.startsWith("https://")) d else "http://$d"), "Web")
    }

    fun run(ctx: Context, v: String) {
        if (v == "c:") Cam.show = true else Remap.runExtra(ctx, v)
    }
}

object HomeFx {
    /** Teclas con un recordatorio o la camara abiertos. true = tecla consumida. */
    fun handleKey(cfg: Cfg, e: KeyEvent): Boolean {
        if (Remind.cur == null && !Cam.show) return false
        if (e.type != KeyEventType.KeyDown) return true
        if (Remind.cur != null) {
            when (e.key) {
                Key.DirectionRight -> Remind.snooze(cfg)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> Remind.cur = null
                else -> return false
            }
            return true
        }
        if (e.key == Key.Back) { Cam.show = false; return true }
        return false
    }
}

@Composable
fun HomeOverlays(cfg: Cfg) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            if (cfg.ex("remOn") == "1") Remind.check(cfg)
        }
    }
    val acc = Themes.current.acc
    val ambOn = cfg.ex("ambiOn") == "1"
    val ip = cfg.ex("ambiIp")
    val lvl = cfg.ex("ambiLvl", "70").toIntOrNull() ?: 70
    LaunchedEffect(acc, ambOn, ip, lvl) {
        if (ambOn && ip.isNotBlank()) {
            delay(800)
            Ambi.send(ctx, ip, acc, lvl)
        }
    }
    val corner = cfg.ex("camCorner", "0").toIntOrNull() ?: 0
    Box(Modifier.fillMaxSize().padding(36.dp)) {
        Remind.cur?.let { r ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(shape)
                    .background(Color(0xFF121722))
                    .border(2.dp, acc, shape)
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Text("RECORDATORIO · " + r.take(5), color = acc, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(r.drop(6), color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(vertical = 6.dp))
                Text("OK = hecho · Derecha = en 10 min", color = Color(0xB3FFFFFF), fontSize = 14.sp)
            }
        }
        val url = cfg.ex("camUrl")
        if (Cam.show && url.isNotBlank()) {
            val al = when (corner) {
                1 -> Alignment.BottomStart; 2 -> Alignment.TopEnd; 3 -> Alignment.TopStart; else -> Alignment.BottomEnd
            }
            CamCorner(url, Modifier.align(al))
        }
    }
}

@Composable
private fun CamCorner(url: String, modifier: Modifier) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var err by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 8000
                val ins = java.io.BufferedInputStream(c.inputStream, 65536)
                val buf = java.io.ByteArrayOutputStream()
                var prev = -1; var inImg = false; var last = 0L
                while (isActive) {
                    val b = ins.read()
                    if (b < 0) break
                    if (!inImg) {
                        if (prev == 0xFF && b == 0xD8) { inImg = true; buf.reset(); buf.write(0xFF); buf.write(0xD8) }
                    } else {
                        buf.write(b)
                        if (prev == 0xFF && b == 0xD9) {
                            inImg = false
                            val now = System.currentTimeMillis()
                            if (now - last >= 125) {
                                last = now
                                val a = buf.toByteArray()
                                BitmapFactory.decodeByteArray(a, 0, a.size)?.let { bmp = it.asImageBitmap() }
                            }
                        }
                    }
                    prev = b
                }
                c.disconnect()
            } catch (e: Exception) { }
            err = true
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(width = 400.dp, height = 225.dp)
            .clip(shape)
            .background(Color(0xFF0B0E14))
            .border(2.dp, Color(0xFF4A5570), shape),
        contentAlignment = Alignment.Center
    ) {
        val img = bmp
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (err) "Sin señal de la cámara" else "Conectando…", color = Color(0xB3FFFFFF), fontSize = 16.sp)
        if (err && img != null) Text("Sin señal", color = Color(0xFFFF6666), fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
