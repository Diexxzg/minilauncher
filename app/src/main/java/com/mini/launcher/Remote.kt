package com.mini.launcher

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Celulares autorizados (paquete 8A). Se guardan como lineas "token|nombre|ultimaVez" en Prefs.
 * Estas dos claves ("phonesAuth", "phonePin") nunca salen en las copias de ajustes.
 */
object Phones {
    data class Dev(val token: String, val name: String, val last: Long)

    private const val KEY = "phonesAuth"
    private var fails = 0
    private var lockUntil = 0L

    /** PIN fijo de 4 digitos: se pide una sola vez al emparejar un celular. */
    fun pin(p: Prefs): String {
        var s = p.getStr("phonePin", "")
        if (s.length != 4 || !s.all { it.isDigit() }) {
            s = (1000 + Random.nextInt(9000)).toString()
            p.putStr("phonePin", s)
        }
        return s
    }

    fun newPin(p: Prefs): String {
        p.putStr("phonePin", "")
        return pin(p)
    }

    fun list(p: Prefs): List<Dev> = p.getStr(KEY, "").split("\n").mapNotNull { l ->
        val a = l.split("|")
        if (a.size < 3 || a[0].isEmpty()) null else Dev(a[0], a[1], a[2].toLongOrNull() ?: 0L)
    }

    private fun save(p: Prefs, l: List<Dev>) {
        p.putStr(KEY, l.joinToString("\n") { it.token + "|" + it.name.replace("|", " ").replace("\n", " ") + "|" + it.last })
    }

    @Synchronized
    fun add(p: Prefs, name: String): String {
        val b = ByteArray(24)
        SecureRandom().nextBytes(b)
        val t = b.joinToString("") { "%02x".format(it) }
        val n = name.trim().take(40).ifEmpty { "Celular" }
        save(p, (list(p) + Dev(t, n, System.currentTimeMillis())).takeLast(20))
        return t
    }

    @Synchronized
    fun revoke(p: Prefs, token: String) {
        save(p, list(p).filter { it.token != token })
    }

    @Synchronized
    fun valid(p: Prefs, t: String?): Boolean {
        if (t.isNullOrEmpty()) return false
        val l = list(p)
        val d = l.firstOrNull { it.token == t } ?: return false
        val now = System.currentTimeMillis()
        if (now - d.last > 60_000L) save(p, l.map { if (it.token == t) it.copy(last = now) else it })
        return true
    }

    @Synchronized
    fun locked(): Boolean = System.currentTimeMillis() < lockUntil

    /** 5 intentos con PIN equivocado seguidos bloquean los intentos durante 1 minuto. */
    @Synchronized
    fun fail() {
        fails++
        if (fails >= 5) {
            fails = 0
            lockUntil = System.currentTimeMillis() + 60_000L
        }
    }

    @Synchronized
    fun ok() {
        fails = 0
    }

    fun ago(last: Long): String {
        val m = (System.currentTimeMillis() - last) / 60000L
        return when {
            m < 2 -> "ahora"
            m < 60 -> "hace $m min"
            m < 1440 -> "hace ${m / 60} h"
            else -> "hace ${m / 1440} d"
        }
    }
}

/** Anuncia la TV en la red (mDNS) como "_onepiece._tcp" para que la app del celular la encuentre sola. */
object Nsd {
    private var mgr: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null

    @Synchronized
    fun register(c: Context, port: Int) {
        if (listener != null) return
        try {
            val m = c.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo()
            info.serviceName = "One Piece " + Build.MODEL
            info.serviceType = "_onepiece._tcp."
            info.setPort(port)
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            mgr = m
            listener = l
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun unregister() {
        try {
            listener?.let { mgr?.unregisterService(it) }
        } catch (e: Exception) {
        }
        listener = null
        mgr = null
    }
}
