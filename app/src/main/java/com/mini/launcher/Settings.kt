package com.mini.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Categorías que muestran la vista previa en vivo a la izquierda (Fondo y color, Dock y diseño). */
fun settingsPreviewCat(cat: Int): Boolean = cat in 0..1

val CITIES = listOf(
    "Caracas", "Maracaibo", "Valencia", "Barquisimeto", "Bogotá", "Medellín",
    "Ciudad de México", "Miami", "Madrid", "Lima", "Santiago", "Buenos Aires"
)

val GLOW_NAMES = listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo")

// ---------- Estilo (cristal oscuro) ----------
// Escala interna del contenido del panel (menor = mas pequeño).
private const val PANEL_SCALE = 0.84f

/**
 * Ancho (dp) del panel lateral derecho para un ancho disponible dado.
 * Lo usan el panel y el inicio (que se hace mas angosto) para no taparse entre si.
 */
fun sidePanelWidth(availDp: Float, wide: Boolean = false): Float =
    if (wide) {
        // Configuracion: categorias a la izquierda y opciones a la derecha.
        (availDp * 0.38f).coerceIn(440f, 540f).coerceAtMost(availDp * 0.5f)
    } else {
        (availDp * 0.40f).coerceIn(400f, 560f).coerceAtMost(availDp * 0.62f)
    }

private val ACC: Color get() = Themes.current.acc
private val R_TEXT = Color(0xFFF1F4FA)
private val R_TEXT2 = Color(0xFFA8B1C3)
private val R_OFF = Color(0xFF3A4150)
private val R_DARK = Color(0xFF0B0D11)

/** Icono de la fila que se está dibujando (lo fija la lista; vacío = sin cajita de icono). */
val LocalRowIcon = androidx.compose.runtime.compositionLocalOf { "" }

@Composable
fun FocusRow(
    modifier: Modifier = Modifier,
    onKey: (Int) -> Boolean = { false },
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.(Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val icon = LocalRowIcon.current
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .clip(shape)
        .background(
            if (focused) Brush.horizontalGradient(listOf(ACC.copy(alpha = 0.33f), ACC.copy(alpha = 0.18f)))
            else SolidColor(Color(0x0AFFFFFF))
        )
        .then(if (focused) Modifier.border(2.dp, ACC, shape) else Modifier)
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) {
                when (e.key) {
                    Key.DirectionLeft -> onKey(-1)
                    Key.DirectionRight -> onKey(1)
                    else -> false
                }
            } else {
                false
            }
        }
        .onFocusChanged { focused = it.isFocused }
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base.focusable()
    Row(
        clickable.padding(start = 7.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon.isNotEmpty()) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (focused) ACC else ACC.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { LineIcon(icon, if (focused) R_DARK else ACC, 16) }
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        content(this, focused)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            val nv = (value + d * step).coerceIn(min, max)
            if (nv != value) onChange(nv)
            true
        }
    ) { focused ->
        Text(label, color = R_TEXT, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        val frac = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
        Box(Modifier.width(110.dp).height(20.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(R_OFF)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .background(if (focused) ACC else ACC.copy(alpha = 0.6f))
                )
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (94f * frac).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(R_TEXT)
            )
        }
        Text(
            value.toString() + unit, color = R_TEXT2, fontSize = 13.sp, maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit
) {
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            onChange(d > 0)
            true
        },
        onClick = { onChange(!checked) }
    ) {
        Text(label, color = R_TEXT, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) ACC else R_OFF)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if (checked) R_DARK else R_TEXT2))
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val sel = selected.coerceIn(0, options.lastIndex)
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            val n = (sel + d).coerceIn(0, options.lastIndex)
            if (n != sel) onSelect(n)
            true
        },
        onClick = { onSelect((sel + 1) % options.size) }
    ) { focused ->
        Text(label, color = R_TEXT, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            (if (focused) "‹ " else "") + options[sel] + (if (focused) " ›" else ""),
            color = if (focused) R_TEXT else R_TEXT2, fontSize = 13.sp, maxLines = 1
        )
    }
}

private val THEME_SHOWN = listOf("Medianoche", "Océano", "Esmeralda", "Atardecer", "Rojo", "Violeta", "Dorado")

