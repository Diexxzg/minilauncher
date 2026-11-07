package com.mini.launcher.remote

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/** Una TV encontrada o elegida. */
class Tv(val host: String, val port: Int, val name: String)

/** Datos guardados en el celular: TV emparejada y su token. */
class Store(c: Context) {
    private val sp = c.getSharedPreferences("control", Context.MODE_PRIVATE)
    fun get(k: String): String = sp.getString(k, "") ?: ""
    fun put(k: String, v: String) {
        sp.edit().putString(k, v).apply()
    }
}

object Api {
    private val ipRegex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun isIp(s: String): Boolean = ipRegex.matches(s)

    /** Solo redes locales: 192.168.x, 10.x, 172.16-31.x o enlace local. */
    fun isLan(host: String): Boolean {
        if (!isIp(host)) return false
        return try {
            val a = InetAddress.getByName(host)
            a.isSiteLocalAddress || a.isLinkLocalAddress
        } catch (e: Exception) {
            false
        }
    }

    class Res(val code: Int, val body: String)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun call(host: String, port: Int, method: String, path: String, timeout: Int = 5000): Res {
        if (!isLan(host)) throw IOException("Solo se permiten direcciones de tu red local.")
        val c = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeout
            c.readTimeout = timeout
            if (method == "POST") {
                c.doOutput = true
                c.setFixedLengthStreamingMode(0)
                c.outputStream.close()
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return Res(code, body)
        } finally {
            c.disconnect()
        }
    }

    /** Cambia el PIN (una sola vez) por un token. Devuelve token y nombre de la TV. */
    fun pair(host: String, port: Int, pin: String, myName: String): Pair<String, String> {
        val r = call(host, port, "POST", "/pair?pin=" + enc(pin) + "&name=" + enc(myName))
        return when (r.code) {
            200 -> {
                val o = JSONObject(r.body)
                Pair(o.getString("token"), o.optString("name", "TV"))
            }
            403 -> throw IOException("PIN incorrecto.")
            429 -> throw IOException("Demasiados intentos. Espera un minuto.")
            else -> throw IOException("La TV respondió con error " + r.code + ".")
        }
    }

    /** 200 = autorizada; 401/403 = la TV ya no reconoce este celular. */
    fun ping(host: String, port: Int, token: String): Int =
        call(host, port, "GET", "/ping?t=" + enc(token)).code

    /** QR de la TV: onepiece://pair?a=IP:PUERTO&k=PIN */
    fun parseQr(s: String): Pair<Tv, String>? {
        return try {
            val u = Uri.parse(s)
            if (u.scheme != "onepiece") return null
            val a = u.getQueryParameter("a") ?: return null
            val k = u.getQueryParameter("k") ?: return null
            val host = a.substringBefore(":")
            val port = a.substringAfter(":", "8080").toIntOrNull() ?: 8080
            if (!isIp(host) || k.length != 4) null else Pair(Tv(host, port, "TV"), k)
        } catch (e: Exception) {
            null
        }
    }
}

/** Busca TVs One Piece en la red (mDNS, servicio "_onepiece._tcp"). */
class Finder(ctx: Context, private val onFound: (Tv) -> Unit) {
    private val m = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var l: NsdManager.DiscoveryListener? = null

    fun start() {
        stop()
        val d = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                resolve(s)
            }
        }
        l = d
        try {
            m.discoverServices("_onepiece._tcp.", NsdManager.PROTOCOL_DNS_SD, d)
        } catch (e: Exception) {
            l = null
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(s: NsdServiceInfo) {
        try {
            m.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val h = (i.host as? Inet4Address)?.hostAddress ?: return
                    onFound(Tv(h, i.port, i.serviceName.removePrefix("One Piece ").trim().ifEmpty { "TV" }))
                }
            })
        } catch (e: Exception) {
        }
    }

    fun stop() {
        l?.let {
            try {
                m.stopServiceDiscovery(it)
            } catch (e: Exception) {
            }
        }
        l = null
    }
}
