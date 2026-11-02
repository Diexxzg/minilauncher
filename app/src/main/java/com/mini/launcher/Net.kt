package com.mini.launcher

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Estado de la red. kind: 0 = sin conexion, 1 = Wi-Fi, 2 = Ethernet, 3 = otra.
 * ssid solo aparece si la TV deja leerlo (permiso de ubicacion concedido).
 */
data class NetState(
    val kind: Int = 0,
    val online: Boolean = false,
    val ssid: String? = null,
    val level: Int = 3,
    val bt: Boolean = false,
    val btCount: Int = 0
)

@Suppress("DEPRECATION")
fun readNet(ctx: Context): NetState {
    var kind = 0
    var online = false
    var ssid: String? = null
    var level = 3
    try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = if (net != null) cm.getNetworkCapabilities(net) else null
        if (caps != null) {
            kind = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
                else -> 3
            }
            online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    } catch (e: Exception) {
    }
    if (kind == 1) {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            if (info != null) {
                val raw = info.ssid ?: ""
                val s = raw.removePrefix("\"").removeSuffix("\"")
                if (s.isNotEmpty() && s != "<unknown ssid>" && s != "0x") ssid = s
                val rssi = info.rssi
                level = when {
                    rssi >= -55 -> 3
                    rssi >= -67 -> 2
                    rssi >= -78 -> 1
                    else -> 0
                }
            }
        } catch (e: Exception) {
        }
    }
    var bt = false
    var btCount = 0
    try {
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val ad = bm?.adapter
        bt = ad != null && ad.isEnabled
        if (bt && bm != null) {
            val ok = Build.VERSION.SDK_INT < 31 ||
                ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) btCount = bm.getConnectedDevices(BluetoothProfile.GATT).size
        }
    } catch (e: Exception) {
    }
    return NetState(kind, online, ssid, level, bt, btCount)
}

/** Boton de la barra superior: estado real de Wi-Fi / Ethernet / Bluetooth. */
@Composable
fun NetPill(net: NetState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassBox(
        onClick = onClick,
        modifier = modifier.height(44.dp).widthIn(min = 44.dp),
        shape = CircleShape
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (net.kind) {
                1 -> WifiIcon(if (net.online) Color.White else Color(0xFFFFC857), 22, net.level)
                2 -> EthernetIcon(if (net.online) Color.White else Color(0xFFFFC857), 22)
                3 -> WifiIcon(Color.White, 22, 3)
                else -> WifiIcon(Color(0x88FFFFFF), 22, 3, true)
            }
            val ssid = net.ssid
            if (net.kind == 1 && ssid != null) {
                Text(
                    ssid, color = Color(0xE6FFFFFF), fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)
                )
            } else if (net.kind == 0) {
                Text("Sin conexión", color = Color(0xCCFFFFFF), fontSize = 13.sp)
            } else if (net.kind == 1 && !net.online) {
                Text("Sin internet", color = Color(0xFFFFC857), fontSize = 13.sp)
            }
            if (net.bt) {
                BluetoothIcon(Color(0xE6FFFFFF), 18)
                if (net.btCount > 0) {
                    Text(net.btCount.toString(), color = Color(0xE6FFFFFF), fontSize = 13.sp)
                }
            }
        }
    }
}