/** Selector de tema: 7 círculos fijos y «+N más»; izquierda/derecha recorre todos (los guardados siguen por índice). */
@Composable
fun ThemeSwatchRow(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val order = remember {
        val first = THEME_SHOWN.map { n -> Themes.list.indexOfFirst { it.name == n } }.filter { it >= 0 }
        first + Themes.list.indices.filter { it !in first }
    }
    val sel = selected.coerceIn(0, Themes.list.lastIndex)
    val pos = order.indexOf(sel).coerceAtLeast(0)
    val shown = minOf(7, order.size)
    FocusRow(
        modifier = modifier,
        onKey = { d ->
            val np = (pos + d).coerceIn(0, order.lastIndex)
            if (np != pos) onSelect(order[np])
            true
        },
        onClick = { onSelect(order[(pos + 1) % order.size]) }
    ) {
        Column(Modifier.weight(1f)) {
            Text("Tema de color", color = R_TEXT, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val idx = if (pos >= shown) order.take(shown) + order[pos] else order.take(shown)
                for (i in idx) {
                    val on = i == sel
                    Box(
                        Modifier
                            .size(if (on) 20.dp else 16.dp)
                            .clip(CircleShape)
                            .background(Themes.list[i].acc)
                            .then(if (on) Modifier.border(2.dp, R_TEXT, CircleShape) else Modifier)
                    )
                }
                if (order.size > shown) Text("+" + (order.size - shown) + " más", color = R_TEXT2, fontSize = 11.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(Themes.list[sel].name, color = ACC, fontSize = 12.sp)
        }
    }
}

@Composable
fun ActionRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier, onClick = onClick) { focused ->
        Text(text, color = R_TEXT, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Text("›", color = if (focused) R_TEXT else R_TEXT2, fontSize = 18.sp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = R_TEXT2, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = R_TEXT, fontSize = 13.sp)
    }
}
/**
 * Panel de cristal pegado al lado derecho, de arriba a abajo. El inicio se hace mas angosto
 * (ver sidePanelWidth) para que el panel nunca quede encima de las apps.
 * El contenido se dibuja a PANEL_SCALE para verse mas compacto.
 */
@Composable
fun GlassPanelFrame(wide: Boolean = false, compact: Boolean = false, content: @Composable () -> Unit) {
    val conf = LocalConfiguration.current
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val us = LocalUiScale.current
    val ov = LocalOverscan.current / us
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, androidx.compose.animation.core.snap(), label = "panelIn")
    val availW = conf.screenWidthDp.toFloat() / us - 2f * ov
    val availH = conf.screenHeightDp.toFloat() / us - 2f * ov
    // Configuración (wide): ventana horizontal centrada. En Fondo y Dock se hace más baja y se sube
    // para que el inicio siga viéndose debajo con los cambios en vivo.
    val pw = if (wide) (availW * 0.62f).coerceIn(520f, 1100f) else sidePanelWidth(availW, false)
    val winFrac = if (!wide) 1f else 0.60f
    val topPad = if (!wide) 8.dp else (availH * 0.09f).dp
    val shape = RoundedCornerShape(if (wide) 28.dp else 22.dp)
    val panelBg = if (wide) Brush.verticalGradient(listOf(Color(0xFA10101A), Color(0xFA08080E)))
    else Brush.verticalGradient(listOf(Themes.current.panelTop, Themes.current.panelBottom))
    val panelBorder = Brush.verticalGradient(listOf(ACC.copy(alpha = 0.8f), ACC.copy(alpha = 0.25f)))
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(if (wide) Alignment.TopCenter else Alignment.CenterEnd)
                .padding(top = topPad, bottom = if (wide) 0.dp else 8.dp, end = if (wide) 0.dp else 6.dp)
                .width(pw.dp)
                .fillMaxHeight(winFrac)
                .graphicsLayer {
                    if (wide) translationY = (1f - p) * 40.dp.toPx() else translationX = (1f - p) * 56.dp.toPx()
                    alpha = p
                }
                .drawBehind {
                    // Resplandor del acento alrededor de la ventana (capas suaves, sin desenfoque).
                    if (wide && !Fx.glow) return@drawBehind
                    val cr = (if (wide) 28.dp else 22.dp).toPx()
                    for (i in 1..3) {
                        val g = i * 5.dp.toPx()
                        drawRoundRect(
                            ACC.copy(alpha = 0.06f),
                            topLeft = Offset(-g, -g),
                            size = Size(size.width + 2 * g, size.height + 2 * g),
                            cornerRadius = CornerRadius(cr + g)
                        )
                    }
                }
                .clip(shape)
                .background(panelBg)
                .drawBehind {
                    // Brillo suave arriba, efecto cristal.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0x14FFFFFF), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.4f
                        )
                    )
                    if (wide) return@drawBehind
                    val r = size.width * 0.95f
                    drawCircle(
                        Brush.radialGradient(listOf(ACC.copy(alpha = 0.20f), Color.Transparent), center = Offset(0f, 0f), radius = r),
                        radius = r, center = Offset(0f, 0f)
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(ACC.copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width, size.height), radius = r),
                        radius = r, center = Offset(size.width, size.height)
                    )
                }
                .border(1.dp, panelBorder, shape)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(dens.density * (if (wide) 0.56f else PANEL_SCALE), dens.fontScale)
            ) { content() }
        }
    }
}
// ---------- Opciones ----------

/** Datos que cambian con el tiempo o que se leen del sistema (se refrescan cada pocos segundos). */
internal class AboutSnap(
    val res: String,
    val dpi: Int,
    val hz: Int,
    val storage: String,
    val ram: String,
    val temp: String?,
    val net: List<Pair<String, String>>,
    val up: String
)

