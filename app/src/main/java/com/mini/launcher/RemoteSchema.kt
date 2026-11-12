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
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ajustes de la TV que se ven y se cambian desde el celular. GENERADO a partir de Settings.kt:
 * cada fila simple (interruptor, opciones, deslizador) trae como leer y escribir su valor.
 * Las filas que abren dialogos o permisos en la TV salen con kind "info" (solo se ven).
 */
object RemoteSchema {
    class R(
        val id: Int, val cat: Int, val sec: String, val label: String, val kind: String,
        val opts: List<String>, val mn: Int, val mx: Int, val step: Int, val unit: String, val act: String,
        val get: ((Cfg, Prefs, Context) -> Int)?,
        val set: ((Cfg, Prefs, Context, Int) -> Unit)?
    )

    val cats = listOf("Fondo y color", "Dock y diseño", "Inicio", "Pantalla y energía", "Música", "Apps", "Sistema", "Permisos", "Seguridad", "Accesibilidad", "Acerca de")

    val rows: List<R> = listOf(
        R(0, 0, "Fondo", "Cambiar fondo de pantalla", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(3, 0, "Fondo", "Pausar fondo animado", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.pauseWall) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setPauseWall(it) }),
        R(9, 0, "Color", "Tema de color", "theme", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.theme },
            { cfg, prefs, ctx, v -> cfg.setTheme(v) }),
        R(11, 0, "Color", "Color de acento propio", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(12, 0, "Color", "Brillo de color", "info", listOf("Apagado", "Suave", "Medio", "Fuerte", "Máximo"), 0, 0, 1, "", "",
            null,
            null),
        R(13, 0, "Color", "Color del brillo", "choice", listOf("Color de cada app", "Color del fondo", "Color del tema"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.glowSrc },
            { cfg, prefs, ctx, v -> val it = v; cfg.setGlowSrc(it) }),
        R(15, 0, "Color", "Oscurecer fondo", "slider", emptyList(), 0, 80, 5, "%", "",
            { cfg, prefs, ctx -> cfg.dim },
            { cfg, prefs, ctx, v -> val it = v; cfg.setDim(it) }),
        R(55, 1, "Fichas", "Tamaño general", "info", emptyList(), 50, 180, 10, "%", "",
            null,
            null),
        R(56, 1, "Fichas", "Separación", "info", emptyList(), 0, 48, 2, " dp", "",
            null,
            null),
        R(57, 1, "Fichas", "Redondeo", "info", emptyList(), 0, 40, 2, " dp", "",
            null,
            null),
        R(58, 1, "Fichas", "Margen inferior", "info", emptyList(), 0, 80, 2, " dp", "",
            null,
            null),
        R(59, 1, "Fichas", "Estilo de ficha", "choice", listOf("Banner", "Banner 16:9", "Icono cuadrado", "Círculo"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.tileStyle },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTileStyle(it) }),
        R(60, 1, "Fichas", "Iconos uniformes", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.uniformIcons) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setUniformIcons(it) }),
        R(14, 1, "Foco y efectos", "Estilo de foco", "choice", listOf("Clásico (aro y brillo)", "Aro grueso", "Solo brillo", "Solo escala"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.focusStyle },
            { cfg, prefs, ctx, v -> val it = v; cfg.setFocusStyle(it) }),
        R(18, 1, "Foco y efectos", "Animación al abrir apps", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.openAnim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setOpenAnim(it) }),
        R(68, 2, "Parte superior", "Elementos de arriba", "choice", listOf("Completa", "Reducida", "Mínima"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.topMode },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTopMode(it) }),
        R(69, 2, "Parte superior", "Aviso de elementos de arriba", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(70, 2, "Parte superior", "Tamaño de arriba", "slider", emptyList(), 60, 150, 10, " %", "",
            { cfg, prefs, ctx -> cfg.topScale },
            { cfg, prefs, ctx, v -> val it = v; cfg.setTopScale(it) }),
        R(71, 2, "Reloj y fecha", "Reloj grande", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.bigClock) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBigClock(it) }),
        R(72, 2, "Reloj y fecha", "Formato de hora", "choice", listOf("12 horas", "24 horas"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.clock24) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setClock24(it == 1) }),
        R(73, 2, "Reloj y fecha", "Mostrar fecha", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showDate) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowDate(it) }),
        R(74, 2, "Reloj y fecha", "Formato de fecha", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(81, 2, "Clima", "Mostrar clima", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showWeather) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowWeather(it) }),
        R(82, 2, "Clima", "Ciudad del clima", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(83, 2, "Clima", "Unidades", "choice", listOf("°C", "°F"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.fahrenheit) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setFahrenheit(it == 1) }),
        R(89, 2, "Barra superior", "Ocultar barra superior", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.hideBar) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setHideBar(it) }),
        R(90, 2, "Barra superior", "Ocultar tras", "slider", emptyList(), 3, 60, 3, " s", "",
            { cfg, prefs, ctx -> cfg.hideBarSecs },
            { cfg, prefs, ctx, v -> val it = v; cfg.setHideBarSecs(it) }),
        R(19, 3, "Pantalla", "Tamaño de la interfaz", "slider", emptyList(), 70, 130, 5, "%", "",
            { cfg, prefs, ctx -> cfg.uiScale },
            { cfg, prefs, ctx, v -> val it = v; cfg.setUiScale(it) }),
        R(20, 3, "Pantalla", "Margen de seguridad", "slider", emptyList(), 0, 60, 2, " dp", "",
            { cfg, prefs, ctx -> cfg.overscan },
            { cfg, prefs, ctx, v -> val it = v; cfg.setOverscan(it) }),
        R(27, 3, "Rendimiento", "Fluidez máxima", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.fluid) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setFluid(it) }),
        R(28, 3, "Rendimiento", "Modo ahorro", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.saverOnly) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setSaver(it) }),
        R(30, 3, "Rendimiento", "Rendimiento: salud, red, almacenamiento y más", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(91, 3, "Reposo", "Modo ambiente", "choice", listOf("Desactivado", "2 min", "5 min", "10 min", "20 min"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambient },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbient(it) }),
        R(92, 3, "Reposo", "Protección contra quemado", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.burnIn) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBurnIn(it) }),
        R(100, 3, "Reposo", "Temporizador de sueño", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(101, 3, "Reposo", "Al terminar el temporizador", "choice", listOf("Pantalla negra", "Modo ambiente"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.sleepAction },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSleepAction(it) }),
        R(22, 3, "Diseño del modo ambiente", "Marco de fotos", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("frameOn") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("frameOn", if (it) "1" else "0") }),
        R(23, 3, "Diseño del modo ambiente", "Carpeta del marco", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(24, 3, "Diseño del modo ambiente", "Fondo vivo según la hora", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("liveBg") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("liveBg", if (it) "1" else "0") }),
        R(25, 3, "Diseño del modo ambiente", "Amplitud del movimiento", "choice", listOf("Normal", "Amplia", "Muy amplia"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ex("ambMove", "0").toIntOrNull() ?: 0 },
            { cfg, prefs, ctx, v -> val it = v; cfg.setEx("ambMove", it.toString()) }),
        R(26, 3, "Diseño del modo ambiente", "Pantalla negra al entrar", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ex("ambBlack") == "1") 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setEx("ambBlack", if (it) "1" else "0") }),
        R(93, 3, "Diseño del modo ambiente", "Oscurecer en modo ambiente", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ambDimOn) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setAmbDimOn(it) }),
        R(94, 3, "Diseño del modo ambiente", "Nivel de oscurecimiento", "slider", emptyList(), 10, 85, 5, "%", "",
            { cfg, prefs, ctx -> cfg.ambDimLvl },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbDimLvl(it) }),
        R(95, 3, "Diseño del modo ambiente", "Fondo animado en reposo", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.ambAnim) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setAmbAnim(it) }),
        R(96, 3, "Diseño del modo ambiente", "Posición de la hora", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosTime },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosTime(it) }),
        R(97, 3, "Diseño del modo ambiente", "Posición de la fecha", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosDate },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosDate(it) }),
        R(98, 3, "Diseño del modo ambiente", "Posición del reproductor", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosMusic },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosMusic(it) }),
        R(99, 3, "Diseño del modo ambiente", "Posición de los iconos", "choice", listOf("Arriba izquierda", "Arriba centro", "Arriba derecha", "Centro izquierda", "Centro", "Centro derecha", "Abajo izquierda", "Abajo centro", "Abajo derecha", "Oculto"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.ambPosIcons },
            { cfg, prefs, ctx, v -> val it = v; cfg.setAmbPosIcons(it) }),
        R(53, 3, "Sonido", "Sonido al mover el foco", "choice", listOf("Apagado", "Suave", "Medio"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.soundLevel },
            { cfg, prefs, ctx, v -> val it = v; cfg.setSoundLevel(it) }),
        R(102, 4, "Reproductor", "Mostrar reproductor", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.showMusic) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setShowMusic(it) }),
        R(103, 4, "Reproductor", "Tamaño del reproductor", "slider", emptyList(), 50, 120, 5, "%", "",
            { cfg, prefs, ctx -> cfg.musicSize },
            { cfg, prefs, ctx, v -> val it = v; cfg.setMusicSize(it) }),
        R(104, 4, "Reproductor", "Posición del reproductor", "choice", listOf("Junto al reloj", "Arriba al centro"), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> cfg.musicPos },
            { cfg, prefs, ctx, v -> val it = v; cfg.setMusicPos(it) }),
        R(105, 5, "Organizar", "Editar apps", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(106, 5, "Carpetas", "Carpetas del dock", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(107, 5, "Apps nuevas", "Aviso de app nueva", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.newAppAlert) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setNewAppAlert(it) }),
        R(108, 6, "Conexiones", "Wi-Fi", "action", emptyList(), 0, 0, 1, "", "wifi",
            null,
            null),
        R(109, 6, "Conexiones", "Bluetooth", "action", emptyList(), 0, 0, 1, "", "bluetooth",
            null,
            null),
        R(110, 6, "Conexiones", "Entradas (HDMI)", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(112, 6, "Sistema", "Todos los ajustes de Android", "action", emptyList(), 0, 0, 1, "", "settings",
            null,
            null),
        R(114, 6, "Sistema", "Protector de pantalla del sistema", "action", emptyList(), 0, 0, 1, "", "dream",
            null,
            null),
        R(115, 6, "Sistema", "Establecer como inicio predeterminado", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(117, 6, "Celular", "Conexión con el celular", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(140, 6, "Mando y uso diario", "Remapear botones del mando", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(170, 6, "Visitas", "Tarjeta de visitas", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(118, 6, "", "Botón ", "info", listOf("Ninguno", "Todas las apps", "Ajustes", "Entradas", "Búsqueda", "Menú de energía", "Editar apps", "Modo ambiente", "Pantalla negra", "Limpiar RAM"), 0, 0, 1, "", "",
            null,
            null),
        R(119, 6, "", "Botón ", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(130, 6, "", "Atajo ", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(158, 6, "Código PIN", "Estado del PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(160, 6, "Código PIN", "Estado del PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(111, 7, "Permisos", "Permitir nombre de red y Bluetooth", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(144, 7, "Permisos", "Permiso del calendario", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(145, 7, "Permisos", "Permiso de ubicación", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(146, 7, "Permisos", "Permiso de notificaciones", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(159, 8, "Código PIN", "Crear PIN de 4 dígitos", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(161, 8, "Código PIN", "Cambiar PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(162, 8, "Código PIN", "Quitar PIN", "info", emptyList(), 0, 0, 1, "", "",
            null,
            null),
        R(163, 8, "Protección", "Proteger ajustes", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.lockSettings) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setLockSettings(it) }),
        R(164, 8, "Protección", "Proteger apps ocultas", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.lockHidden) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setLockHidden(it) }),
        R(167, 9, "Lectura", "Texto grande", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.bigText) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setBigText(it) }),
        R(168, 9, "Lectura", "Alto contraste", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.highContrast) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setHighContrast(it) }),
        R(169, 9, "Navegación", "Foco grueso", "switch", emptyList(), 0, 0, 1, "", "",
            { cfg, prefs, ctx -> if (cfg.thickFocus) 1 else 0 },
            { cfg, prefs, ctx, v -> val it = v == 1; cfg.setThickFocus(it) })
    )

    fun json(cfg: Cfg, prefs: Prefs, ctx: Context): String {
        val a = JSONArray()
        for (r in rows) {
            val o = JSONObject().put("id", r.id).put("c", r.cat).put("s", r.sec).put("l", r.label).put("k", r.kind)
            if (r.opts.isNotEmpty()) o.put("o", JSONArray(r.opts))
            if (r.kind == "slider") o.put("mn", r.mn).put("mx", r.mx).put("st", r.step).put("u", r.unit)
            if (r.act.isNotEmpty()) o.put("a", r.act)
            val g = r.get
            if (g != null) {
                try {
                    o.put("v", g(cfg, prefs, ctx))
                } catch (e: Exception) {
                    o.put("k", "info")
                }
            }
            a.put(o)
        }
        val th = JSONArray()
        for (t in Themes.list) th.put(JSONObject().put("n", t.name).put("c", (t.acc.toArgb().toLong() and 0xFFFFFFFFL)))
        return JSONObject().put("cats", JSONArray(cats)).put("rows", a).put("themes", th).toString()
    }

    fun apply(cfg: Cfg, prefs: Prefs, ctx: Context, id: Int, v: Int) {
        val r = rows.firstOrNull { it.id == id } ?: return
        val s = r.set ?: return
        try {
            s(cfg, prefs, ctx, v)
        } catch (e: Exception) {
        }
    }
}
