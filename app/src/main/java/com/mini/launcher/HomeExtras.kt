package com.mini.launcher

import android.content.Context

/** Atajos del Panel rapido: «Nombre;destino» donde destino es una direccion web o una MAC (Wake-on-LAN). */
object QS {
    fun parse(s: String): Triple<String, String, String>? {
        val i = s.indexOf(';')
        if (i <= 0) return null
        val name = s.substring(0, i).trim()
        val d = s.substring(i + 1).trim()
        if (name.isEmpty() || d.isEmpty()) return null
        val mac = Remap.parseMac(d)
        if (mac != null) return Triple(name, "l:" + mac.joinToString(":") { "%02X".format(it) }, "Wake-on-LAN")
        return Triple(name, "w:" + (if (d.startsWith("http://") || d.startsWith("https://")) d else "http://$d"), "Web")
    }

    fun run(ctx: Context, v: String) {
        Remap.runExtra(ctx, v)
    }
}