private fun fmtBytes(b: Long): String {
    val gb = b / 1073741824.0
    return if (gb >= 1.0) {
        String.format(java.util.Locale.forLanguageTag("es-VE"), "%.1f GB", gb)
    } else {
        (b / 1048576L).toString() + " MB"
    }
}

/** Temperatura del equipo si la TV la deja leer (zona termica o bateria); si no, null. */
private fun readTemp(ctx: Context): String? {
    for (i in 0..9) {
        val f = java.io.File("/sys/class/thermal/thermal_zone" + i + "/temp")
        val raw = runCatching { f.readText().trim().toDoubleOrNull() }.getOrNull() ?: continue
        val c = if (raw > 1000.0) raw / 1000.0 else raw
        if (c in 15.0..120.0) return Math.round(c).toString() + " °C"
    }
    try {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val present = bi?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ?: false
        if (present && t > 0) return Math.round(t / 10.0).toString() + " °C"
    } catch (e: Exception) {
    }
    return null
}

/** Tiempo desde que se encendio la TV, en texto corto. */
private fun fmtUptime(ms: Long): String {
    val mins = ms / 60000L
    val d = mins / 1440L
    val h = (mins % 1440L) / 60L
    val m = mins % 60L
    return when {
        d > 0L -> d.toString() + " d " + h.toString() + " h"
        h > 0L -> h.toString() + " h " + m.toString() + " min"
        else -> m.toString() + " min"
    }
}

/** true si One Piece es el inicio predeterminado de la TV. */
internal fun isDefaultHome(ctx: Context): Boolean = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
} catch (e: Exception) {
    false
}

/** true si el texto es una fecha aaaa-mm-dd valida. */
private fun localIp(): String? {
    try {
        val ifs = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in java.util.Collections.list(ifs)) {
            if (!ni.isUp || ni.isLoopback) continue
            for (a in java.util.Collections.list(ni.inetAddresses)) {
                if (!a.isLoopbackAddress && a is java.net.Inet4Address) return a.hostAddress
            }
        }
    } catch (e: Exception) {
    }
    return null
}

@Suppress("DEPRECATION")
internal fun readAboutSnap(ctx: Context): AboutSnap {
    var res = "?"
    var dpi = 0
    var hz = 0
    try {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val dm = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        res = dm.widthPixels.toString() + " × " + dm.heightPixels.toString()
        dpi = dm.densityDpi
        hz = Math.round(wm.defaultDisplay.refreshRate)
    } catch (e: Exception) {
    }
    var storage = "No disponible"
    try {
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        storage = fmtBytes(free) + " libres de " + fmtBytes(total)
    } catch (e: Exception) {
    }
    var ram = "No disponible"
    try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        ram = fmtBytes(mi.availMem) + " libres de " + fmtBytes(mi.totalMem)
    } catch (e: Exception) {
    }
    val ns = readNet(ctx)
    val net = ArrayList<Pair<String, String>>()
    val kindName = when (ns.kind) {
        1 -> "Wi-Fi"
        2 -> "Ethernet"
        3 -> "Otra"
        else -> "Sin conexión"
    }
    val inetName = when {
        ns.kind == 0 -> "—"
        ns.online -> "Disponible"
        else -> "Sin salida a internet"
    }
    net.add(Pair("Conexión", kindName))
    net.add(Pair("Internet", inetName))
    if (ns.kind == 1) {
        net.add(Pair("Red Wi-Fi", ns.ssid ?: "No visible (falta el permiso de ubicación)"))
    }
    net.add(Pair("Dirección IP", localIp() ?: "—"))
    val btName = if (ns.bt) {
        if (ns.btCount > 0) "Activado · " + ns.btCount.toString() + " conectado(s)" else "Activado"
    } else {
        "Apagado"
    }
    net.add(Pair("Bluetooth", btName))
    return AboutSnap(res, dpi, hz, storage, ram, readTemp(ctx), net, fmtUptime(SystemClock.elapsedRealtime()))
}

/**
 * Tarjeta de solo lectura. Es una sola parada de foco: con las flechas se salta de tarjeta en
 * tarjeta y el panel se desplaza solo, asi se puede leer todo sin que el foco se pierda.
 */
@Composable
internal fun InfoCard(lines: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(ACC.copy(alpha = 0.22f), ACC.copy(alpha = 0.06f)))
                else Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x08FFFFFF)))
            )
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) SolidColor(ACC.copy(alpha = 0.85f))
                else Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        for (line in lines) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    line.first, color = Color(0xA6FFFFFF), fontSize = 17.sp,
                    modifier = Modifier.weight(0.42f).padding(end = 10.dp)
                )
                Text(
                    line.second, color = Color.White, fontSize = 18.sp,
                    textAlign = TextAlign.End, modifier = Modifier.weight(0.58f)
                )
            }
        }
    }
}
